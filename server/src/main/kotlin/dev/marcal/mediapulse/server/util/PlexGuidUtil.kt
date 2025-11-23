package dev.marcal.mediapulse.server.util

import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid

object PlexGuidUtil {
    fun firstValue(
        guids: List<PlexGuid>?,
        scheme: String,
    ): String? =
        guids
            .orEmpty()
            .firstOrNull { it.id.startsWith("$scheme://") }
            ?.id
            ?.substringAfter("$scheme://")
}
