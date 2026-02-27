package dev.marcal.mediapulse.server.api.movies

import java.time.Instant

data class MovieCardDto(
    val movieId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String? = null,
    val year: Int?,
    val coverUrl: String?,
    val watchedAt: Instant?,
)

data class MovieImageDto(
    val id: Long,
    val url: String,
    val isPrimary: Boolean,
)

data class MovieWatchDto(
    val watchId: Long,
    val watchedAt: Instant,
    val source: String,
)

data class MovieExternalIdDto(
    val provider: String,
    val externalId: String,
)

data class MovieDetailsResponse(
    val movieId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String? = null,
    val year: Int?,
    val description: String?,
    val coverUrl: String?,
    val images: List<MovieImageDto>,
    val watches: List<MovieWatchDto>,
    val externalIds: List<MovieExternalIdDto>,
)

data class MoviesSearchResponse(
    val movies: List<MovieCardDto>,
)

data class MoviesSummaryResponse(
    val range: RangeDto,
    val watchesCount: Long,
    val uniqueMoviesCount: Long,
)

data class RangeDto(
    val start: Instant,
    val end: Instant,
)
