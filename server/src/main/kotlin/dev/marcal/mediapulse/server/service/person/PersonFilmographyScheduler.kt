package dev.marcal.mediapulse.server.service.person

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PersonFilmographyScheduler(
    private val movieService: PersonFilmographyService,
    private val showService: PersonShowFilmographyService,
) {
    @Scheduled(fixedDelayString = "\${media-pulse.tmdb.enrichment.interval-ms:120000}")
    fun scheduled() {
        movieService.enrichPending()
        showService.enrichPending()
    }
}
