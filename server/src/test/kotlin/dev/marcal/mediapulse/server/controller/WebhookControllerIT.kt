package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.MediapulseServerApplicationTests
import dev.marcal.mediapulse.server.repository.WebhookEventRepository
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class WebhookControllerIT : MediapulseServerApplicationTests() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var webhookEventRepository: WebhookEventRepository

    @Nested
    inner class WebhookControllerTests {
        @Test
        fun `should return OK when webhook is received`() {
            val provider = "plex"
            val payload = """{"key":"value"}"""

            mockMvc
                .perform(
                    post("/webhook/$provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload),
                ).andExpect(status().isOk)

            val list = webhookEventRepository.findAll()
            assert(list.isNotEmpty()) { "Webhook payload should be saved in the repository" }
            assert(list.size == 1) { "Only one payload should be saved" }
        }
    }
}
