package dev.marcal.mediapulse.server.service.person

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PersonAutomaticEnrichmentScheduler(
    private val service: PersonAutomaticEnrichmentService,
) {
    @Scheduled(fixedDelayString = "\${media-pulse.tmdb.enrichment.interval-ms:120000}")
    fun scheduled() {
        service.enrichPending()
    }
}
