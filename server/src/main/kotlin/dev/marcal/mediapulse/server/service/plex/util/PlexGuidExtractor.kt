package dev.marcal.mediapulse.server.service.plex.util

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload

object PlexGuidExtractor {
    fun extractGuids(meta: PlexWebhookPayload.PlexMetadata): Map<String, String> =
        meta.guidList
            .mapNotNull { guid ->
                val parts = guid.id?.split("://")
                if (parts?.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }.toMap()
}
