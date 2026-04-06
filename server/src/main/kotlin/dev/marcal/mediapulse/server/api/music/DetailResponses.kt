package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class ArtistPageResponse(
    val artistId: Long,
    val artistName: String,
    val totalPlays: Long,
    val uniqueTracksPlayed: Long,
    val uniqueAlbumsPlayed: Long,
    val libraryAlbumsCount: Long,
    val libraryTracksCount: Long,
    val lastPlayed: Instant?,
    val albums: List<ArtistAlbumRow>,
    val topTracks: List<ArtistTrackRow>,
    val playsByDay: List<PlaysByDayRow>,
)

data class ArtistAlbumRow(
    val albumId: Long,
    val albumTitle: String,
    val year: Int?,
    val coverUrl: String?,
    val totalTracks: Long,
    val playedTracks: Long,
    val playCount: Long,
    val lastPlayed: Instant?,
)

data class ArtistTrackRow(
    val trackId: Long,
    val title: String,
    val albumId: Long?,
    val albumTitle: String?,
    val playCount: Long,
    val lastPlayed: Instant?,
)

data class TrackPageResponse(
    val trackId: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val totalPlays: Long,
    val lastPlayed: Instant?,
    val albums: List<TrackAlbumRow>,
    val recentPlays: List<TrackPlayRow>,
)

data class TrackAlbumRow(
    val albumId: Long,
    val albumTitle: String,
    val year: Int?,
    val coverUrl: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val playCount: Long,
    val lastPlayed: Instant?,
)

data class TrackPlayRow(
    val playedAt: Instant,
    val source: String,
    val albumId: Long,
    val albumTitle: String,
)
