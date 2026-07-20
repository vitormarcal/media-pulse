package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import dev.marcal.mediapulse.server.model.spotify.SpotifyAuthorizationStatus
import dev.marcal.mediapulse.server.model.spotify.SpotifySyncState
import dev.marcal.mediapulse.server.repository.spotify.SpotifySyncStateRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyStatusServiceTest {
    private val repository = mockk<SpotifySyncStateRepository>()

    @Test
    fun `should expose reauthorization action only when oauth is enabled`() {
        every { repository.getOrCreateSingleton() } returns
            SpotifySyncState(authorizationStatus = SpotifyAuthorizationStatus.REAUTHORIZATION_REQUIRED)
        val props =
            SpotifyProperties(
                clientId = "id",
                clientSecret = "secret",
                refreshToken = "token",
                oauth = SpotifyProperties.OAuth(enabled = true),
            )

        val response = SpotifyStatusService(props, repository).getStatus()

        assertTrue(response.reauthorizationAvailable)
        assertEquals("/oauth/spotify/login", response.reauthorizationUrl)
        assertEquals(SpotifyAuthorizationStatus.REAUTHORIZATION_REQUIRED, response.status)
    }

    @Test
    fun `should not expose provider details for generic error`() {
        every { repository.getOrCreateSingleton() } returns
            SpotifySyncState(authorizationStatus = SpotifyAuthorizationStatus.ERROR, lastErrorCode = "invalid_client")
        val props = SpotifyProperties(clientId = "id", clientSecret = "secret", refreshToken = "token")

        val response = SpotifyStatusService(props, repository).getStatus()

        assertEquals("A última importação do Spotify falhou. Consulte os logs da aplicação.", response.message)
        assertNull(response.reauthorizationUrl)
    }
}
