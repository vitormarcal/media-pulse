package dev.marcal.mediapulse.server.api.music

data class ArtistCoverageResponse(
    val artistId: Long,
    val artistName: String,
    val totalTracks: Long,
    val playedTracks: Long,
    val coveragePercent: Double,
)

data class AlbumCoverageResponse(
    val albumId: Long,
    val artistId: Long,
    val albumTitle: String,
    val artistName: String,
    val totalTracks: Long,
    val playedTracks: Long,
    val coveragePercent: Double,
)
