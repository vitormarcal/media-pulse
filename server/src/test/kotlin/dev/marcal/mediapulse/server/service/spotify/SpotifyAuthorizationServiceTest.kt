package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.SpotifyAuthService
import dev.marcal.mediapulse.server.integration.spotify.SpotifyOAuthTokenService
import dev.marcal.mediapulse.server.repository.spotify.SpotifyCredentialsRepository
import dev.marcal.mediapulse.server.repository.spotify.SpotifySyncStateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpotifyAuthorizationServiceTest {
    private val tokenService = mockk<SpotifyOAuthTokenService>()
    private val authService = mockk<SpotifyAuthService>()
    private val credentialsRepository = mockk<SpotifyCredentialsRepository>()
    private val syncStateRepository = mockk<SpotifySyncStateRepository>()
    private val service =
        SpotifyAuthorizationService(tokenService, authService, credentialsRepository, syncStateRepository)

    @Test
    fun `should store refresh token and cache access token`() =
        runBlocking {
            coEvery { tokenService.exchangeCodeForTokens("code") } returns tokens(refreshToken = "refresh-token")
            everySuccessfulAuthorization()

            assertTrue(service.authorize("code"))

            verify { credentialsRepository.saveRefreshToken("refresh-token") }
            verify { authService.acceptAuthorization("access-token", 3600) }
            verify { syncStateRepository.markHealthy() }
        }

    @Test
    fun `should reject exchange without refresh token`() =
        runBlocking {
            coEvery { tokenService.exchangeCodeForTokens("code") } returns tokens(refreshToken = null)

            assertFalse(service.authorize("code"))

            verify(exactly = 0) { credentialsRepository.saveRefreshToken(any()) }
            verify(exactly = 0) { authService.acceptAuthorization(any(), any()) }
            verify(exactly = 0) { syncStateRepository.markHealthy() }
            coVerify(exactly = 1) { tokenService.exchangeCodeForTokens("code") }
        }

    private fun everySuccessfulAuthorization() {
        every { credentialsRepository.saveRefreshToken(any()) } just runs
        every { authService.acceptAuthorization(any(), any()) } just runs
        every { syncStateRepository.markHealthy() } returns mockk()
    }

    private fun tokens(refreshToken: String?) =
        SpotifyOAuthTokenService.SpotifyTokenExchangeResponse(
            access_token = "access-token",
            token_type = "Bearer",
            scope = "user-read-recently-played",
            expires_in = 3600,
            refresh_token = refreshToken,
        )
}
