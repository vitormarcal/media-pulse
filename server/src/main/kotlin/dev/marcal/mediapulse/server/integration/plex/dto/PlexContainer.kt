package dev.marcal.mediapulse.server.integration.plex.dto

import com.fasterxml.jackson.annotation.JsonProperty

// Envelope padrão Plex
data class PlexContainer<T>(
    @JsonProperty("MediaContainer") val mc: MediaContainer<T>
) {
    data class MediaContainer<T>(
        val size: Int? = null,
        val totalSize: Int? = null,
        val Directory: List<T>? = null,
        val Metadata: List<T>? = null
    )
}

data class PlexLibrarySection(
    val key: String,
    val type: String,            // "artist", "movie", etc.
    val title: String? = null
)

data class PlexGuid(val id: String)

// Artista (type=8)
data class PlexArtist(
    val ratingKey: String,       // id interno do Plex p/ queries
    val title: String,           // nome do artista
    @JsonProperty("Guid") val guids: List<PlexGuid>? = emptyList()
)

// Álbum (type=9)
data class PlexAlbum(
    val ratingKey: String,
    val parentRatingKey: String? = null, // artista “pai” no Plex
    val title: String,                   // álbum
    val parentTitle: String? = null,     // artista (algumas respostas vêm assim)
    val year: Int? = null,
    val thumb: String? = null,
    @JsonProperty("Guid") val guids: List<PlexGuid>? = emptyList()
)
