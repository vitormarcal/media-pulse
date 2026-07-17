package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class AlbumListCreateRequest(
    val name: String,
    val description: String? = null,
)

data class AlbumListUpdateRequest(
    val name: String,
    val description: String? = null,
)

data class AlbumListOrderRequest(
    val albumIds: List<Long>,
)

data class AlbumListListenedRequest(
    val listened: Boolean,
)

data class AlbumListSummaryDto(
    val listId: Long,
    val name: String,
    val slug: String,
    val description: String?,
    val itemCount: Int,
    val listenedCount: Int,
    val coverUrls: List<String>,
    val updatedAt: Instant,
)

data class AlbumListItemDto(
    val albumId: Long,
    val albumTitle: String,
    val artistId: Long,
    val artistName: String,
    val year: Int?,
    val coverUrl: String?,
    val position: Int,
    val listenedAt: Instant?,
    val rating: Int?,
)

data class AlbumListDetailsResponse(
    val listId: Long,
    val name: String,
    val slug: String,
    val description: String?,
    val itemCount: Int,
    val listenedCount: Int,
    val items: List<AlbumListItemDto>,
)
