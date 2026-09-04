package dev.marcal.mediapulse.server.api.shows

import dev.marcal.mediapulse.server.api.comments.MediaCommentDto
import dev.marcal.mediapulse.server.api.ratings.MediaRatingDto
import java.time.Instant
import java.time.LocalDate

enum class ShowCreditTypeDto {
    CAST,
    CREW,
}

enum class ShowTermKindDto { GENRE, TAG }

enum class ShowTermSourceDto { TMDB, USER }

enum class ShowEnrichmentStatus { PENDING, RETRY_SCHEDULED, COMPLETE, BLOCKED }

enum class ShowMetadataEnrichmentField { TITLE, YEAR, DESCRIPTION, TMDB_ID, IMAGES }

enum class ShowMetadataEnrichmentApplyMode { MISSING, SELECTED }

data class ShowTermDto(
    val id: Long,
    val name: String,
    val slug: String,
    val kind: ShowTermKindDto,
    val source: ShowTermSourceDto,
    val hiddenGlobally: Boolean,
    val hiddenForShow: Boolean,
    val active: Boolean,
)

data class ShowTermSuggestionDto(
    val id: Long,
    val name: String,
    val slug: String,
    val kind: ShowTermKindDto,
    val source: ShowTermSourceDto,
    val hiddenGlobally: Boolean,
)

data class ShowEnrichmentStepDto(
    val status: ShowEnrichmentStatus,
    val lastAttemptAt: Instant? = null,
    val retryAfter: Instant? = null,
)

data class ShowAutomaticEnrichmentDto(
    val terms: ShowEnrichmentStepDto,
)

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

data class ShowSeasonEpisodeDto(
    val episodeId: Long,
    val title: String,
    val episodeNumber: Int?,
    val summary: String?,
    val durationMs: Int?,
    val originallyAvailableAt: LocalDate?,
    val watchCount: Long,
    val lastWatchedAt: Instant?,
    val rating: MediaRatingDto? = null,
)

