package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedItem
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import dev.marcal.mediapulse.server.service.dispatch.EventDispatcher
import org.springframework.stereotype.Component

@Component
class SpotifyEventDispatcher(
    private val objectMapper: ObjectMapper,
    private val spotifyPlaybackService: SpotifyPlaybackService,
) : EventDispatcher {
    override val provider: String = "spotify"

    override suspend fun dispatch(
        payload: String,
        eventId: Long?,
    ): DispatchResult {
        val item = objectMapper.readValue(payload, SpotifyRecentlyPlayedItem::class.java)
        spotifyPlaybackService.processRecentlyPlayedItem(item, eventId)
        return DispatchResult.SUCCESS
    }
}
