package dev.marcal.mediapulse.server.controller.webhook.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class PlexWebhookPayload(
    val event: String,
    @JsonProperty("Metadata") val metadata: PlexMetadata,
) {
    data class PlexMetadata(
        val librarySectionType: String,
        val ratingKey: String,
        val type: String,
        val title: String,
        val grandparentTitle: String,
        val parentTitle: String,
        val parentYear: Int,
        val summary: String,
        val viewCount: Int = 0,
        val lastViewedAt: Instant = Instant.now(),
        @JsonProperty("Image") val image: List<PlexImageMetadata> = emptyList(),
        @JsonProperty("Guid") val guid: List<PlexGuidMetadata> = emptyList(),
    ) {
        data class PlexImageMetadata(
            val alt: String,
            val type: String,
            val url: String,
        )

        data class PlexGuidMetadata(
            val id: String,
        )
    }
}
