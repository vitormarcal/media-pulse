package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class TrendingGenreResponse(
    val genre: String,
    val playCountNow: Long,
    val playCountPrev: Long,
    val delta: Long,
)

data class RecentGenreResponse(
    val genre: String,
    val lastPlayed: Instant,
    val playCountInWindow: Long,
)

data class UnderplayedGenreResponse(
    val genre: String,
    val libraryAlbums: Long,
    val playCount: Long,
    val lastPlayed: Instant?,
)

data class TopGenreBySourceResponse(
    val source: String, // PLEX | MUSICBRAINZ | LASTFM
    val genres: List<TopGenreResponse>,
)
