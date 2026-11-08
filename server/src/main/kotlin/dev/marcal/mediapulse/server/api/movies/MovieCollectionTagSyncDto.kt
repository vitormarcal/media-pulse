package dev.marcal.mediapulse.server.api.movies

data class MovieCollectionTagSyncDto(
    val revision: Long,
    val pending: Boolean,
    val message: String?,
    val hasRemoteImages: Boolean,
)
