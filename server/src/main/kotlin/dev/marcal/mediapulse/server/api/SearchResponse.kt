package dev.marcal.mediapulse.server.api

data class SearchResponse(
    val artists: List<IdName>,
    val albums: List<SearchAlbumRow>,
    val tracks: List<SearchTrackRow>,
)

data class IdName(
    val id: Long,
    val name: String,
)

data class SearchAlbumRow(
    val id: Long,
    val title: String,
    val artistName: String,
    val year: Int?,
)

data class SearchTrackRow(
    val id: Long,
    val title: String,
    val artistName: String,
    val albumTitle: String,
)
