package dev.marcal.mediapulse.server.service.pipeline

import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.config.PipelineProperties
import dev.marcal.mediapulse.server.config.PlexProperties
import dev.marcal.mediapulse.server.config.SpotifyProperties
import dev.marcal.mediapulse.server.service.hardcover.HardcoverImportService
import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzAlbumGenreEnrichmentService
import dev.marcal.mediapulse.server.service.plex.import.PlexMovieImportService
import dev.marcal.mediapulse.server.service.plex.import.PlexMusicImportService
import dev.marcal.mediapulse.server.service.spotify.SpotifyImportService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class ImportPipelineRunner(
    private val pipelineProps: PipelineProperties,
    private val plexProps: PlexProperties,
    private val mbProps: MusicBrainzProperties,
    private val spotifyProps: SpotifyProperties,
    private val hardcoverProperties: HardcoverProperties,
    private val plexMusicImportService: PlexMusicImportService,
    private val plexMovieImportService: PlexMovieImportService,
    private val mbService: MusicBrainzAlbumGenreEnrichmentService,
    private val spotifyImportService: SpotifyImportService,
    private val hardcoverImportService: HardcoverImportService,
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
                val stats = plexMusicImportService.importAllMusicLibrary(pageSize = plexProps.import.pageSize)
                logger.info(
                    "Pipeline Plex music done | artistsSeen={} albumsSeen={} tracksSeen={}",
                    stats.artistsSeen,
                    stats.albumsSeen,
                    stats.tracksSeen,
                )
            } else {
                logger.info("Pipeline Plex music skipped | reason=disabled")
            }

            if (plexProps.import.moviesEnabled) {
                val stats = plexMovieImportService.importAllMovies(pageSize = plexProps.import.pageSize)
                logger.info(
                    "Pipeline Plex movies done | moviesSeen={} moviesUpserted={}",
                    stats.moviesSeen,
                    stats.moviesUpserted,
                )
            } else {
                logger.info("Pipeline Plex movies skipped | reason=disabled")
            }

            if (spotifyProps.enabled && spotifyProps.import.enabled) {
                val imported = spotifyImportService.importRecentlyPlayed()
                logger.info("Pipeline Spotify done | imported={}", imported)
            } else {
                logger.info("Pipeline Spotify skipped | reason=disabled")
            }

            if (mbProps.enabled) {
                val processed = mbService.enrichBatch(mbProps.enrich.batchSize)
                logger.info("Pipeline MusicBrainz done | processed={}", processed)
            } else {
                logger.info("Pipeline MusicBrainz skipped | reason=disabled")
            }

            if (hardcoverProperties.enabled) {
                val processed = hardcoverImportService.importUserBooks()
                logger.info("Pipeline Hardcover done | processed={}", processed)
            } else {
                logger.info("Pipeline Hardcover skipped | reason=disabled")
            }

            logger.info("Pipeline finished | reason={}", reason)
        } catch (e: Exception) {
            logger.error("Pipeline failed | reason={}", reason, e)
        } finally {
            running.set(false)
        }
    }
}
