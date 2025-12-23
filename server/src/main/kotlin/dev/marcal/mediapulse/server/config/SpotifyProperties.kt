package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.spotify")
data class SpotifyProperties(
    val enabled: Boolean = false,
    val apiBaseUrl: String = "https://api.spotify.com/v1",
    val accountsBaseUrl: String = "https://accounts.spotify.com",
    val clientId: String,
    val clientSecret: String,
    val refreshToken: String,
    val import: Import = Import(),
    val oauth: OAuth = OAuth(),
) {
    data class Import(
        val enabled: Boolean = true,
        val pageSize: Int = 50,
    )

    data class OAuth(
        val enabled: Boolean = true,
        val redirectUri: String = "http://localhost:8080/oauth/spotify/callback",
        val scopes: String = "user-read-recently-played",
    )
}
