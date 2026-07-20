package dev.marcal.mediapulse.server.controller.spotify.dto

import dev.marcal.mediapulse.server.model.spotify.SpotifyAuthorizationStatus
import java.time.Instant

data class SpotifyStatusResponse(
    val enabled: Boolean,
    val status: SpotifyAuthorizationStatus,
    val lastSuccessAt: Instant?,
    val lastFailureAt: Instant?,
    val message: String?,
    val reauthorizationAvailable: Boolean,
    val reauthorizationUrl: String?,
)
