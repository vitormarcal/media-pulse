package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.controller.dto.WebhookDTO
import dev.marcal.mediapulse.server.service.WebhookEventService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RequestMapping("/webhook")
@RestController
class WebhookController(
    private val webhookEventService: WebhookEventService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    @PostMapping("/plex")
    fun webhook(
        @RequestBody payload: String,
        @RequestPart("thumb", required = false) thumb: MultipartFile?,
    ) {
        logger.info("Webhook received for provider: plex. Thumb present: ${thumb != null}. Payload: $payload")
        webhookEventService.save(
            WebhookDTO(
                provider = "plex",
                payload = payload,
                file = thumb,
            ),
        )
    }
}
