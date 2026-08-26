package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.repository.crud.MovieAutomaticEnrichmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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

        if (movie.termsSyncedAt == null) {
            runStep(movieId, "terms") { movieTermsService.syncFromTmdbIfLinked(movieId) }
        }
        if (movie.creditsSyncedAt == null) {
            runStep(movieId, "credits") { movieCreditsService.syncFromTmdbIfLinked(movieId) }
        }
        if (movie.companiesSyncedAt == null) {
            runStep(movieId, "companies") { movieCompaniesService.syncFromTmdbIfLinked(movieId) }
        }
    }

    private fun runStep(
        movieId: Long,
        step: String,
        action: () -> Unit,
    ) {
        runCatching(action).onFailure { ex ->
            logger.warn("Automatic movie enrichment step failed | movieId={} step={}", movieId, step, ex)
        }
    }
}
