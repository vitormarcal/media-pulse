package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.service.WebhookPayloadService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/webhook")
@RestController
class WebhookController(
    private val webhookPayloadService: WebhookPayloadService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    @PostMapping("/{provider}")
    fun webhook(
        @PathVariable provider: String,
        @RequestBody payload: String,
    ) {
        logger.info("Webhook received for provider: $provider")
        webhookPayloadService.save(provider, payload)
    }
}
