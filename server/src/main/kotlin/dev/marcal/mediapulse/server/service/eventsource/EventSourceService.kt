package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.EventSourceRepository
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.stereotype.Service

@Service
class EventSourceService(
    private val repository: EventSourceRepository,
) {
    /**
     * Saves a webhook event payload with a unique fingerprint.
     * If an event with the same fingerprint already exists, it returns the existing event.
     *
     * @param payload The JSON payload of the webhook event.
     * @param provider The provider of the webhook event (e.g., "ifood").
     * @return The saved or existing WebhookEvent entity.
     */
    fun save(
        payload: String,
        provider: String,
    ): EventSource {
        val fingerprint = DigestUtils.sha256Hex(payload)

        val existing = repository.findByFingerprint(fingerprint)
        if (existing != null) return existing

        val entity =
            EventSource(
                provider = provider,
                payload = payload,
                status = EventSource.Status.PENDING,
                fingerprint = fingerprint,
            )

        return repository.save(entity)
    }
}
