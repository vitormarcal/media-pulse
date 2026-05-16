package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.steamgriddb")
data class SteamGridDbProperties(
    val enabled: Boolean = true,
    val apiBaseUrl: String = "https://www.steamgriddb.com/api/v2",
    val apiKey: String = "",
)
