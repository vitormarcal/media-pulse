package dev.marcal.mediapulse.server.integration.spotify.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpotifyAlbumTracksResponse(
    @JsonProperty("items")
    val items: List<SpotifyAlbumTrackItem> = emptyList(),
    @JsonProperty("limit")
    val limit: Int? = null,
    @JsonProperty("offset")
    val offset: Int? = null,
    @JsonProperty("total")
    val total: Int? = null,
    @JsonProperty("next")
    val next: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpotifyAlbumTrackItem(
    @JsonProperty("id")
    val id: String? = null,
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("duration_ms")
    val durationMs: Int? = null,
    @JsonProperty("track_number")
    val trackNumber: Int? = null,
    // Spotify album tracks endpoint does not include disc_number; use item.discNumber if present, else default 1.
    @JsonProperty("disc_number")
    val discNumber: Int? = null,
    @JsonProperty("artists")
    val artists: List<SpotifySimpleArtist> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpotifySimpleArtist(
    @JsonProperty("id")
    val id: String? = null,
    @JsonProperty("name")
    val name: String? = null,
)
