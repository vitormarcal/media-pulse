package dev.marcal.mediapulse.server.service.plex

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import dev.marcal.mediapulse.server.service.dispatch.EventDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PlexWebhookDispatcher(
    private val objectMapper: ObjectMapper,
    private val plexMusicPlaybackService: PlexMusicPlaybackService,
    private val plexMovieWatchService: PlexMovieWatchService,
) : EventDispatcher {
    override val provider: String = "plex"

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Dispatches a Plex webhook payload and returns the semantic processing result.
     *
     * Domain outcomes are returned as DispatchResult.
     * Technical failures are propagated as exceptions.
     */
    override suspend fun dispatch(
        payload: String,
        eventId: Long?,
    ): DispatchResult {
        val webhookPayload = parsePayload(payload)

        return when (webhookPayload.event) {
            "media.scrobble" -> {
                doPlaybackScrobble(webhookPayload, eventId)
            }
            else -> {
                logger.debug("Unsupported Plex event: ${webhookPayload.event}")
                DispatchResult.UNSUPPORTED
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

    private suspend fun doPlaybackScrobble(
        webhookPayload: PlexWebhookPayload,
        eventId: Long?,
    ): DispatchResult =
        when (webhookPayload.metadata.type) {
            "track" -> {
                val processed = plexMusicPlaybackService.processScrobble(webhookPayload, eventId)
                if (processed != null) {
                    DispatchResult.SUCCESS
                } else {
                    throw IllegalStateException("Track playback not found for: ${webhookPayload.metadata.title}")
                }
            }
            "movie" -> {
                val processed = plexMovieWatchService.processScrobble(webhookPayload)
                if (processed != null) {
                    DispatchResult.SUCCESS
                } else {
                    throw IllegalStateException("Movie watch not found for: ${webhookPayload.metadata.title}")
                }
            }
            "episode" -> {
                logger.debug("Ignored Plex scrobble type: episode")
                DispatchResult.IGNORED
            }

            else -> {
                logger.debug("Unsupported Plex scrobble metadata type: ${webhookPayload.metadata.type}")
                DispatchResult.UNSUPPORTED
            }
        }
}
