package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class ArtistLibraryRow(
    val artistId: Long,
    val artistName: String,
    val coverUrl: String?,
    val totalPlays: Long,
    val albumsCount: Long,
    val tracksCount: Long,
    val lastPlayed: Instant?,
)

data class ArtistLibraryPageResponse(
    val items: List<ArtistLibraryRow>,
    val nextCursor: String?,
)

data class AlbumLibraryRow(
    val albumId: Long,
    val albumTitle: String,
    val artistId: Long,
    val artistName: String,
    val coverUrl: String?,
    val year: Int?,
    val totalTracks: Long,
    val playedTracks: Long,
    val playCount: Long,
    val lastPlayed: Instant?,
)

data class AlbumLibraryPageResponse(
    val items: List<AlbumLibraryRow>,
    val nextCursor: String?,
)

data class TrackLibraryRow(
    val trackId: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val albumId: Long?,
    val albumTitle: String?,
    val coverUrl: String?,
    val totalPlays: Long,
    val lastPlayed: Instant?,
)

data class TrackLibraryPageResponse(
    val items: List<TrackLibraryRow>,
    val nextCursor: String?,
)
