package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.igdb")
data class IgdbProperties(
    val enabled: Boolean = true,
    val apiBaseUrl: String = "https://api.igdb.com/v4",
    val oauthBaseUrl: String = "https://id.twitch.tv/oauth2",
    val clientId: String = "",
    val clientSecret: String = "",
)
