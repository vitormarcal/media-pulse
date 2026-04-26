package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieTermCreateRequest
import dev.marcal.mediapulse.server.api.movies.MovieTermDto
import dev.marcal.mediapulse.server.api.movies.MovieTermKindDto
import dev.marcal.mediapulse.server.api.movies.MovieTermsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieTermsSyncResponse
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTerm
import dev.marcal.mediapulse.server.model.movie.MovieTermKind
import dev.marcal.mediapulse.server.model.movie.MovieTermSource
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermsCrudRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MovieTermsService(
    private val movieRepository: MovieRepository,
    private val movieQueryRepository: MovieQueryRepository,
    private val movieTermRepository: MovieTermRepository,
    private val movieTermAssignmentRepository: MovieTermAssignmentRepository,
    private val movieTermsCrudRepository: MovieTermsCrudRepository,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun syncFromTmdb(movieId: Long): MovieTermsSyncResponse = syncFromTmdbInternal(movieId)

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

    fun syncAllFromTmdb(limit: Int = 100): MovieTermsBatchSyncResponse {
        val requestedLimit = limit.coerceIn(1, 1000)
        val pendingTotal = movieTermsCrudRepository.countPendingTmdbSyncCandidates()
        val candidates = movieTermsCrudRepository.findTmdbSyncCandidates(requestedLimit)
        var synced = 0
        var failed = 0
        var processed = 0

        logger.info(
            "Movie terms TMDb batch sync started | requestedLimit={} | pendingTotal={} | selectedCandidates={}",
            requestedLimit,
            pendingTotal,
            candidates.size,
        )

        candidates.forEach { candidate ->
            runCatching {
                transactionTemplate.execute {
                    syncFromTmdbInternal(candidate.movieId)
                } ?: error("Batch sync transaction returned null")
            }.onSuccess {
                synced++
            }.onFailure { ex ->
                failed++
                logger.warn(
                    "Failed to sync movie terms from TMDb in batch | movieId={} tmdbId={}",
                    candidate.movieId,
                    candidate.tmdbId,
                    ex,
                )
            }

            processed++
            logger.info(
                "Movie terms TMDb batch sync progress | processed={} | synced={} | failed={} | remainingInBatch={} | remainingPendingEstimate={}",
                processed,
                synced,
                failed,
                (candidates.size - processed).coerceAtLeast(0),
                (pendingTotal - processed).coerceAtLeast(0),
            )
        }

        return MovieTermsBatchSyncResponse(
            requestedLimit = requestedLimit,
            candidates = candidates.size,
            processed = processed,
            synced = synced,
            failed = failed,
        ).also { response ->
            logger.info(
                "Movie terms TMDb batch sync finished | requestedLimit={} | pendingTotal={} | candidates={} | processed={} | synced={} | failed={}",
                requestedLimit,
                pendingTotal,
                response.candidates,
                response.processed,
                response.synced,
                response.failed,
            )
        }
    }

    private fun syncFromTmdbInternal(movieId: Long): MovieTermsSyncResponse {
        val movie = requireMovie(movieId)
        val tmdbId =
            movieQueryRepository
                .getMovieDetails(movieId)
                .externalIds
                .firstOrNull { it.provider == "TMDB" }
                ?.externalId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Filme sem vínculo TMDb")

        val snapshot =
            manualMovieCatalogService.fetchTmdbMovieSnapshot(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb details not found")

        val syncedTerms =
            snapshot.genres.map { MovieTermKind.GENRE to it } +
                snapshot.keywords.map { MovieTermKind.TAG to it }

        val termIds =
            syncedTerms
                .map { (kind, name) -> upsertTermAndAssignment(movie, kind, name, MovieTermSource.TMDB).id }
                .distinct()

        return MovieTermsSyncResponse(
            movieId = movieId,
            syncedCount = termIds.size,
            visibleCount = movieQueryRepository.getMovieTerms(movieId).count { it.active },
        ).also {
            movieTermsCrudRepository.markTermsSynced(movieId)
        }
    }

    @Transactional
    fun addTerm(
        movieId: Long,
        request: MovieTermCreateRequest,
    ): MovieTermDto {
        val movie = requireMovie(movieId)
        val term = upsertTermAndAssignment(movie, request.kind.toModel(), request.name, MovieTermSource.USER)
        return movieQueryRepository.getMovieTerms(movieId).first { it.id == term.id }
    }

    @Transactional
    fun updateGlobalVisibility(
        termId: Long,
        hidden: Boolean,
    ): MovieTermDto {
        val term =
            movieTermRepository.findById(termId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found")
            }

        val saved = movieTermRepository.save(term.copy(hidden = hidden, updatedAt = Instant.now()))
        return movieQueryRepository.findTerm(saved.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found")
    }

    @Transactional
    fun updateMovieVisibility(
        movieId: Long,
        termId: Long,
        hidden: Boolean,
    ): MovieTermDto {
        requireMovie(movieId)
        val updated = movieTermAssignmentRepository.updateVisibility(movieId, termId, hidden)
        if (updated == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie term assignment not found")
        }
        return movieQueryRepository.getMovieTerms(movieId).firstOrNull { it.id == termId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found for movie")
    }

    private fun requireMovie(movieId: Long): Movie =
        movieRepository.findById(movieId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
        }

    private fun upsertTermAndAssignment(
        movie: Movie,
        kind: MovieTermKind,
        rawName: String,
        assignmentSource: MovieTermSource,
    ): MovieTerm {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        if (name.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name deve ser preenchido")
        }

        val normalizedName = name.lowercase()
        val existing = movieTermRepository.findByKindAndNormalizedName(kind, normalizedName)
        val term =
            existing
                ?: movieTermRepository.save(
                    MovieTerm(
                        name = name,
                        normalizedName = normalizedName,
                        slug = SlugTextUtil.normalize(name, maxLength = 64),
                        kind = kind,
                        source = assignmentSource,
                    ),
                )

        val refreshedTerm =
            if (existing != null && existing.hidden) {
                movieTermRepository.save(existing.copy(hidden = false, updatedAt = Instant.now()))
            } else {
                term
            }

        movieTermAssignmentRepository.upsert(movie.id, refreshedTerm.id, assignmentSource)
        return refreshedTerm
    }

    private fun MovieTermKindDto.toModel(): MovieTermKind = MovieTermKind.valueOf(name)
}
