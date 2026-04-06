package dev.marcal.mediapulse.server.api.movies

import java.time.Instant

data class MovieLibraryCardDto(
    val movieId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String? = null,
    val year: Int?,
    val coverUrl: String?,
    val watchCount: Long,
    val lastWatchedAt: Instant?,
)

data class MoviesLibraryResponse(
    val items: List<MovieLibraryCardDto>,
    val nextCursor: String?,
)
