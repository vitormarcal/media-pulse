package dev.marcal.mediapulse.server.service

import dev.marcal.mediapulse.server.controller.dto.WebhookDTO
import dev.marcal.mediapulse.server.model.WebhookEvent
import dev.marcal.mediapulse.server.repository.WebhookEventRepository
import org.springframework.stereotype.Service

@Service
class WebhookEventService(
    private val repository: WebhookEventRepository,
) {
    fun save(webhookDTO: WebhookDTO) {
        val entity = WebhookEvent(provider = webhookDTO.provider, payload = webhookDTO.payload)
        repository.save(entity)
    }
}
