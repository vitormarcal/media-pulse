package dev.marcal.mediapulse.server.controller.webhook

import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RequestMapping("/webhook")
@RestController
class WebhookController(
    private val eventSourceService: EventSourceService,
    private val processEventSourceService: ProcessEventSourceService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    @PostMapping("/plex", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun webhook(
        @RequestPart("payload") payload: String,
        @RequestPart("thumb", required = false) thumb: MultipartFile?,
    ) {
        logger.info("Webhook received for provider: plex. Thumb present: ${thumb != null}. Payload: $payload")
        val eventId =
            eventSourceService
                .save(
                    provider = "plex",
                    payload = payload,
                ).id

        processEventSourceService.executeAsync(eventId)
    }
}
