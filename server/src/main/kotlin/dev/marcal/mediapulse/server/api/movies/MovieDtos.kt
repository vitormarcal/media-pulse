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

data class MoviesRecentResponse(
    val items: List<MovieCardDto>,
    val nextCursor: String?,
)

data class MoviesSummaryResponse(
    val range: RangeDto,
    val watchesCount: Long,
    val uniqueMoviesCount: Long,
)

data class MoviesByYearResponse(
    val year: Int,
    val range: RangeDto,
    val stats: MoviesByYearStatsDto,
    val watched: List<MovieYearWatchedDto>,
    val unwatched: List<MovieYearUnwatchedDto>,
)

data class MoviesByYearStatsDto(
    val watchesCount: Long,
    val uniqueMoviesCount: Long,
    val rewatchesCount: Long,
)

data class MoviesStatsResponse(
    val total: MoviesTotalStatsDto,
    val unwatchedCount: Long,
    val years: List<MoviesYearStatsDto>,
    val latestWatchAt: Instant?,
    val firstWatchAt: Instant?,
)

data class MoviesTotalStatsDto(
    val watchesCount: Long,
    val uniqueMoviesCount: Long,
)

data class MoviesYearStatsDto(
    val year: Int,
    val watchesCount: Long,
    val uniqueMoviesCount: Long,
    val rewatchesCount: Long,
)

data class MovieYearWatchedDto(
    val movieId: Long,
    val slug: String?,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val coverUrl: String?,
    val watchCountInYear: Long,
    val firstWatchedAt: Instant,
    val lastWatchedAt: Instant,
)

data class MovieYearUnwatchedDto(
    val movieId: Long,
    val slug: String?,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val coverUrl: String?,
)

data class RangeDto(
    val start: Instant,
    val end: Instant,
)

data class ManualMovieWatchCreateRequest(
    val watchedAt: Instant,
    val title: String,
    val year: Int? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
)

data class ManualMovieWatchCreateResponse(
    val movieId: Long,
    val title: String,
    val year: Int?,
    val coverUrl: String?,
    val watchedAt: Instant,
    val source: String,
    val createdMovie: Boolean,
    val watchInserted: Boolean,
    val coverAssigned: Boolean,
    val externalIds: List<ManualMovieExternalIdView>,
)

data class ManualMovieExternalIdView(
    val provider: String,
    val externalId: String,
)
