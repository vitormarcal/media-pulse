package dev.marcal.mediapulse.server.service.movie

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MovieAutomaticEnrichmentScheduler(
    private val service: MovieAutomaticEnrichmentService,
) {
    @Scheduled(fixedDelayString = "\${media-pulse.tmdb.enrichment.interval-ms:120000}")
    fun scheduled() {
        service.enrichPending()
    }
}
