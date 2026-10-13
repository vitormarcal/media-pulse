package dev.marcal.mediapulse.server.api.games

data class GameImagesImportResponse(
    val savedImagesCount: Int = 0,
    val coverAssigned: Boolean = false,
    val failedImagesCount: Int = 0,
)
