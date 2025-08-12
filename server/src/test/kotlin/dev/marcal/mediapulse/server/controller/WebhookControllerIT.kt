package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.MediapulseServerApplicationTests
import dev.marcal.mediapulse.server.fixture.PlexEventsFixture
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class WebhookControllerIT : MediapulseServerApplicationTests() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Nested
    inner class WebhookControllerTests {
        @Test
        fun `should return OK when webhook multipart is received`() {
            val payloadJson = PlexEventsFixture.musicEventsJson.first()

            val payloadPart =
                MockMultipartFile(
                    "payload",
                    "payload",
                    MediaType.APPLICATION_JSON_VALUE,
                    payloadJson.toByteArray(),
                )

            mockMvc
                .perform(
                    multipart("/webhook/plex")
                        .file(payloadPart)
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                ).andExpect(status().isOk)

            val list = eventSourceCrudRepository.findAll()
            assert(list.isNotEmpty()) { "Webhook payload should be saved in the repository" }
            assert(list.size == 1) { "Only one payload should be saved" }
        }
    }

    @Nested
    inner class WebhookControllerErrorTests {
        @Test
        fun `should return BadRequest when payload is missing`() {
            mockMvc
                .perform(
                    multipart("/webhook/plex")
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return BadRequest when payload is empty`() {
            val payloadPart =
                MockMultipartFile(
                    "payload",
                    "payload",
                    MediaType.APPLICATION_JSON_VALUE,
                    "".toByteArray(),
                )

            mockMvc
                .perform(
                    multipart("/webhook/plex")
                        .file(payloadPart)
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                ).andExpect(status().isBadRequest)
        }

        @Test
        fun `should return UnsupportedMediaType when content-type is incorrect`() {
            val payloadJson = PlexEventsFixture.musicEventsJson.first()
            mockMvc
                .perform(
                    multipart("/webhook/plex")
                        .file(
                            MockMultipartFile(
                                "payload",
                                "payload",
                                MediaType.APPLICATION_JSON_VALUE,
                                payloadJson.toByteArray(),
                            ),
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(status().isUnsupportedMediaType)
        }

        @Test
        fun `should return NotFound for unknown endpoint`() {
            val payloadJson = PlexEventsFixture.musicEventsJson.first()
            val payloadPart =
                MockMultipartFile(
                    "payload",
                    "payload",
                    MediaType.APPLICATION_JSON_VALUE,
                    payloadJson.toByteArray(),
                )

            mockMvc
                .perform(
                    multipart("/webhook/unknown")
                        .file(payloadPart)
                        .contentType(MediaType.MULTIPART_FORM_DATA),
                ).andExpect(status().isNotFound)
        }
    }
}
