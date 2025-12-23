package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import dev.marcal.mediapulse.server.service.plex.PlexWebhookDispatcher
import dev.marcal.mediapulse.server.service.spotify.SpotifyEventDispatcher
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ProcessEventSourceService(
    private val repository: EventSourceCrudRepository,
    private val plexWebhookDispatcher: PlexWebhookDispatcher,
    private val spotifyEventDispatcher: SpotifyEventDispatcher,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Asynchronously processes a webhook event by its ID.
     *
     * @param eventId The ID of the webhook event to process.
     */
    @Async
    suspend fun executeAsync(eventId: Long) {
        this.execute(eventId)
    }

    /**
     * Processes a webhook event by dispatching it to the appropriate handler based on the provider.
     *
     * @param eventId The ID of the webhook event to process.
     */
    suspend fun execute(eventId: Long) {
        val event = repository.findByIdOrNull(eventId) ?: return

        val eventUpdated =
            try {
                when (event.provider) {
                    "plex" -> {
                        logger.info("Processing Plex webhook event with ID: $eventId")
                        plexWebhookDispatcher.dispatch(event.payload, eventId)
                        event.markAsSuccess()
                    }

                    "spotify" -> {
                        logger.info("Processing spotify event with ID: $eventId")
                        spotifyEventDispatcher.dispatch(event.payload, eventId)
                        event.markAsSuccess()
                    }

                    else -> {
                        logger.warn("Unsupported provider: ${event.provider} for event ID: $eventId")
                        event.markAsFailed("Unsupported provider: ${event.provider}")
                    }
                }
            } catch (ex: Exception) {
                logger.error("Error processing webhook event with ID $eventId", ex)
                event.markAsFailed(ex.message?.take(255) ?: "Unknown error")
            }

        repository.save(eventUpdated)
    }
}
