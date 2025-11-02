package dev.marcal.mediapulse.server.api

// Tops
data class TopArtistResponse(
    val artistId: Long,
    val artistName: String,
    val playCount: Long,
)

data class TopAlbumResponse(
    val albumId: Long,
    val albumTitle: String,
    val artistId: Long,
    val artistName: String,
    val playCount: Long,
)

data class TopTrackResponse(
    val trackId: Long,
    val title: String,
    val albumId: Long,
    val albumTitle: String,
    val artistId: Long,
    val artistName: String,
    val playCount: Long,
)
