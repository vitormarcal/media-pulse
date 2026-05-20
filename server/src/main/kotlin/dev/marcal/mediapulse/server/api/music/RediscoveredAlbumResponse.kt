package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class RediscoveredAlbumResponse(
    val albumId: Long,
    val albumTitle: String,
    val artistId: Long,
    val artistName: String,
    val year: Int?,
    val coverUrl: String?,
    val historicalPlayCount: Long,
    val recentPlayCount: Long,
    val lastHistoricalPlay: Instant,
    val firstRecentPlay: Instant,
    val latestPlay: Instant,
    val quietGapDays: Long,
)
