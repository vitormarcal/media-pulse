package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.config.PlexProperties
import dev.marcal.mediapulse.server.service.plex.import.PlexImportService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class PlexImportScheduler(
    private val plexImportService: PlexImportService,
    private val plexProps: PlexProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${media-pulse.plex.import.schedule-cron}")
    fun scheduledImport() {
        if (!plexProps.import.enabled) {
            return
        }

        runBlocking {
            try {
                val stats =
                    plexImportService.importAllArtistsAndAlbums(
                        pageSize = plexProps.import.pageSize,
                    )
                logger.info(
                    "Plex scheduled import done. artistsSeen=${stats.artistsSeen}, albumsSeen=${stats.albumsSeen}, tracksSeen=${stats.tracksSeen}",
                )
            } catch (e: Exception) {
                logger.error("Error running Plex scheduled import", e)
            }
        }
    }
}
