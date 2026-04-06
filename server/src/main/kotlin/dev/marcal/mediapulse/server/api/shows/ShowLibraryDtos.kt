package dev.marcal.mediapulse.server.api.shows

import java.time.Instant

data class ShowLibraryCardDto(
    val showId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String? = null,
    val year: Int?,
    val coverUrl: String?,
    val watchedEpisodesCount: Long,
    val episodesCount: Long,
    val lastWatchedAt: Instant?,
)

data class ShowsLibraryResponse(
    val items: List<ShowLibraryCardDto>,
    val nextCursor: String?,
)
