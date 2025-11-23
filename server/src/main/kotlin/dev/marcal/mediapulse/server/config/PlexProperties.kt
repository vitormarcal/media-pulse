package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("media-pulse.plex")
data class PlexProperties(
    val baseUrl: String,
    val token: String,
    val import: ImportProperties = ImportProperties(),
) {
    data class ImportProperties(
        val enabled: Boolean = true,
        val pageSize: Int = 200,
        val scheduleCron: String = "0 0 4 * * *",
        val runOnStartup: Boolean = true,
    )
}
