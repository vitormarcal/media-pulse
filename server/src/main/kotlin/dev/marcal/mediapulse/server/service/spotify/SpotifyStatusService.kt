package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyStatusResponse
import dev.marcal.mediapulse.server.model.spotify.SpotifyAuthorizationStatus
import dev.marcal.mediapulse.server.repository.spotify.SpotifySyncStateRepository
import org.springframework.stereotype.Service

@Service
class SpotifyStatusService(
    private val props: SpotifyProperties,
    private val syncStateRepository: SpotifySyncStateRepository,
) {
    fun getStatus(): SpotifyStatusResponse {
        val state = syncStateRepository.getOrCreateSingleton()
        val requiresReauthorization = state.authorizationStatus == SpotifyAuthorizationStatus.REAUTHORIZATION_REQUIRED
        val reauthorizationAvailable = requiresReauthorization && props.oauth.enabled

        return SpotifyStatusResponse(
            enabled = props.enabled,
            status = state.authorizationStatus,
            lastSuccessAt = state.lastSuccessAt,
            lastFailureAt = state.lastFailureAt,
            message = messageFor(state.authorizationStatus),
            reauthorizationAvailable = reauthorizationAvailable,
            reauthorizationUrl = if (reauthorizationAvailable) "/oauth/spotify/login" else null,
        )
    }

    private fun messageFor(status: SpotifyAuthorizationStatus): String? =
        when (status) {
            SpotifyAuthorizationStatus.REAUTHORIZATION_REQUIRED ->
                "A autorização do Spotify expirou e precisa ser renovada."
            SpotifyAuthorizationStatus.ERROR ->
                "A última importação do Spotify falhou. Consulte os logs da aplicação."
            SpotifyAuthorizationStatus.UNKNOWN,
            SpotifyAuthorizationStatus.HEALTHY,
            -> null
        }
}
