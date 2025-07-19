package dev.marcal.mediapulse.server.service

import dev.marcal.mediapulse.server.model.WebhookPayload
import dev.marcal.mediapulse.server.repository.WebhookPayloadRepository
import org.springframework.stereotype.Service

@Service
class WebhookPayloadService(
    private val repository: WebhookPayloadRepository,
) {
    fun save(
        provider: String,
        payload: String,
    ) {
        val entity = WebhookPayload(provider = provider, payload = payload)
        repository.save(entity)
    }
}
