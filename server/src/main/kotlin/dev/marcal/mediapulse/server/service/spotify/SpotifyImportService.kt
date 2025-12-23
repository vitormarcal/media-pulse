package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.integration.spotify.SpotifyApiClient
import dev.marcal.mediapulse.server.repository.spotify.SpotifySyncStateRepository
import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SpotifyImportService(
    private val spotifyApi: SpotifyApiClient,
    private val syncRepo: SpotifySyncStateRepository,
    private val eventSourceService: EventSourceService,
    private val processEventSourceService: ProcessEventSourceService,
    private val objectMapper: ObjectMapper,
) {
    suspend fun importRecentlyPlayed(
        resetCursor: Boolean = false,
        maxPages: Int? = null,
    ): Int {
        if (resetCursor) {
            syncRepo.updateCursor(0)
        }

        val state = syncRepo.getOrCreateSingleton()
        var afterMs = state.cursorAfterMs
        var imported = 0
        var maxSeenMs = afterMs
        var pages = 0

        while (true) {
            if (maxPages != null && pages >= maxPages) break

            val page = spotifyApi.getRecentlyPlayed(afterMs, limit = 50)
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

                processEventSourceService.executeAsync(saved.id)

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

        return imported
    }
}
