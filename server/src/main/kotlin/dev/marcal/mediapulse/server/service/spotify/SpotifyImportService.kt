package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.integration.spotify.SpotifyApiClient
import dev.marcal.mediapulse.server.integration.spotify.SpotifyReauthorizationRequiredException
import dev.marcal.mediapulse.server.integration.spotify.SpotifyTokenRefreshException
import dev.marcal.mediapulse.server.model.spotify.SpotifyAuthorizationStatus
import dev.marcal.mediapulse.server.repository.spotify.SpotifySyncStateRepository
import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SpotifyImportService(
    private val spotifyApi: SpotifyApiClient,
    private val syncRepo: SpotifySyncStateRepository,
    private val eventSourceService: EventSourceService,
    private val processEventSourceService: ProcessEventSourceService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    suspend fun importRecentlyPlayed(
        resetCursor: Boolean = false,
        maxPages: Int? = null,
    ): Int {
        if (!running.compareAndSet(false, true)) {
            logger.info("Spotify import already running | ignored")
            return -1
        }

        val runId =
            java.util.UUID
                .randomUUID()
                .toString()
                .take(8)
        val start = System.currentTimeMillis()
        logger.info("Spotify import started | runId={} resetCursor={} maxPages={}", runId, resetCursor, maxPages)

        try {
            if (resetCursor) {
                syncRepo.updateCursor(0)
            }

            val state = syncRepo.getOrCreateSingleton()
            var afterMs = state.cursorAfterMs
            var imported = 0
            var maxSeenMs = afterMs
            var pages = 0
            val limit = 50

            while (true) {
                if (maxPages != null && pages >= maxPages) break

                val page = spotifyApi.getRecentlyPlayed(afterMs = afterMs, limit = limit)
                val items = page.items

                if (items.isEmpty()) break

                for (it in items) {
                    val playedAt = Instant.parse(it.playedAt)
                    it.track?.id ?: continue

                    val payload = objectMapper.writeValueAsString(it)

                    val saved =
                        eventSourceService.save(
                            provider = "spotify",
                            payload = payload,
                        )

                    processEventSourceService.execute(saved.id)

                    imported++
                    val ms = playedAt.toEpochMilli()
                    if (ms > maxSeenMs) maxSeenMs = ms
                }

                afterMs = maxSeenMs + 1
                pages++
            }

            if (maxSeenMs > state.cursorAfterMs) {
                syncRepo.updateCursor(maxSeenMs + 1)
            }

            syncRepo.markHealthy()
            logger.info("Spotify import completed | runId={} imported={} elapsedMs={}", runId, imported, System.currentTimeMillis() - start)
            return imported
        } catch (e: SpotifyReauthorizationRequiredException) {
            syncRepo.markFailure(SpotifyAuthorizationStatus.REAUTHORIZATION_REQUIRED, e.errorCode)
            logger.warn(
                "Spotify import stopped because reauthorization is required | runId={} elapsedMs={}",
                runId,
                System.currentTimeMillis() - start,
            )
            throw e
        } catch (e: Exception) {
            val errorCode = (e as? SpotifyTokenRefreshException)?.errorCode ?: "import_failed"
            syncRepo.markFailure(SpotifyAuthorizationStatus.ERROR, errorCode)
            logger.error("Spotify import failed | runId={} elapsedMs={}", runId, System.currentTimeMillis() - start, e)
            throw e
        } finally {
            running.set(false)
        }
    }
}
