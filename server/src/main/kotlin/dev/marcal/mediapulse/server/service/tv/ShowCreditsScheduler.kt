package dev.marcal.mediapulse.server.service.tv

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component
class ShowCreditsScheduler(
    private val service: ShowCreditsService,
) {
    private val running = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${media-pulse.tmdb.enrichment.interval-ms:120000}")
    fun scheduled() {
        if (!running.compareAndSet(false, true)) return
        try {
            service.syncAllFromTmdb(limit = 25)
        } finally {
            running.set(false)
        }
    }
}
