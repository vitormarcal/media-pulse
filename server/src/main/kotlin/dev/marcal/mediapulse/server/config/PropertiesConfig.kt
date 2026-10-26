package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
    OwnerAuthProperties::class,
    CorsProperties::class,
    PipelineProperties::class,
    PlexProperties::class,
    MusicBrainzProperties::class,
    SpotifyProperties::class,
    HardcoverProperties::class,
    HttpClientsProperties::class,
    TmdbProperties::class,
    IgdbProperties::class,
    SteamGridDbProperties::class,
    FrontendProperties::class,
    VisualLibraryProperties::class,
)
class PropertiesConfig
