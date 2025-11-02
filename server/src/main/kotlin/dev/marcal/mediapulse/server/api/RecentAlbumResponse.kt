package dev.marcal.mediapulse.server.api

import java.time.Instant

data class RecentAlbumResponse(
    val albumId: Long,
    val albumTitle: String,
    val artistId: Long,
    val artistName: String,
    val year: Int?,
    val coverUrl: String?,
    val lastPlayed: Instant,
    val playCount: Long,
)
