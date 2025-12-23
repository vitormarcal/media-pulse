package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SpotifyPollScheduler(
    private val props: SpotifyProperties,
    private val spotifyImportService: SpotifyImportService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Scheduled(cron = "\${media-pulse.spotify.poll.cron}")
    fun scheduled() {
        if (!props.enabled) return
        if (!props.poll.enabled) return
        if (!props.import.enabled) return

        scope.launch {
            try {
                val imported = spotifyImportService.importRecentlyPlayed()
                logger.info("Spotify poll done | imported={}", imported)
            } catch (e: Exception) {
                logger.error("Spotify poll failed", e)
            }
        }
    }
}
