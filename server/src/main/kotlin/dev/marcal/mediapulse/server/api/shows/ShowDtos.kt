package dev.marcal.mediapulse.server.api.shows

import java.time.Instant
import java.time.LocalDate

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
    val seasonTitle: String? = null,
    val episodeNumber: Int?,
    val watchedAt: Instant,
    val source: String,
)

data class ShowSeasonDto(
    val seasonNumber: Int?,
    val seasonTitle: String? = null,
    val episodesCount: Long,
    val watchedEpisodesCount: Long,
    val completed: Boolean,
    val lastWatchedAt: Instant? = null,
)

data class ShowProgressDto(
    val episodesCount: Long,
    val watchedEpisodesCount: Long,
    val seasonsCount: Long,
    val completedSeasonsCount: Long,
    val completed: Boolean,
    val inProgress: Boolean,
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
    val seasons: List<ShowSeasonDto> = emptyList(),
    val progress: ShowProgressDto? = null,
    val watches: List<ShowWatchDto>,
    val externalIds: List<ShowExternalIdDto>,
)

data class ShowsSearchResponse(
    val shows: List<ShowCardDto>,
)

data class CurrentlyWatchingShowDto(
    val showId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String? = null,
    val year: Int?,
    val coverUrl: String?,
    val lastWatchedAt: Instant,
    val progress: ShowProgressDto,
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

data class ManualShowWatchCreateRequest(
    val watchedAt: Instant,
    val showTitle: String,
    val episodeTitle: String,
    val year: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val tmdbId: String? = null,
    val tvdbId: String? = null,
    val originallyAvailableAt: LocalDate? = null,
)

data class ManualShowWatchCreateResponse(
    val showId: Long,
    val title: String,
    val year: Int?,
    val coverUrl: String?,
    val episodeId: Long,
    val episodeTitle: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val watchedAt: Instant,
    val source: String,
    val createdShow: Boolean,
    val createdEpisode: Boolean,
    val watchInserted: Boolean,
    val coverAssigned: Boolean,
    val externalIds: List<ManualShowExternalIdView>,
)

data class ManualShowExternalIdView(
    val provider: String,
    val externalId: String,
)
