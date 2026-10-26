package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("media-pulse")
data class CorsProperties(
    val allowedOrigin: List<String> = listOf("http://localhost:3000", "http://localhost:8080"),
) {
    init {
        require(allowedOrigin.none { it == "*" }) { "Session CORS requires explicit allowed origins" }
    }
}
