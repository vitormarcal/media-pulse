package dev.marcal.mediapulse.server.controller.spotify.dto

data class SpotifyImportRequest(
    val resetCursor: Boolean = false,
    val maxPages: Int? = null,
)

data class SpotifyImportResponse(
    val resetCursor: Boolean,
)
