package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.repository.crud.MovieAutomaticEnrichmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service
class MovieAutomaticEnrichmentService(
    private val pendingRepository: MovieAutomaticEnrichmentRepository,
    private val movieRepository: MovieRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val movieTermsService: MovieTermsService,
    private val movieCreditsService: MovieCreditsService,
    private val movieCompaniesService: MovieCompaniesService,
) {
    data class BatchResult(
        val candidates: Int,
        val completed: Int,
        val pending: Int,
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    fun enrichPending(limit: Int = 25): BatchResult {
        if (!running.compareAndSet(false, true)) return BatchResult(0, 0, 0)

        return try {
            val candidates = pendingRepository.findPendingMovieIds(limit.coerceIn(1, 200))
            var completed = 0
            candidates.forEach { movieId ->
                enrichMovie(movieId)
                val refreshed = movieRepository.findById(movieId).orElse(null)
                if (refreshed != null &&
                    refreshed.tmdbId != null &&
                    refreshed.termsSyncedAt != null &&
                    refreshed.creditsSyncedAt != null &&
                    refreshed.companiesSyncedAt != null
                ) {
                    completed++
                }
            }
            BatchResult(candidates.size, completed, candidates.size - completed)
        } finally {
            running.set(false)
        }
    }

    fun enrichMovie(movieId: Long) {
        var movie = movieRepository.findById(movieId).orElse(null) ?: return

        val imdbId = movie.imdbId
        if (movie.tmdbId == null && imdbId != null) {
            val resolvedTmdbId = tmdbApiClient.findMovieTmdbIdByImdbId(imdbId)
            pendingRepository.markTmdbResolutionChecked(movieId)
            if (resolvedTmdbId == null) {
                logger.info("Movie enrichment waiting for TMDb resolution | movieId={} imdbId={}", movieId, imdbId)
                return
            }
            runCatching {
                manualMovieCatalogService.linkExternalIdIfAvailable(movieId, Provider.TMDB, resolvedTmdbId)
            }.onFailure { ex ->
                logger.warn("Failed to link resolved TMDb id | movieId={} tmdbId={}", movieId, resolvedTmdbId, ex)
                return
            }
            movie = movieRepository.findById(movieId).orElse(movie)
        }

        if (movie.tmdbId == null) return

        if (shouldAttempt(movie.termsSyncedAt, movie.termsSyncAttemptedAt)) {
            runStep(movieId, "terms", pendingRepository::markTermsFailure) {
                movieTermsService.syncFromTmdbIfLinked(movieId)
            }
        }
        if (shouldAttempt(movie.creditsSyncedAt, movie.creditsSyncAttemptedAt)) {
            runStep(movieId, "credits", pendingRepository::markCreditsFailure) {
                movieCreditsService.syncFromTmdbIfLinked(movieId)
            }
        }
        if (shouldAttempt(movie.companiesSyncedAt, movie.companiesSyncAttemptedAt)) {
            runStep(movieId, "companies", pendingRepository::markCompaniesFailure) {
                movieCompaniesService.syncFromTmdbIfLinked(movieId)
            }
        }
    }

    private fun runStep(
        movieId: Long,
        step: String,
        markFailure: (Long, String) -> Int,
        action: () -> Unit,
    ) {
        runCatching(action).onFailure { ex ->
            runCatching { markFailure(movieId, ex.message ?: ex.javaClass.simpleName) }
                .onFailure { persistenceError ->
                    logger.warn(
                        "Failed to record automatic movie enrichment error | movieId={} step={}",
                        movieId,
                        step,
                        persistenceError,
                    )
                }
            logger.warn("Automatic movie enrichment step failed | movieId={} step={}", movieId, step, ex)
        }
    }

    private fun shouldAttempt(
        syncedAt: Instant?,
        attemptedAt: Instant?,
    ): Boolean = syncedAt == null && (attemptedAt == null || attemptedAt <= Instant.now().minus(RETRY_DELAY))

    private companion object {
        val RETRY_DELAY: Duration = Duration.ofDays(1)
    }
}
