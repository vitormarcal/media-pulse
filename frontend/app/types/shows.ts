import type { EditorialHighlight, EditorialShelfItem, ShowProgressDto } from '~/types/home'
import type { MediaCommentDto } from '~/types/comments'
import type { MediaRatingDto } from '~/types/ratings'

export interface ShowImageDto {
  id: number
  url: string
  isPrimary: boolean
}

export interface ShowWatchDto {
  watchId: number
  episodeId: number
  episodeTitle: string
  seasonNumber: number | null
  seasonTitle: string | null
  episodeNumber: number | null
  watchedAt: string
  source: string
}

export interface ShowSeasonDto {
  seasonNumber: number | null
  seasonTitle: string | null
  episodesCount: number
  watchedEpisodesCount: number
  completed: boolean
  lastWatchedAt: string | null
}

export interface ShowSeasonEpisodeDto {
  episodeId: number
  title: string
  episodeNumber: number | null
  summary: string | null
  durationMs: number | null
  originallyAvailableAt: string | null
  watchCount: number
  lastWatchedAt: string | null
  rating: MediaRatingDto | null
}

export interface ShowSeasonDetailsResponse {
  showId: number
  showSlug: string | null
  showTitle: string
  showOriginalTitle: string
  showYear: number | null
  showCoverUrl: string | null
  showTmdbId: string | null
  seasonNumber: number | null
  seasonTitle: string | null
  episodesCount: number
  watchedEpisodesCount: number
  completed: boolean
  lastWatchedAt: string | null
  episodes: ShowSeasonEpisodeDto[]
}

export interface ShowExternalIdDto {
  provider: string
  externalId: string
}

export interface ShowListPreviewShowDto {
  showId: number
  title: string
  slug: string | null
  coverUrl: string | null
}
export interface ShowListSummaryDto {
  listId: number
  name: string
  slug: string
  description: string | null
  itemCount: number
  coverShowId: number | null
  coverUrl: string | null
  previewShows: ShowListPreviewShowDto[]
}
export interface ShowListItemDto {
  showId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  episodesCount: number
  watchedEpisodesCount: number
}
export interface ShowListDetailsResponse {
  listId: number
  name: string
  slug: string
  description: string | null
  coverShowId: number | null
  coverUrl: string | null
  showCount: number
  watchedShowsCount: number
  shows: ShowListItemDto[]
}
export interface ShowListAttachRequest {
  listId?: number | null
  name?: string | null
  description?: string | null
}

export type ShowCreditType = 'CAST' | 'CREW'
export type ShowTermKind = 'GENRE' | 'TAG'
export type ShowTermSource = 'TMDB' | 'USER'
export type ShowEnrichmentStatus = 'PENDING' | 'RETRY_SCHEDULED' | 'COMPLETE' | 'BLOCKED'

export interface ShowTermDto {
  id: number
  name: string
  slug: string
  kind: ShowTermKind
  source: ShowTermSource
  hiddenGlobally: boolean
  hiddenForShow: boolean
  active: boolean
}

export interface ShowTermSuggestionDto {
  id: number
  name: string
  slug: string
  kind: ShowTermKind
  source: ShowTermSource
  hiddenGlobally: boolean
}

export interface ShowTermDetailsResponse {
  termId: number
  name: string
  slug: string
  kind: ShowTermKind
  source: ShowTermSource
  showCount: number
  watchedShowsCount: number
  shows: ShowLibraryCardDto[]
}

export interface ShowEnrichmentStepDto {
  status: ShowEnrichmentStatus
  lastAttemptAt: string | null
  retryAfter: string | null
}

export interface ShowPersonCreditDto {
  personId: number
  tmdbId: string
  name: string
  slug: string
  profileUrl: string | null
  creditType: ShowCreditType
  department: string | null
  job: string | null
  characterName: string | null
  billingOrder: number | null
}

export interface ShowDetailsResponse {
  showId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  description: string | null
  coverUrl: string | null
  images: ShowImageDto[]
  seasons: ShowSeasonDto[]
  progress: ShowProgressDto | null
  watches: ShowWatchDto[]
  externalIds: ShowExternalIdDto[]
  people: ShowPersonCreditDto[]
  terms: ShowTermDto[]
  lists: ShowListSummaryDto[]
  rating: MediaRatingDto | null
  comments: MediaCommentDto[]
  enrichment: { terms: ShowEnrichmentStepDto }
}

export interface ShowTermsSyncResponse {
  showId: number
  syncedCount: number
  visibleCount: number
}

export interface ShowCreditsSyncResponse {
  showId: number
  syncedCount: number
  visibleCount: number
}

export interface ExistingShowWatchCreateRequest {
  watchedAt: string
  episodeTitle: string
  seasonNumber?: number | null
  episodeNumber?: number | null
  originallyAvailableAt?: string | null
}

export interface ManualShowWatchCreateRequest extends ExistingShowWatchCreateRequest {
  showTitle: string
  year?: number | null
  tmdbId?: string | null
  tvdbId?: string | null
}

