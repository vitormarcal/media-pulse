package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.musicbrainz")
data class MusicBrainzProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://musicbrainz.org",
    val userAgent: String = "media-pulse/1.0 (contact: you@example.com)",
    val enrich: Enrich = Enrich(),
) {
    data class Enrich(
        val batchSize: Int = 200,
        val onlyMissingAlbumGenres: Boolean = true,
        val maxTags: Int = 10,
        val minRequestIntervalMs: Long = 1100, // 1 req/seg
    )
}
