package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.config.PlexProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class PlexStartupImporter(
    private val plexImportService: PlexImportService,
    private val plexProps: PlexProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!plexProps.import.enabled || !plexProps.import.runOnStartup) {
            return
        }

        scope.launch {
            try {
                logger.info("Starting Plex initial import on startup")
                val stats =
                    plexImportService.importAllArtistsAndAlbums(
                        pageSize = plexProps.import.pageSize,
                    )
                logger.info("Plex initial import done. artistsSeen=${stats.artistsSeen}, albumsSeen=${stats.albumsSeen}")
            } catch (e: Exception) {
                logger.error("Error running Plex initial import", e)
            }
        }
    }
}
