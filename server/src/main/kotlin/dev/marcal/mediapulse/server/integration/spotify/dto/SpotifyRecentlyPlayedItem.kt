package dev.marcal.mediapulse.server.integration.spotify.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SpotifyRecentlyPlayedResponse(
    val items: List<SpotifyRecentlyPlayedItem> = emptyList(),
)

data class SpotifyRecentlyPlayedItem(
    @JsonProperty("played_at")
    val playedAt: String, // ISO-8601
    val track: SpotifyTrack?,
)

data class SpotifyTrack(
    val id: String?,
    val name: String?,
    @JsonProperty("duration_ms")
    val durationMs: Int?,
    @JsonProperty("track_number")
    val trackNumber: Int?,
    @JsonProperty("disc_number")
    val discNumber: Int?,
    val artists: List<SpotifyArtist>?,
    val album: SpotifyAlbum?,
)

data class SpotifyArtist(
    val id: String?,
    val name: String?,
)

data class SpotifyAlbum(
    val id: String?,
    val name: String?,
    @JsonProperty("release_date")
    val releaseDate: String?, // "YYYY-MM-DD" ou "YYYY"
    val images: List<SpotifyImage>?,
    val artists: List<SpotifyArtist>?,
)

data class SpotifyImage(
    val url: String,
    val width: Int?,
    val height: Int?,
)
