package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCollectionMemberDto
import dev.marcal.mediapulse.server.api.movies.MovieCollectionMembersResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicBoolean

@Service
class MovieCollectionMembersService(
    private val movieCollectionCrudRepository: MovieCollectionCrudRepository,
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
    fun getMembers(collectionId: Long): MovieCollectionMembersResponse {
        val collection =
            movieCollectionCrudRepository.findCollection(collectionId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie collection not found")
        val members = movieCollectionCrudRepository.findMembers(collectionId)

        return MovieCollectionMembersResponse(
            collectionId = collection.id,
            tmdbId = collection.tmdbId,
            name = collection.name,
            overview = collection.overview,
            posterUrl = collection.posterUrl,
            backdropUrl = collection.backdropUrl,
            members =
                members.map { member ->
                    MovieCollectionMemberDto(
                        tmdbId = member.tmdbId,
                        title = member.title,
                        originalTitle = member.originalTitle,
                        year = member.year,
                        overview = member.overview,
                        posterUrl = member.posterUrl,
                        backdropUrl = member.backdropUrl,
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
            val candidates = movieCollectionCrudRepository.findPendingCollectionIds(limit.coerceIn(1, 200))
            val completed = candidates.count(::refreshMembers)
            BatchResult(candidates.size, completed, candidates.size - completed)
        } finally {
            running.set(false)
        }
    }

    fun refreshMembers(collectionId: Long): Boolean {
        val collection =
            movieCollectionCrudRepository.findCollection(collectionId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie collection not found")
        val tmdbCollection =
            tmdbApiClient.fetchMovieCollectionDetails(collection.tmdbId)
                ?: run {
                    tx.inTx {
                        movieCollectionCrudRepository.markMemberSyncFailure(collectionId, "TMDb collection unavailable")
                    }
                    return false
                }

        tx.inTx {
            movieCollectionCrudRepository.replaceMemberSnapshot(
                collectionId = collection.id,
                name = tmdbCollection.name,
                overview = tmdbCollection.overview,
                posterUrl = tmdbCollection.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                backdropUrl = tmdbCollection.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                members =
                    tmdbCollection.parts.map { part ->
                        MovieCollectionCrudRepository.MovieCollectionMemberSnapshot(
                            tmdbId = part.tmdbId,
                            title = part.title ?: part.originalTitle ?: "TMDb ${part.tmdbId}",
                            originalTitle = part.originalTitle,
                            year = part.releaseYear,
                            overview = part.overview,
                            posterUrl = part.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                            backdropUrl = part.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        )
                    },
            )
        }
        return true
    }

    fun refreshAndGetMembers(collectionId: Long): MovieCollectionMembersResponse {
        if (!refreshMembers(collectionId)) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDb collection unavailable")
        }
        return getMembers(collectionId)
    }
}
