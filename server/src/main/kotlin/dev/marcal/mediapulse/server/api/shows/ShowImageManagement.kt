package dev.marcal.mediapulse.server.api.shows

data class ShowImageReferenceRequest(
    val url: String,
)

data class ShowImageManagementResponse(
    val coverUrl: String?,
)
