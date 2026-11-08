package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieCollectionTagSyncDto
import dev.marcal.mediapulse.server.service.movie.MovieCollectionTagSyncService
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

class MovieCollectionTagSyncControllerTest {
    private val service = mockk<MovieCollectionTagSyncService>()
    private val mvc = MockMvcBuilders.standaloneSetup(MovieCollectionTagSyncController(service)).build()

    @Test
    fun `state exposes pending failures and retry returns queued state`() {
        every { service.state(1) } returns MovieCollectionTagSyncDto(4, true, "Tag limit", true)
        every { service.retry(1) } returns MovieCollectionTagSyncDto(4, true, "Tag limit", true)
        mvc
            .perform(
                get("/api/movies/1/collection-tags/sync"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.pending").value(true))
            .andExpect(jsonPath("$.message").value("Tag limit"))
        mvc.perform(post("/api/movies/1/collection-tags/sync")).andExpect(status().isOk).andExpect(jsonPath("$.revision").value(4))
    }

    @Test
    fun `missing movie and missing configuration have actionable problem detail`() {
        every { service.state(99) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "Filme não encontrado.")
        every { service.retry(1) } throws ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure o Visual Library.")
        mvc
            .perform(
                get("/api/movies/99/collection-tags/sync"),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.detail").value("Filme não encontrado."))
        mvc
            .perform(
                post("/api/movies/1/collection-tags/sync"),
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.detail").value("Configure o Visual Library."))
    }
}
