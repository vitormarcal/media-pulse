package dev.marcal.mediapulse.server.integration.spotify.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SpotifyExtendedHistoryItem(
    // timestamp, usually "2020-01-01T12:34:56Z"
    @JsonProperty("ts")
    val ts: String? = null,
    @JsonProperty("ms_played")
    val msPlayed: Long? = null,
    @JsonProperty("master_metadata_track_name")
    val trackName: String? = null,
    @JsonProperty("master_metadata_album_artist_name")
    val artistName: String? = null,
    @JsonProperty("master_metadata_album_album_name")
    val albumName: String? = null,
    // e.g. "spotify:track:<id>"
    @JsonProperty("spotify_track_uri")
    val spotifyTrackUri: String? = null,
    // optional
    @JsonProperty("skipped")
    val skipped: Boolean? = null,
)
