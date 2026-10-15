package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillBatch
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillPreviewRequest
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.service.music.AlbumCreditBackfillService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class AlbumCreditBackfillControllerTest {
    @Test
    fun `administrative preview returns a reviewable batch and apply is a separate request`() {
        val id = UUID.randomUUID()
        val batch = AlbumCreditBackfillBatch(id, Instant.parse("2026-01-01T00:00:00Z"), emptyList())
        val service = mockk<AlbumCreditBackfillService>()
        coEvery { service.preview(AlbumCreditBackfillPreviewRequest(listOf(7), 1)) } returns batch
        every { service.get(id) } returns batch
        every { service.apply(id) } returns batch
        every { service.undo(id) } returns batch
        every { service.recent() } returns listOf(batch)
        val mvc =
            MockMvcBuilders
                .standaloneSetup(AlbumCreditBackfillController(service))
                .setMessageConverters(MappingJackson2HttpMessageConverter(JacksonConfig().objectMapper()))
                .build()
        val pending =
            mvc
                .perform(
                    post("/api/admin/music/album-artist-backfills/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"albumIds":[7],"limit":1}"""),
                ).andExpect(request().asyncStarted())
                .andReturn()
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk).andExpect(jsonPath("$.batchId").value(id.toString()))
        verify(exactly = 0) { service.apply(any()) }
        mvc.perform(get("/api/admin/music/album-artist-backfills/$id")).andExpect(status().isOk)
        mvc
            .perform(
                get("/api/admin/music/album-artist-backfills"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].batchId").value(id.toString()))
        mvc.perform(post("/api/admin/music/album-artist-backfills/$id/apply")).andExpect(status().isOk)
        mvc.perform(post("/api/admin/music/album-artist-backfills/$id/undo")).andExpect(status().isOk)
        verify(exactly = 1) { service.apply(id) }
        verify(exactly = 1) { service.undo(id) }
    }
}
