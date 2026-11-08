package dev.marcal.mediapulse.server.api.movies

data class MovieImageReferenceRequest(
    val url: String,
)

data class MovieImageManagementResponse(
    val coverUrl: String?,
)
