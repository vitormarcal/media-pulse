package dev.marcal.mediapulse.server.dispatcher

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.service.plex.PlexMusicPlaybackService
import dev.marcal.mediapulse.server.service.plex.PlexSeriesPlaybackService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlexWebhookDispatcher(
    private val objectMapper: ObjectMapper,
    private val plexMusicPlaybackService: PlexMusicPlaybackService,
    private val plexSeriesPlaybackService: PlexSeriesPlaybackService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Dispatches the webhook payload to the appropriate handler based on the event type.
     *
     * @param payload The JSON payload received from the Plex webhook.
     * @param eventId The ID of the event, used for logging and tracking.
     * @throws IllegalStateException if the event type is unsupported or if processing fails.
     */
    fun dispatch(
        payload: String,
        eventId: Long?,
    ) {
        val webhookPayload = parsePayload(payload)

        return when (webhookPayload.event) {
            "media.scrobble" -> {
                doPlaybackScrobble(webhookPayload, eventId)
            }
            else -> {
                logger.warn("Unsupported event: ${webhookPayload.event}")
                throw IllegalStateException("Unsupported event: ${webhookPayload.event}")
            }
        }
    }

    private fun parsePayload(payload: String): PlexWebhookPayload =
        try {
            objectMapper.readValue(payload, PlexWebhookPayload::class.java)
        } catch (ex: Exception) {
            logger.error("Failed to parse webhook payload: $payload", ex)
            throw IllegalStateException("Invalid webhook payload format", ex)
        }

    private fun doPlaybackScrobble(
        webhookPayload: PlexWebhookPayload,
        eventId: Long?,
    ) {
        when (webhookPayload.metadata.type) {
            "track" ->
                plexMusicPlaybackService.processScrobble(webhookPayload, eventId)
                    ?: throw IllegalStateException("Track playback not found for: ${webhookPayload.metadata.title}")
            "episode" ->
                plexSeriesPlaybackService.processScrobble(webhookPayload, eventId)
                    ?: throw IllegalStateException("Episode playback not found for: ${webhookPayload.metadata.title}")
            else -> {
                logger.warn("Unsupported metadata type for scrobble: ${webhookPayload.metadata.type}")
                throw IllegalStateException("Unsupported metadata type: ${webhookPayload.metadata.type}")
            }
        }
    }
}
