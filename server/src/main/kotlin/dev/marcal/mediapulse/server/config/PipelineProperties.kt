package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.pipeline")
data class PipelineProperties(
    val enabled: Boolean = true,
    val runOnStartup: Boolean = false,
)
