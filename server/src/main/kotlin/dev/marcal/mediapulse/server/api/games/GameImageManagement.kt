package dev.marcal.mediapulse.server.api.games

data class GameImageReferenceRequest(
    val url: String,
)

data class GameImageManagementResponse(
    val coverUrl: String?,
)
