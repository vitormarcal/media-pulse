package dev.marcal.mediapulse.server.api.shows

import java.time.Instant

data class ShowCardDto(
    val showId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String? = null,
    val year: Int?,
    val coverUrl: String?,
    val watchedAt: Instant?,
)

data class ShowImageDto(
    val id: Long,
    val url: String,
    val isPrimary: Boolean,
)

data class ShowWatchDto(
    val watchId: Long,
    val episodeId: Long,
    val episodeTitle: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val watchedAt: Instant,
    val source: String,
)

data class ShowExternalIdDto(
    val provider: String,
    val externalId: String,
)

data class ShowDetailsResponse(
    val showId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String? = null,
    val year: Int?,
    val description: String?,
    val coverUrl: String?,
    val images: List<ShowImageDto>,
    val watches: List<ShowWatchDto>,
    val externalIds: List<ShowExternalIdDto>,
)

data class ShowsSearchResponse(
    val shows: List<ShowCardDto>,
)

data class ShowsSummaryResponse(
    val range: RangeDto,
    val watchesCount: Long,
    val uniqueShowsCount: Long,
)

data class ShowsByYearResponse(
    val year: Int,
    val range: RangeDto,
    val stats: ShowsByYearStatsDto,
    val watched: List<ShowYearWatchedDto>,
    val unwatched: List<ShowYearUnwatchedDto>,
)

data class ShowsByYearStatsDto(
    val watchesCount: Long,
    val uniqueShowsCount: Long,
    val rewatchesCount: Long,
)

data class ShowsStatsResponse(
    val total: ShowsTotalStatsDto,
    val unwatchedCount: Long,
    val years: List<ShowsYearStatsDto>,
    val latestWatchAt: Instant?,
    val firstWatchAt: Instant?,
)

data class ShowsTotalStatsDto(
    val watchesCount: Long,
    val uniqueShowsCount: Long,
)

data class ShowsYearStatsDto(
    val year: Int,
    val watchesCount: Long,
    val uniqueShowsCount: Long,
    val rewatchesCount: Long,
)

data class ShowYearWatchedDto(
    val showId: Long,
    val slug: String?,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val coverUrl: String?,
    val watchCountInYear: Long,
    val firstWatchedAt: Instant,
    val lastWatchedAt: Instant,
)

data class ShowYearUnwatchedDto(
    val showId: Long,
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
