package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCompanyMemberDto
import dev.marcal.mediapulse.server.api.movies.MovieCompanyMembersResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyMembersRepository
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicBoolean

@Service
class MovieCompanyMembersService(
    private val repository: MovieCompanyMembersRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val tx: TxUtil,
) {
    data class BatchResult(
        val candidates: Int,
        val completed: Int,
        val pending: Int,
    )

    private val running = AtomicBoolean(false)

    @Transactional(readOnly = true)
    fun getMembers(companyId: Long): MovieCompanyMembersResponse {
        val company =
            repository.findCompany(companyId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie company not found")

        return MovieCompanyMembersResponse(
            companyId = company.id,
            tmdbId = company.tmdbId,
            name = company.name,
            logoUrl = company.logoUrl,
            originCountry = company.originCountry,
            members =
                repository.findMembers(companyId).map { member ->
                    MovieCompanyMemberDto(
                        tmdbId = member.tmdbId,
                        title = member.title,
                        originalTitle = member.originalTitle,
                        year = member.year,
                        overview = member.overview,
                        posterUrl = member.posterUrl,
                        backdropUrl = null,
                        tmdbUrl = "https://www.themoviedb.org/movie/${member.tmdbId}",
                        localMovieId = member.localMovieId,
                        localSlug = member.localSlug,
                        inCatalog = member.localMovieId != null,
                    )
                },
        )
    }

    fun enrichPending(limit: Int = 25): BatchResult {
        if (!running.compareAndSet(false, true)) return BatchResult(0, 0, 0)
        return try {
            val candidates = repository.findPendingCompanyIds(limit.coerceIn(1, 200))
            val completed = candidates.count(::refreshMembers)
            BatchResult(candidates.size, completed, candidates.size - completed)
        } finally {
            running.set(false)
        }
    }

    fun refreshMembers(companyId: Long): Boolean {
        val company =
            repository.findCompany(companyId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie company not found")
        val tmdbMovies =
            tmdbApiClient.fetchCompanyMovies(company.tmdbId) ?: run {
                tx.inTx { repository.markSyncFailure(companyId, "TMDb company movies unavailable") }
                return false
            }

        tx.inTx {
            repository.replaceSnapshot(
                companyId,
                tmdbMovies.movies.map { movie ->
                    MovieCompanyMembersRepository.MemberSnapshot(
                        tmdbId = movie.tmdbId,
                        title = movie.title ?: movie.originalTitle ?: "TMDb ${movie.tmdbId}",
                        originalTitle = movie.originalTitle,
                        year = movie.releaseYear,
                        overview = movie.overview,
                        posterUrl = movie.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                    )
                },
            )
        }
        return true
    }

    fun refreshAndGetMembers(companyId: Long): MovieCompanyMembersResponse {
        if (!refreshMembers(companyId)) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDb company movies unavailable")
        }
        return getMembers(companyId)
    }
}
