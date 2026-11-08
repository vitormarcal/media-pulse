package dev.marcal.mediapulse.server.controller.games

import dev.marcal.mediapulse.server.api.games.GameImageManagementResponse
import dev.marcal.mediapulse.server.config.OwnerAccessFilter
import dev.marcal.mediapulse.server.config.OwnerAuthProperties
import dev.marcal.mediapulse.server.service.auth.OwnerAuthService
import dev.marcal.mediapulse.server.service.game.GameImageManagementService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class GameImageManagementControllerTest {
    private val service = mockk<GameImageManagementService>()
    private val auth = mockk<OwnerAuthService>()
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(GameImageManagementController(service))
            .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(OwnerAccessFilter(auth, OwnerAuthProperties()))
            .build()
    private val session =
        MockHttpSession().apply {
            setAttribute("ownerGeneration", 1L)
            setAttribute("csrf", "token")
        }

    init {
        every { auth.generation() } returns 1L
    }

    @Test
    fun `owner can choose existing cover and remove final image with nullable response`() {
        every { service.update(7, "saved", false) } returns GameImageManagementResponse("saved")
        every { service.update(7, "saved", true) } returns GameImageManagementResponse(null)
        mvc
            .perform(
                post("/api/games/7/images/primary")
                    .servletPath("/api/games/7/images/primary")
                    .session(session)
                    .header("X-CSRF-Token", "token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"saved"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.coverUrl").value("saved"))
        mvc
            .perform(
                delete("/api/games/7/images")
                    .servletPath("/api/games/7/images")
                    .session(session)
                    .header("X-CSRF-Token", "token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"url":"saved"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.coverUrl").isEmpty)
    }

    @Test
    fun `both mutations require owner session and csrf`() {
        for (request in listOf(post("/api/games/7/images/primary"), delete("/api/games/7/images"))) {
            mvc
                .perform(request.contentType(MediaType.APPLICATION_JSON).content("""{"url":"saved"}"""))
                .andExpect(status().isUnauthorized)
            mvc.perform(request.session(session)).andExpect(status().isForbidden)
        }
        verify(exactly = 0) { service.update(any(), any(), any()) }
    }

    @Test
    fun `missing reference is bad request`() {
        mvc
            .perform(
                post("/api/games/7/images/primary")
                    .session(session)
                    .header("X-CSRF-Token", "token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isBadRequest)
        verify(exactly = 0) { service.update(any(), any(), any()) }
    }
}
