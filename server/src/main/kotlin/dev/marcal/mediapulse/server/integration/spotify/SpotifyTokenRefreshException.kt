package dev.marcal.mediapulse.server.integration.spotify

open class SpotifyTokenRefreshException(
    val errorCode: String,
    val providerDescription: String?,
    message: String,
) : RuntimeException(message)

class SpotifyReauthorizationRequiredException(
    providerDescription: String? = null,
) : SpotifyTokenRefreshException(
        errorCode = "invalid_grant",
        providerDescription = providerDescription,
        message = "Spotify authorization expired or was revoked",
    )