export interface ManualShowWatchCreateResponse {
  showId: number
  title: string
  year: number | null
  coverUrl: string | null
  episodeId: number
  episodeTitle: string
  seasonNumber: number | null
  episodeNumber: number | null
  watchedAt: string
  source: string
  createdShow: boolean
  createdEpisode: boolean
  watchInserted: boolean
  coverAssigned: boolean
  externalIds: ShowExternalIdDto[]
  lists: ShowListSummaryDto[]
}

export interface ManualShowCatalogCreateResponse {
  showId: number
  slug: string | null
  title: string
  year: number | null
  coverUrl: string | null
  createdShow: boolean
  coverAssigned: boolean
  seasonsImported: number
  episodesImported: number
  externalIds: ShowExternalIdDto[]
}

export interface ShowCatalogSuggestionsResponse {
  query: string
  suggestions: Array<{
    tmdbId: string
    title: string
    originalTitle: string | null
    year: number | null
    overview: string | null
    posterUrl: string | null
  }>
}

export type ShowMetadataEnrichmentField = 'TITLE' | 'YEAR' | 'DESCRIPTION' | 'TMDB_ID' | 'IMAGES'
export type ShowMetadataEnrichmentApplyMode = 'MISSING' | 'SELECTED'

export interface ShowMetadataEnrichmentPreviewResponse {
  showId: number
  resolvedTmdbId: string
  title: string
  fields: Array<{
    field: ShowMetadataEnrichmentField
    label: string
    currentValue: string | null
    suggestedValue: string | null
    available: boolean
    missing: boolean
    changed: boolean
    selectedByDefault: boolean
  }>
  images: {
    currentCoverUrl: string | null
    suggestedPosterUrl: string | null
    suggestedBackdropUrl: string | null
    candidates: Array<{
      key: string
      label: string
      imageUrl: string
      kind: string
      selectedByDefault: boolean
      suggestedAsPrimary: boolean
    }>
    available: boolean
    missing: boolean
    changed: boolean
    selectedByDefault: boolean
  }
}

export interface ShowMetadataEnrichmentApplyRequest {
  tmdbId: string | null
  mode: ShowMetadataEnrichmentApplyMode
  fields: ShowMetadataEnrichmentField[]
  imageSelection?: { selectedKeys: string[]; primaryKey: string | null } | null
}

export interface ShowMetadataEnrichmentApplyResponse {
  showId: number
  slug: string | null
  title: string
  appliedFields: ShowMetadataEnrichmentField[]
  coverAssigned: boolean
  externalIds: ShowExternalIdDto[]
}

export type ShowSeasonEnrichmentField =
  | 'SEASON_TITLE'
  | 'EPISODE_TITLE'
  | 'EPISODE_SUMMARY'
  | 'EPISODE_DURATION'
  | 'EPISODE_AIR_DATE'

export type ShowSeasonEnrichmentApplyMode = 'MISSING' | 'SELECTED'

export interface ShowSeasonEnrichmentPreviewRequest {
  tmdbId?: string | null
}

export interface ShowSeasonEnrichmentFieldPreview {
  field: ShowSeasonEnrichmentField
  label: string
  currentValue: string | null
  suggestedValue: string | null
  available: boolean
  missing: boolean
  changed: boolean
  selectedByDefault: boolean
}

export interface ShowSeasonEpisodeEnrichmentPreview {
  episodeId: number
  episodeNumber: number | null
  currentTitle: string
  suggestedTitle: string | null
  fields: ShowSeasonEnrichmentFieldPreview[]
}

export interface ShowSeasonEnrichmentPreviewResponse {
  showId: number
  seasonNumber: number
  resolvedTmdbId: string
  showTitle: string
  seasonTitle: string | null
  suggestedSeasonTitle: string | null
  seasonFields: ShowSeasonEnrichmentFieldPreview[]
  episodes: ShowSeasonEpisodeEnrichmentPreview[]
  changedEpisodesCount: number
  selectedFieldsCount: number
  missingTmdbEpisodesCount: number
}

export interface ShowSeasonEpisodeEnrichmentSelection {
  episodeId: number
  fields: ShowSeasonEnrichmentField[]
}

export interface ShowSeasonEnrichmentApplyRequest {
  tmdbId?: string | null
  mode: ShowSeasonEnrichmentApplyMode
  seasonFields: ShowSeasonEnrichmentField[]
  episodeFields: ShowSeasonEpisodeEnrichmentSelection[]
}

export interface ShowSeasonEnrichmentApplyResponse {
  showId: number
  seasonNumber: number
  resolvedTmdbId: string
  updatedEpisodesCount: number
  appliedFieldsCount: number
}

export interface ShowSeasonCardModel {
  id: string
  title: string
  progressLabel: string
  progressValue: number
  detail: string
  isComplete: boolean
  href: string | null
}

export interface ShowWatchEntryModel {
  id: string
  title: string
  context: string
  meta: string
  watchedAt: string
  relativeWatchedAt: string
  source: string
}

