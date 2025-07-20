package dev.marcal.mediapulse.server.service

import dev.marcal.mediapulse.server.controller.dto.WebhookDTO
import dev.marcal.mediapulse.server.model.WebhookPayload
import dev.marcal.mediapulse.server.repository.WebhookPayloadRepository
import org.springframework.stereotype.Service

@Service
class WebhookPayloadService(
    private val repository: WebhookPayloadRepository,
) {
    fun save(webhookDTO: WebhookDTO) {
        val entity = WebhookPayload(provider = webhookDTO.provider, payload = webhookDTO.payload)
        repository.save(entity)
    }
}
