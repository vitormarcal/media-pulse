package dev.marcal.mediapulse.server.controller.oauth

import dev.marcal.mediapulse.server.config.SpotifyProperties
import dev.marcal.mediapulse.server.service.spotify.SpotifyAuthorizationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals

class SpotifyOAuthControllerTest {
    private val authorizationService = mockk<SpotifyAuthorizationService>()
    private val controller =
        SpotifyOAuthController(
            SpotifyProperties(
                clientId = "client-id",
                clientSecret = "client-secret",
                oauth = SpotifyProperties.OAuth(enabled = true),
            ),
            authorizationService,
        )

    @Test
    fun `should redirect to home after successful callback`() =
        runBlocking {
            val state = loginState()
            coEvery { authorizationService.authorize("code") } returns true

            val response = controller.callback(code = "code", state = state, error = null)

            assertEquals(HttpStatus.FOUND, response.statusCode)
            assertEquals("/", response.headers.location.toString())
            coVerify(exactly = 1) { authorizationService.authorize("code") }
        }

    @Test
    fun `should reject callback without an initiated login`() =
        runBlocking {
            val response = controller.callback(code = "code", state = "unknown", error = null)

            assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
            coVerify(exactly = 0) { authorizationService.authorize(any()) }
        }

    private fun loginState(): String {
        val location = controller.login().headers.getFirst(HttpHeaders.LOCATION)!!
        return URI(location)
            .rawQuery
            .split("&")
            .first { it.startsWith("state=") }
            .substringAfter("state=")
            .let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
    }
}
