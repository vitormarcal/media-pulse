package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PlexProperties::class)
class PlexConfig
