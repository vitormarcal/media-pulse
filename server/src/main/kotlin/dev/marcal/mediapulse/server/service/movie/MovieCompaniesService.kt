package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCompaniesBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompaniesSyncResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieCompany
import dev.marcal.mediapulse.server.model.movie.MovieCompanyType
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompaniesCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MovieCompaniesService(
    private val movieRepository: MovieRepository,
    private val movieQueryRepository: MovieQueryRepository,
    private val movieCompanyRepository: MovieCompanyRepository,
    private val movieCompanyAssignmentRepository: MovieCompanyAssignmentRepository,
    private val movieCompaniesCrudRepository: MovieCompaniesCrudRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun syncFromTmdb(movieId: Long): MovieCompaniesSyncResponse = syncFromTmdbInternal(movieId)

    @Transactional
    fun syncFromTmdbIfLinked(movieId: Long) {
        val hasTmdbLink =
            movieQueryRepository
                .getMovieDetails(movieId)
                .externalIds
                .any { it.provider == "TMDB" }

        if (hasTmdbLink) {
            syncFromTmdbInternal(movieId)
        }
    }

    fun syncAllFromTmdb(limit: Int = 100): MovieCompaniesBatchSyncResponse {
        val requestedLimit = limit.coerceIn(1, 1000)
        val pendingTotal = movieCompaniesCrudRepository.countPendingTmdbSyncCandidates()
        val candidates = movieCompaniesCrudRepository.findTmdbSyncCandidates(requestedLimit)
        var synced = 0
        var failed = 0
        var processed = 0

        logger.info(
            "Movie companies TMDb batch sync started | requestedLimit={} | pendingTotal={} | selectedCandidates={}",
            requestedLimit,
            pendingTotal,
            candidates.size,
        )

        candidates.forEach { candidate ->
            runCatching {
                transactionTemplate.execute {
                    syncFromTmdbInternal(candidate.movieId)
                } ?: error("Batch company sync transaction returned null")
            }.onSuccess {
                synced++
            }.onFailure { ex ->
                failed++
                logger.warn(
                    "Failed to sync movie companies from TMDb in batch | movieId={} tmdbId={}",
                    candidate.movieId,
                    candidate.tmdbId,
                    ex,
                )
            }

            processed++
            logger.info(
                "Movie companies TMDb batch sync progress | processed={} | synced={} | failed={} | remainingInBatch={} | remainingPendingEstimate={}",
                processed,
                synced,
                failed,
                (candidates.size - processed).coerceAtLeast(0),
                (pendingTotal - processed).coerceAtLeast(0),
            )
        }

        return MovieCompaniesBatchSyncResponse(
            requestedLimit = requestedLimit,
            candidates = candidates.size,
            processed = processed,
            synced = synced,
            failed = failed,
        ).also { response ->
            logger.info(
                "Movie companies TMDb batch sync finished | requestedLimit={} | pendingTotal={} | candidates={} | processed={} | synced={} | failed={}",
                requestedLimit,
                pendingTotal,
                response.candidates,
                response.processed,
                response.synced,
                response.failed,
            )
        }
    }

    private fun syncFromTmdbInternal(movieId: Long): MovieCompaniesSyncResponse {
        val movie = requireMovie(movieId)
        val tmdbId = requireTmdbId(movieId)
        val details =
            tmdbApiClient.fetchMovieDetails(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb movie details not found")

        val resolvedCompanies =
            details.productionCompanies
                .map { company ->
                    val localCompany =
                        upsertCompany(
                            tmdbId = company.tmdbId,
                            rawName = company.name,
                            logoPath = company.logoPath,
                            originCountry = company.originCountry,
                        )
                    MovieCompanyAssignmentRepository.UpsertMovieCompanyRequest(
                        movieId = movie.id,
                        companyId = localCompany.id,
                        companyType = MovieCompanyType.PRODUCTION,
                    )
                }.distinctBy { listOf(it.companyId, it.companyType.name) }

        movieCompanyAssignmentRepository.replaceForMovie(movie.id, resolvedCompanies)

        return MovieCompaniesSyncResponse(
            movieId = movieId,
            syncedCount = resolvedCompanies.size,
            visibleCount = movieQueryRepository.getMovieCompanies(movieId).size,
        ).also {
            movieCompaniesCrudRepository.markCompaniesSynced(movieId)
        }
    }

    private fun requireMovie(movieId: Long): Movie =
        movieRepository.findById(movieId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
        }

    private fun requireTmdbId(movieId: Long): String =
        movieQueryRepository
            .getMovieDetails(movieId)
            .externalIds
            .firstOrNull { it.provider == "TMDB" }
            ?.externalId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Filme sem vínculo TMDb")

    private fun upsertCompany(
        tmdbId: String,
        rawName: String,
        logoPath: String?,
        originCountry: String?,
    ): MovieCompany {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        val normalizedName = name.lowercase()
        val logoUrl = logoPath?.let(manualMovieCatalogService::buildTmdbImageUrl)
        val slug = SlugTextUtil.normalize("$name $tmdbId", maxLength = 80)
        val existing = movieCompanyRepository.findByTmdbId(tmdbId)
        return if (existing == null) {
            movieCompanyRepository.save(
                MovieCompany(
                    tmdbId = tmdbId,
                    name = name,
                    normalizedName = normalizedName,
                    slug = slug,
                    logoUrl = logoUrl,
                    originCountry = originCountry,
                ),
            )
        } else {
            movieCompanyRepository.save(
                existing.copy(
                    name = name,
                    normalizedName = normalizedName,
                    slug = slug,
                    logoUrl = logoUrl ?: existing.logoUrl,
                    originCountry = originCountry ?: existing.originCountry,
                    updatedAt = Instant.now(),
                ),
            )
        }
    }
}