export interface ShowPageData {
  showId: number
  slug: string
  title: string
  originalTitle: string
  year: number | null
  description: string | null
  coverUrl: string | null
  gallery: string[]
  externalIds: ShowExternalIdDto[]
  progress: {
    watchedEpisodes: number
    totalEpisodes: number
    watchedSeasons: number
    totalSeasons: number
    completionPct: number
    statusText: string
  }
  rating: MediaRatingDto | null
  heroMeta: string[]
  enrichment: ShowDetailsResponse['enrichment']
  terms: {
    visibleCount: number
    hiddenCount: number
    groups: Array<{
      id: string
      title: string
      items: Array<{
        id: string
        termId: number
        name: string
        href: string
        kind: ShowTermKind
        source: ShowTermSource
        hiddenGlobally: boolean
        hiddenForShow: boolean
        active: boolean
        stateLabel: string
      }>
    }>
  }
  people: {
    summary: string
    visibleCount: number
    groups: Array<{
      id: string
      title: string
      items: Array<{
        id: string
        personId: number
        name: string
        href: string
        roleLabel: string
        profileUrl: string | null
      }>
    }>
  }
  seasons: ShowSeasonCardModel[]
  recentWatches: ShowWatchEntryModel[]
  comments: MediaCommentDto[]
}

export interface ShowSeasonEpisodeModel {
  id: string
  episodeId: number
  title: string
  episodeNumber: number | null
  context: string
  summary: string | null
  meta: string[]
  watchedLabel: string
  watched: boolean
  rating: MediaRatingDto | null
}

export interface ShowSeasonPageData {
  showId: number
  showSlug: string | null
  showTitle: string
  showOriginalTitle: string
  showYear: number | null
  showCoverUrl: string | null
  showTmdbId: string | null
  seasonTitle: string
  seasonNumber: number | null
  progress: {
    watchedEpisodes: number
    totalEpisodes: number
    completionPct: number
    statusText: string
    lastWatchedLabel: string
  }
  heroMeta: string[]
  episodes: ShowSeasonEpisodeModel[]
}

export interface ShowCollectionContextMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface ShowCollectionData {
  generatedAt: string
  hero: {
    title: string
    intro: string
    lead: EditorialHighlight | null
    supporting: EditorialHighlight[]
  }
  inProgress: EditorialShelfItem[]
  recentMoments: EditorialShelfItem[]
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: ShowCollectionContextMetric[]
  }
}

export interface ShowLibraryCardDto {
  showId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  watchedEpisodesCount: number
  episodesCount: number
  lastWatchedAt: string | null
}

export interface ShowsLibraryResponse {
  items: ShowLibraryCardDto[]
  nextCursor: string | null
}

export interface ShowsSearchResponse {
  shows: Array<{
    showId: number
    title: string
    originalTitle: string
    slug: string | null
    year: number | null
    coverUrl: string | null
    watchedAt: string | null
  }>
}

export interface ShowsStatsResponse {
  total: {
    watchesCount: number
    uniqueShowsCount: number
  }
  unwatchedCount: number
  years: Array<{
    year: number
    watchesCount: number
    uniqueShowsCount: number
    rewatchesCount: number
  }>
  latestWatchAt: string | null
  firstWatchAt: string | null
}

export interface ShowsByYearResponse {
  year: number
  range: {
    start: string
    end: string
  }
  stats: {
    watchesCount: number
    uniqueShowsCount: number
    rewatchesCount: number
  }
  watched: Array<{
    showId: number
    slug: string | null
    title: string
    originalTitle: string
    year: number | null
    coverUrl: string | null
    watchCountInYear: number
    firstWatchedAt: string
    lastWatchedAt: string
  }>
  unwatched: Array<{
    showId: number
    slug: string | null
    title: string
    originalTitle: string
    year: number | null
    coverUrl: string | null
  }>
}

export interface ShowLibraryMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface ShowLibraryYearChip {
  year: number
  label: string
  watches: string
}

export interface ShowLibraryCardModel {
  id: string
  title: string
  subtitle: string
  href: string | null
  imageUrl: string | null
  progressLabel: string
  progressValue: number
  activityLabel: string
  aside: string
  isDormant?: boolean
}

export interface ShowTermPageData {
  kind: ShowTermKind
  name: string
  slug: string
  heroMeta: string[]
  stats: {
    showCount: number
    watchedShowsCount: number
  }
  shows: ShowLibraryCardModel[]
}

export interface ShowLibraryPageData {
  hero: {
    title: string
    intro: string
    backLink: string
    backLabel: string
    accentLink: string
    accentLabel: string
    spotlight: {
      title: string
      subtitle: string
      imageUrl: string | null
      href: string | null
      meta: string
      note: string
    } | null
  }
  filters: {
    query: string
    selectedYear: number | null
    selectedUnwatched: boolean
    years: ShowLibraryYearChip[]
  }
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: ShowLibraryMetric[]
  }
  sections: Array<{
    id: string
    eyebrow: string
    title: string
    description: string
    summary: string
    items: ShowLibraryCardModel[]
    emptyMessage?: string
  }>
  libraryCursor: string | null
  mode: 'library' | 'search' | 'year'
}
