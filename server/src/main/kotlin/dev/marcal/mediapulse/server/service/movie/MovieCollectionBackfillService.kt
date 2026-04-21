package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCollectionBackfillResponse
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MovieCollectionBackfillService(
    private val movieCollectionCrudRepository: MovieCollectionCrudRepository,
    private val movieRepository: MovieRepository,
    private val manualMovieCatalogService: ManualMovieCatalogService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun backfill(limit: Int = 50): MovieCollectionBackfillResponse {
        val requestedLimit = limit.coerceIn(1, 500)
        val candidates = movieCollectionCrudRepository.findBackfillCandidates(requestedLimit)
        var linked = 0
        var withoutCollection = 0
        var failed = 0

        candidates.forEach { candidate ->
            val movie = movieRepository.findById(candidate.movieId).orElse(null)
            if (movie == null) {
                failed++
                return@forEach
            }

            val snapshot =
                runCatching {
                    manualMovieCatalogService.fetchTmdbMovieSnapshot(candidate.tmdbId)
                }.onFailure { ex ->
                    logger.warn(
                        "Failed to fetch TMDb movie collection during backfill | movieId={} tmdbId={}",
                        candidate.movieId,
                        candidate.tmdbId,
                        ex,
                    )
                }.getOrNull()

            if (snapshot == null) {
                failed++
                return@forEach
            }

            if (snapshot.collection == null) {
                movieCollectionCrudRepository.markCollectionChecked(candidate.movieId)
                withoutCollection++
                return@forEach
            }

            runCatching {
                manualMovieCatalogService.assignTmdbCollection(movie, snapshot)
            }.onSuccess { updatedMovie ->
                if (updatedMovie.collectionId != null) {
                    linked++
                } else {
                    withoutCollection++
                }
            }.onFailure { ex ->
                failed++
                logger.warn(
                    "Failed to assign TMDb movie collection during backfill | movieId={} tmdbId={}",
                    candidate.movieId,
                    candidate.tmdbId,
                    ex,
                )
            }
        }

        return MovieCollectionBackfillResponse(
            requestedLimit = requestedLimit,
            candidates = candidates.size,
            processed = candidates.size,
            linked = linked,
            withoutCollection = withoutCollection,
            failed = failed,
        )
    }
}
