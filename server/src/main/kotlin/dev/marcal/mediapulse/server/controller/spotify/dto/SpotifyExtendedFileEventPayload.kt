package dev.marcal.mediapulse.server.controller.spotify.dto

data class SpotifyExtendedFileEventPayload(
    val path: String,
    val sha256: String,
    val originalName: String? = null,
    val compressed: Boolean = true,
    val parserVersion: Int = 1,
)
