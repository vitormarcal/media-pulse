package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.frontend")
data class FrontendProperties(
    val staticPath: String = "",
)
