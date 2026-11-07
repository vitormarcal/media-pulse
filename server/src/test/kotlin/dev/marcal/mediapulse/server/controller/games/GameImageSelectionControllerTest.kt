package dev.marcal.mediapulse.server.controller.games

import dev.marcal.mediapulse.server.api.games.GameImageCandidatesResponse
import dev.marcal.mediapulse.server.api.games.GameImageExistence
import dev.marcal.mediapulse.server.api.games.GameImageLookupResponse
import dev.marcal.mediapulse.server.api.games.GameImageLookupResult
import dev.marcal.mediapulse.server.api.games.GameImageSelectionRequest
import dev.marcal.mediapulse.server.api.games.GameImageSelectionResponse
import dev.marcal.mediapulse.server.service.game.GameImageSelectionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class GameImageSelectionControllerTest {
    private val service = mockk<GameImageSelectionService>()
    private val mvc = MockMvcBuilders.standaloneSetup(GameImageSelectionController(service)).build()

    @Test
    fun `selection endpoints expose candidates and individual results`() {
        every { service.candidates(7) } returns GameImageCandidatesResponse(emptyList(), listOf("IGDB indisponível"))
        every { service.save(7, GameImageSelectionRequest(listOf("id"))) } returns GameImageSelectionResponse(listOf("id"), emptyList())
        mvc
            .perform(get("/api/games/7/images/candidates"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.warnings[0]").value("IGDB indisponível"))
        mvc
            .perform(post("/api/games/7/images/selection").contentType(MediaType.APPLICATION_JSON).content("""{"ids":["id"]}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.savedIds[0]").value("id"))
        mvc
            .perform(post("/api/games/7/images/selection").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `lookup exposes individual states without saving a selection`() {
        every { service.lookup(7, GameImageSelectionRequest(listOf("id"))) } returns
            GameImageLookupResponse(true, listOf(GameImageLookupResult("id", GameImageExistence.IN_LIBRARY)))
        mvc
            .perform(post("/api/games/7/images/lookup").contentType(MediaType.APPLICATION_JSON).content("""{"ids":["id"]}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.results[0].state").value("IN_LIBRARY"))
    }
}
