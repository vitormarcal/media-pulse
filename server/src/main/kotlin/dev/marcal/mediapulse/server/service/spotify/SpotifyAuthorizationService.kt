package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.SpotifyAuthService
import dev.marcal.mediapulse.server.integration.spotify.SpotifyOAuthTokenService
import dev.marcal.mediapulse.server.repository.spotify.SpotifyCredentialsRepository
import dev.marcal.mediapulse.server.repository.spotify.SpotifySyncStateRepository
import org.springframework.stereotype.Service

@Service
class SpotifyAuthorizationService(
    private val tokenService: SpotifyOAuthTokenService,
    private val authService: SpotifyAuthService,
    private val credentialsRepository: SpotifyCredentialsRepository,
    private val syncStateRepository: SpotifySyncStateRepository,
) {
    suspend fun authorize(code: String): Boolean {
        val tokens = tokenService.exchangeCodeForTokens(code)
        val refreshToken = tokens.refresh_token?.takeIf { it.isNotBlank() } ?: return false

        credentialsRepository.saveRefreshToken(refreshToken)
        authService.acceptAuthorization(tokens.access_token, tokens.expires_in)
        syncStateRepository.markHealthy()
        return true
    }
}
