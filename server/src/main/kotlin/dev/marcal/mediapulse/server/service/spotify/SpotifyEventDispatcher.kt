package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedItem
import org.springframework.stereotype.Component

@Component
class SpotifyEventDispatcher(
    private val objectMapper: ObjectMapper,
    private val spotifyPlaybackService: SpotifyPlaybackService,
) {
    suspend fun dispatch(
        payload: String,
        eventId: Long?,
    ) {
        val item = objectMapper.readValue(payload, SpotifyRecentlyPlayedItem::class.java)
        spotifyPlaybackService.processRecentlyPlayedItem(item, eventId)
    }
}
