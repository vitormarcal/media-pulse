package dev.marcal.mediapulse.server.service.person

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PersonFilmographyCompactionScheduler(
    private val service: PersonFilmographyCompactionService,
) {
    @Scheduled(cron = "\${media-pulse.tmdb.filmography-compaction.cron:0 30 4 * * *}")
    fun scheduled() {
        service.compact()
    }
}
