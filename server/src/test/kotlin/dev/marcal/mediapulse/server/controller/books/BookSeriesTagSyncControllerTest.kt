package dev.marcal.mediapulse.server.controller.books

import dev.marcal.mediapulse.server.api.books.BookSeriesTagSyncDto
import dev.marcal.mediapulse.server.service.books.BookSeriesTagSyncService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class BookSeriesTagSyncControllerTest {
    private val service = mockk<BookSeriesTagSyncService>()
    private val mvc = MockMvcBuilders.standaloneSetup(BookSeriesTagSyncController(service)).build()

    @Test
    fun `state exposes pending failures and retry returns queued state`() {
        every { service.state(1) } returns BookSeriesTagSyncDto(4, true, "Tag limit", true)
        every { service.retry(1) } returns BookSeriesTagSyncDto(4, true, "Tag limit", true)
        mvc
            .perform(
                get("/api/books/1/series-tags/sync"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.pending").value(true))
            .andExpect(jsonPath("$.message").value("Tag limit"))
        mvc.perform(post("/api/books/1/series-tags/sync")).andExpect(status().isOk).andExpect(jsonPath("$.revision").value(4))
    }

    @Test
    fun `missing book and missing configuration have actionable problem detail`() {
        every { service.state(99) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado.")
        every { service.retry(1) } throws ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure o Visual Library.")
        mvc
            .perform(
                get("/api/books/99/series-tags/sync"),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail").value("Livro não encontrado."))
        mvc
            .perform(
                post("/api/books/1/series-tags/sync"),
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.detail").value("Configure o Visual Library."))
    }
}
