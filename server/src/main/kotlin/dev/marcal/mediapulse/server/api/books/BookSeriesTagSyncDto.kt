package dev.marcal.mediapulse.server.api.books

data class BookSeriesTagSyncDto(
    val revision: Long,
    val pending: Boolean,
    val message: String?,
    val hasRemoteCovers: Boolean,
)
