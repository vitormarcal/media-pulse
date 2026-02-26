package dev.marcal.mediapulse.server.controller.webhook.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class PlexWebhookPayload(
    val event: String,
    @JsonProperty("Metadata") val metadata: PlexMetadata,
    // Os demais campos (Account/Server/Player) são opcionais no seu serviço, então omitimos
) {
    data class PlexMetadata(
        val librarySectionType: String? = null,
        val ratingKey: String? = null,
        val type: String, // "track", "episode" etc. (obrigatório p/ sua lógica)
        val title: String, // título da faixa (obrigatório p/ sua lógica)
        val originalTitle: String? = null,
        val grandparentTitle: String? = null, // artista
        val parentTitle: String? = null, // álbum
        val parentGuid: String? = null,
        val guid: String? = null,
        val grandparentGuid: String? = null,
        val thumb: String? = null,
        val parentThumb: String? = null,
        val parentIndex: Int? = null,
        val index: Int? = null,
        val parentYear: Int? = null,
        val year: Int? = null,
        // cuidado: no seu fixture vem como string ISO-8601; deixe opcional.
        val lastViewedAt: Instant? = null,
        val summary: String? = null,
        val viewCount: Int? = null,
        @JsonProperty("Image")
        val image: List<PlexImageMetadata> = emptyList(),
        @JsonProperty("Guid")
        val guidList: List<PlexGuidMetadata> = emptyList(),
    ) {
        data class PlexImageMetadata(
            val alt: String? = null,
            val type: String? = null,
            val url: String? = null,
        )

        data class PlexGuidMetadata(
            val id: String? = null,
        )
    }
}
