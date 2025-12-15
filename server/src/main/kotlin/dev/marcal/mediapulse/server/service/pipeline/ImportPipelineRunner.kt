package dev.marcal.mediapulse.server.service.pipeline

import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.config.PipelineProperties
import dev.marcal.mediapulse.server.config.PlexProperties
import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzAlbumGenreEnrichmentService
import dev.marcal.mediapulse.server.service.plex.import.PlexImportService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class ImportPipelineRunner(
    private val pipelineProps: PipelineProperties,
    private val plexProps: PlexProperties,
    private val mbProps: MusicBrainzProperties,
    private val plexImportService: PlexImportService,
    private val mbService: MusicBrainzAlbumGenreEnrichmentService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    suspend fun run(reason: String) {
        if (!pipelineProps.enabled) return

        if (!running.compareAndSet(false, true)) {
            logger.info("Pipeline already running | ignored | reason={}", reason)
            return
        }

        try {
            logger.info("Pipeline started | reason={}", reason)

            if (plexProps.import.enabled) {
                val stats = plexImportService.importAllArtistsAndAlbums(pageSize = plexProps.import.pageSize)
                logger.info(
                    "Pipeline Plex done | artistsSeen={} albumsSeen={} tracksSeen={}",
                    stats.artistsSeen,
                    stats.albumsSeen,
                    stats.tracksSeen,
                )
            } else {
                logger.info("Pipeline Plex skipped | reason=disabled")
            }

            if (mbProps.enabled) {
                val processed = mbService.enrichBatch(mbProps.enrich.batchSize)
                logger.info("Pipeline MusicBrainz done | processed={}", processed)
            } else {
                logger.info("Pipeline MusicBrainz skipped | reason=disabled")
            }

            logger.info("Pipeline finished | reason={}", reason)
        } catch (e: Exception) {
            logger.error("Pipeline failed | reason={}", reason, e)
        } finally {
            running.set(false)
        }
    }
}
