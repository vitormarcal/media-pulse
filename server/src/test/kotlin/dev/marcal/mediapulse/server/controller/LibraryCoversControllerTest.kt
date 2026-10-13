package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryUploadException
import dev.marcal.mediapulse.server.service.image.LibraryCoverService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class LibraryCoversControllerTest {
    private val covers = mockk<LibraryCoverService>()
    private val mvc = MockMvcBuilders.standaloneSetup(LibraryCoversController(covers)).build()

    @Test
    fun `multipart uploads return stable cover references and propagate failure status`() {
        val file = MockMultipartFile("cover", "cover.png", "image/png", byteArrayOf(1))
        every { covers.book(7, any()) } returns "/api/image-assets/book/content"
        every { covers.game(8, any()) } returns "/covers/manual/games/8/new.png"
        mvc
            .perform(multipart("/api/books/7/cover").file(file))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coverUrl").value("/api/image-assets/book/content"))
        mvc
            .perform(multipart("/api/games/8/cover").file(file))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.coverUrl").value("/covers/manual/games/8/new.png"))
        every { covers.book(7, any()) } throws VisualLibraryUploadException(IllegalStateException("offline"))
        mvc.perform(multipart("/api/books/7/cover").file(file)).andExpect(status().isServiceUnavailable)
        every { covers.game(404, any()) } throws ResponseStatusException(HttpStatus.NOT_FOUND)
        mvc.perform(multipart("/api/games/404/cover").file(file)).andExpect(status().isNotFound)
        mvc.perform(multipart("/api/books/7/cover")).andExpect(status().isBadRequest)
    }
}
