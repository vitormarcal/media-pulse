package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    PipelineProperties::class,
    PlexProperties::class,
    MusicBrainzProperties::class,
    SpotifyProperties::class,
    HardcoverProperties::class,
    HttpClientsProperties::class,
    TmdbProperties::class,
    FrontendProperties::class,
)
class PropertiesConfig
