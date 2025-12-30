package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import dev.marcal.mediapulse.server.service.dispatch.EventDispatcher
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ProcessEventSourceService(
    private val repository: EventSourceCrudRepository,
    dispatchers: List<EventDispatcher>,
) {
    private val byProvider = dispatchers.associateBy { it.provider }

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Asynchronously processes a webhook event by its ID.
     *
     * @param eventId The ID of the webhook event to process.
     */
    @Async
    fun executeAsync(eventId: Long) {
        runBlocking { execute(eventId) }
    }

    /**
     * Processes a webhook event by dispatching it to the appropriate handler based on the provider.
     *
     * @param eventId The ID of the webhook event to process.
     */
    suspend fun execute(eventId: Long) {
        val event = repository.findByIdOrNull(eventId) ?: return

        val updated = dispatch(event)

        repository.save(updated)
    }

    private suspend fun dispatch(event: EventSource): EventSource {
        val eventId = event.id
        val dispatcher = byProvider[event.provider] ?: return event.markAsUnsupported("Unsupported provider: ${event.provider}".take(255))
        return try {
            val result = dispatcher.dispatch(event.payload, eventId)
            applyResult(event, result, event.provider)
        } catch (ex: Exception) {
            logger.error("Error processing eventId=$eventId", ex)
            event.markAsFailed(ex.message?.take(255) ?: "Unknown error")
        }
    }

    private fun applyResult(
        event: EventSource,
        result: DispatchResult,
        provider: String,
    ): EventSource =
        when (result) {
            DispatchResult.SUCCESS -> event.markAsSuccess()

            DispatchResult.UNSUPPORTED ->
                event.markAsUnsupported("Unsupported $provider event".take(255))

            DispatchResult.IGNORED ->
                event.markAsUnsupported("Ignored $provider event".take(255))
        }
}