data class ShowSeasonDetailsResponse(
    val showId: Long,
    val showSlug: String?,
    val showTitle: String,
    val showOriginalTitle: String,
    val showYear: Int?,
    val showCoverUrl: String?,
    val showTmdbId: String?,
    val seasonNumber: Int?,
    val seasonTitle: String?,
    val episodesCount: Long,
    val watchedEpisodesCount: Long,
    val completed: Boolean,
    val lastWatchedAt: Instant?,
    val episodes: List<ShowSeasonEpisodeDto>,
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

data class ShowPersonCreditDto(
    val personId: Long,
    val tmdbId: String,
    val name: String,
    val slug: String,
    val profileUrl: String?,
    val creditType: ShowCreditTypeDto,
    val department: String?,
    val job: String?,
    val characterName: String?,
    val billingOrder: Int?,
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
    val people: List<ShowPersonCreditDto> = emptyList(),
    val terms: List<ShowTermDto> = emptyList(),
    val rating: MediaRatingDto? = null,
    val comments: List<MediaCommentDto> = emptyList(),
    val enrichment: ShowAutomaticEnrichmentDto =
        ShowAutomaticEnrichmentDto(ShowEnrichmentStepDto(ShowEnrichmentStatus.BLOCKED)),
)

data class ShowTermCreateRequest(
    val name: String,
    val kind: ShowTermKindDto,
)

data class ShowTermVisibilityRequest(
    val hidden: Boolean,
)

data class ShowTermsSyncResponse(
    val showId: Long,
    val syncedCount: Int,
    val visibleCount: Int,
)

data class ShowTermsBatchSyncResponse(
    val requestedLimit: Int,
    val candidates: Int,
    val processed: Int,
    val synced: Int,
    val failed: Int,
)

data class ShowMetadataEnrichmentPreviewRequest(
    val tmdbId: String? = null,
)

data class ShowMetadataEnrichmentFieldPreview(
    val field: ShowMetadataEnrichmentField,
    val label: String,
    val currentValue: String?,
    val suggestedValue: String?,
    val available: Boolean,
    val missing: Boolean,
    val changed: Boolean,
    val selectedByDefault: Boolean,
)

data class ShowMetadataEnrichmentImageCandidatePreview(
    val key: String,
    val label: String,
    val imageUrl: String,
    val kind: String,
    val selectedByDefault: Boolean,
    val suggestedAsPrimary: Boolean,
)

data class ShowMetadataEnrichmentImagePreview(
    val currentCoverUrl: String?,
    val suggestedPosterUrl: String?,
    val suggestedBackdropUrl: String?,
    val candidates: List<ShowMetadataEnrichmentImageCandidatePreview>,
    val available: Boolean,
    val missing: Boolean,
    val changed: Boolean,
    val selectedByDefault: Boolean,
)

data class ShowMetadataEnrichmentPreviewResponse(
    val showId: Long,
    val resolvedTmdbId: String,
    val title: String,
    val fields: List<ShowMetadataEnrichmentFieldPreview>,
    val images: ShowMetadataEnrichmentImagePreview,
)

data class ShowMetadataEnrichmentImageSelectionRequest(
    val selectedKeys: List<String> = emptyList(),
    val primaryKey: String? = null,
)

data class ShowMetadataEnrichmentApplyRequest(
    val tmdbId: String? = null,
    val mode: ShowMetadataEnrichmentApplyMode = ShowMetadataEnrichmentApplyMode.MISSING,
    val fields: List<ShowMetadataEnrichmentField> = emptyList(),
    val imageSelection: ShowMetadataEnrichmentImageSelectionRequest? = null,
)

data class ShowMetadataEnrichmentApplyResponse(
    val showId: Long,
    val slug: String?,
    val title: String,
    val appliedFields: List<ShowMetadataEnrichmentField>,
    val coverAssigned: Boolean,
    val externalIds: List<ShowExternalIdDto>,
)

data class ShowCreditsSyncResponse(
    val showId: Long,
    val syncedCount: Int,
    val visibleCount: Int,
)

data class ShowCreditsBatchSyncResponse(
    val requestedLimit: Int,
    val candidates: Int,
    val processed: Int,
    val synced: Int,
    val failed: Int,
)

data class ShowsSearchResponse(
    val shows: List<ShowCardDto>,
)

data class ShowsRecentResponse(
    val items: List<ShowCardDto>,
    val nextCursor: String?,
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

data class ExistingShowWatchCreateRequest(
    val watchedAt: Instant,
    val episodeTitle: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val originallyAvailableAt: LocalDate? = null,
)

data class ManualShowCatalogCreateRequest(
    val title: String,
    val year: Int? = null,
    val tmdbId: String? = null,
    val tvdbId: String? = null,
    val importEpisodes: Boolean = true,
)

data class ShowCatalogSuggestionDto(
    val tmdbId: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
)

data class ShowCatalogSuggestionsResponse(
    val query: String,
    val suggestions: List<ShowCatalogSuggestionDto>,
)

data class ManualShowCatalogCreateResponse(
    val showId: Long,
    val slug: String?,
    val title: String,
    val year: Int?,
    val coverUrl: String?,
    val createdShow: Boolean,
    val coverAssigned: Boolean,
    val seasonsImported: Int,
    val episodesImported: Int,
    val externalIds: List<ManualShowExternalIdView>,
)

enum class ShowSeasonEnrichmentField {
    SEASON_TITLE,
    EPISODE_TITLE,
    EPISODE_SUMMARY,
    EPISODE_DURATION,
    EPISODE_AIR_DATE,
}

enum class ShowSeasonEnrichmentApplyMode {
    MISSING,
    SELECTED,
}

data class ShowSeasonEnrichmentPreviewRequest(
    val tmdbId: String? = null,
)

data class ShowSeasonEnrichmentFieldPreview(
    val field: ShowSeasonEnrichmentField,
    val label: String,
    val currentValue: String?,
    val suggestedValue: String?,
    val available: Boolean,
    val missing: Boolean,
    val changed: Boolean,
    val selectedByDefault: Boolean,
)

data class ShowSeasonEpisodeEnrichmentPreview(
    val episodeId: Long,
    val episodeNumber: Int?,
    val currentTitle: String,
    val suggestedTitle: String?,
    val fields: List<ShowSeasonEnrichmentFieldPreview>,
)

data class ShowSeasonEnrichmentPreviewResponse(
    val showId: Long,
    val seasonNumber: Int,
    val resolvedTmdbId: String,
    val showTitle: String,
    val seasonTitle: String?,
    val suggestedSeasonTitle: String?,
    val seasonFields: List<ShowSeasonEnrichmentFieldPreview>,
    val episodes: List<ShowSeasonEpisodeEnrichmentPreview>,
    val changedEpisodesCount: Int,
    val selectedFieldsCount: Int,
    val missingTmdbEpisodesCount: Int,
)

data class ShowSeasonEpisodeEnrichmentSelection(
    val episodeId: Long,
    val fields: List<ShowSeasonEnrichmentField> = emptyList(),
)

data class ShowSeasonEnrichmentApplyRequest(
    val tmdbId: String? = null,
    val mode: ShowSeasonEnrichmentApplyMode = ShowSeasonEnrichmentApplyMode.MISSING,
    val seasonFields: List<ShowSeasonEnrichmentField> = emptyList(),
    val episodeFields: List<ShowSeasonEpisodeEnrichmentSelection> = emptyList(),
)

data class ShowSeasonEnrichmentApplyResponse(
    val showId: Long,
    val seasonNumber: Int,
    val resolvedTmdbId: String,
    val updatedEpisodesCount: Int,
    val appliedFieldsCount: Int,
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
