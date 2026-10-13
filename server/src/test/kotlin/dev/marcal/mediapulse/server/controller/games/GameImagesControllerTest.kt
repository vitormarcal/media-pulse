package dev.marcal.mediapulse.server.controller.games

import dev.marcal.mediapulse.server.api.games.GameImagesImportResponse
import dev.marcal.mediapulse.server.service.game.ManualGameCatalogService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class GameImagesControllerTest {
    private val catalog = mockk<ManualGameCatalogService>()
    private val mvc = MockMvcBuilders.standaloneSetup(GamesController(mockk(), mockk(), catalog)).build()

    @Test
    fun `image import returns counts and propagates failure statuses`() {
        every { catalog.importImages(7) } returns GameImagesImportResponse(2, false, 1)
        mvc
            .perform(post("/api/games/7/images/import"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.savedImagesCount").value(2))
            .andExpect(jsonPath("$.coverAssigned").value(false))
            .andExpect(jsonPath("$.failedImagesCount").value(1))
        for (code in listOf(HttpStatus.NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE)) {
            every { catalog.importImages(7) } throws ResponseStatusException(code)
            mvc.perform(post("/api/games/7/images/import")).andExpect(status().`is`(code.value()))
        }
    }
}
