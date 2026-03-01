package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.tmdb")
data class TmdbProperties(
    val enabled: Boolean = true,
    val apiBaseUrl: String = "https://api.themoviedb.org/3",
    val imageBaseUrl: String = "https://image.tmdb.org",
    val token: String = "",
    val apiKey: String = "",
)
