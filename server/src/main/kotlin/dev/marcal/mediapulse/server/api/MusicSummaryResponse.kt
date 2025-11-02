package dev.marcal.mediapulse.server.api

import java.time.Instant

data class MusicSummaryResponse(
    val range: RangeDto,
    val artistsCount: Long,
    val albumsCount: Long,
    val tracksCount: Long,
)

data class RangeDto(
    val start: Instant,
    val end: Instant,
)
