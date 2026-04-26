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

enum class MovieTermKindDto {
    GENRE,
    TAG,
}

enum class MovieTermSourceDto {
    TMDB,
    USER,
}

data class MovieTermDto(
    val id: Long,
    val name: String,
    val slug: String,
    val kind: MovieTermKindDto,
    val source: MovieTermSourceDto,
    val hiddenGlobally: Boolean,
    val hiddenForMovie: Boolean,
    val active: Boolean,
)

data class MovieCollectionDto(
    val id: Long,
    val tmdbId: String,
    val name: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val movies: List<MovieCollectionMovieDto>,
)

data class MovieCollectionMovieDto(
    val movieId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String?,
    val year: Int?,
    val coverUrl: String?,
    val watched: Boolean,
    val current: Boolean,
)

enum class MovieEnrichmentField {
    TITLE,
    YEAR,
    DESCRIPTION,
    TMDB_ID,
    IMDB_ID,
    IMAGES,
}

enum class MovieEnrichmentApplyMode {
    MISSING,
    SELECTED,
}

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
    val terms: List<MovieTermDto> = emptyList(),
    val collection: MovieCollectionDto? = null,
)

data class MovieTermCreateRequest(
    val name: String,
    val kind: MovieTermKindDto,
)

data class MovieTermVisibilityRequest(
    val hidden: Boolean,
)

data class MovieTermsSyncResponse(
    val movieId: Long,
    val syncedCount: Int,
    val visibleCount: Int,
)

data class MovieTermsBatchSyncResponse(
    val requestedLimit: Int,
    val candidates: Int,
    val processed: Int,
    val synced: Int,
    val failed: Int,
)

data class MovieTermDetailsResponse(
    val termId: Long,
    val name: String,
    val slug: String,
    val kind: MovieTermKindDto,
    val source: MovieTermSourceDto,
    val movieCount: Long,
    val watchedMoviesCount: Long,
    val movies: List<MovieLibraryCardDto>,
)

data class ManualMovieCatalogCreateRequest(
    val title: String,
    val year: Int? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
)

data class MovieCatalogSuggestionDto(
    val tmdbId: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
)

data class MovieCatalogSuggestionsResponse(
    val query: String,
    val suggestions: List<MovieCatalogSuggestionDto>,
)

data class ManualMovieCatalogCreateResponse(
    val movieId: Long,
    val slug: String?,
    val title: String,
    val year: Int?,
    val coverUrl: String?,
    val createdMovie: Boolean,
    val coverAssigned: Boolean,
    val externalIds: List<ManualMovieExternalIdView>,
)

data class MovieEnrichmentPreviewRequest(
    val tmdbId: String? = null,
)

data class MovieEnrichmentFieldPreview(
    val field: MovieEnrichmentField,
    val label: String,
    val currentValue: String?,
    val suggestedValue: String?,
    val available: Boolean,
    val missing: Boolean,
    val changed: Boolean,
    val selectedByDefault: Boolean,
)

data class MovieEnrichmentImagePreview(
    val currentCoverUrl: String?,
    val suggestedPosterUrl: String?,
    val suggestedBackdropUrl: String?,
    val candidates: List<MovieEnrichmentImageCandidatePreview>,
    val available: Boolean,
    val missing: Boolean,
    val changed: Boolean,
    val selectedByDefault: Boolean,
)

data class MovieEnrichmentImageCandidatePreview(
    val key: String,
    val label: String,
    val imageUrl: String,
    val kind: String,
    val selectedByDefault: Boolean,
    val suggestedAsPrimary: Boolean,
)

data class MovieEnrichmentPreviewResponse(
    val movieId: Long,
    val resolvedTmdbId: String,
    val title: String,
    val fields: List<MovieEnrichmentFieldPreview>,
    val images: MovieEnrichmentImagePreview,
)

data class MovieEnrichmentApplyRequest(
    val tmdbId: String? = null,
    val mode: MovieEnrichmentApplyMode = MovieEnrichmentApplyMode.MISSING,
    val fields: List<MovieEnrichmentField> = emptyList(),
    val imageSelection: MovieEnrichmentImageSelectionRequest? = null,
)

data class MovieEnrichmentImageSelectionRequest(
    val selectedKeys: List<String> = emptyList(),
    val primaryKey: String? = null,
)

data class MovieEnrichmentApplyResponse(
    val movieId: Long,
    val slug: String?,
    val title: String,
    val appliedFields: List<MovieEnrichmentField>,
    val coverAssigned: Boolean,
    val externalIds: List<ManualMovieExternalIdView>,
)

data class MovieCollectionBackfillResponse(
    val requestedLimit: Int,
    val candidates: Int,
    val processed: Int,
    val linked: Int,
    val withoutCollection: Int,
    val failed: Int,
)

data class MovieCollectionMembersResponse(
    val collectionId: Long,
    val tmdbId: String,
    val name: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val members: List<MovieCollectionMemberDto>,
)

data class MovieCollectionMemberDto(
    val tmdbId: String,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val tmdbUrl: String,
    val localMovieId: Long?,
    val localSlug: String?,
    val inCatalog: Boolean,
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

data class ExistingMovieWatchCreateRequest(
    val watchedAt: Instant,
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
