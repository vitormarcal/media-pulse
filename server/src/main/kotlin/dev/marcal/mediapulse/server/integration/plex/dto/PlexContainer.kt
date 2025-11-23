package dev.marcal.mediapulse.server.integration.plex.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class PlexContainer<T>(
    @JsonProperty("MediaContainer") val mc: MediaContainer<T>,
) {
    data class MediaContainer<T>(
        val size: Int? = null,
        val totalSize: Int? = null,
        val Directory: List<T>? = null,
        val Metadata: List<T>? = null,
    )
}

data class PlexLibrarySection(
    val key: String,
    val type: String, // "artist", "movie", etc.
    val title: String? = null,
)

data class PlexGuid(
    val id: String,
)

data class PlexArtist(
    val ratingKey: String,
    val title: String,
    @JsonProperty("Guid") val guids: List<PlexGuid>? = emptyList(),
)

data class PlexAlbum(
    val ratingKey: String,
    val parentRatingKey: String? = null,
    val title: String, // álbum
    val parentTitle: String? = null,
    val year: Int? = null,
    val thumb: String? = null,
    @JsonProperty("Guid") val guids: List<PlexGuid>? = emptyList(),
)
