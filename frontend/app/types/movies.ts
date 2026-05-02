import type { EditorialHighlight, EditorialShelfItem } from '~/types/home'

export interface MovieImageDto {
  id: number
  url: string
  isPrimary: boolean
}

export interface MovieWatchDto {
  watchId: number
  watchedAt: string
  source: string
}

export interface MovieExternalIdDto {
  provider: string
  externalId: string
}

export type MovieTermKind = 'GENRE' | 'TAG'
export type MovieTermSource = 'TMDB' | 'USER'
export type MovieCreditType = 'CAST' | 'CREW'
export type MovieCompanyType = 'PRODUCTION'

export interface MovieTermDto {
  id: number
  name: string
  slug: string
  kind: MovieTermKind
  source: MovieTermSource
  hiddenGlobally: boolean
  hiddenForMovie: boolean
  active: boolean
}

export interface MovieTermSuggestionDto {
  id: number
  name: string
  slug: string
  kind: MovieTermKind
  source: MovieTermSource
  hiddenGlobally: boolean
}

export interface MovieTermDetailsResponse {
  termId: number
  name: string
  slug: string
  kind: MovieTermKind
  source: MovieTermSource
  movieCount: number
  watchedMoviesCount: number
  movies: MovieLibraryCardDto[]
}

export interface MovieCollectionDto {
  id: number
  tmdbId: string
  name: string
  posterUrl: string | null
  backdropUrl: string | null
  movies: MovieCollectionMovieDto[]
}

export interface MovieCollectionMovieDto {
  movieId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  watched: boolean
  current: boolean
}

export interface MovieCompanyDto {
  companyId: number
  tmdbId: string
  name: string
  slug: string
  logoUrl: string | null
  originCountry: string | null
  companyType: MovieCompanyType
}

export interface MovieListSummaryDto {
  listId: number
  name: string
  slug: string
  description: string | null
  coverMovieId: number | null
  coverUrl: string | null
  itemCount: number
  previewMovies: Array<{
    movieId: number
    title: string
    slug: string | null
    coverUrl: string | null
  }>
}

export interface MoviePersonCreditDto {
  personId: number
  tmdbId: string
  name: string
  slug: string
  profileUrl: string | null
  creditType: MovieCreditType
  department: string | null
  job: string | null
  characterName: string | null
  billingOrder: number | null
}

export interface MoviePersonSuggestionDto {
  personId: number
  tmdbId: string
  name: string
  slug: string
  profileUrl: string | null
  roles: string[]
}

export interface MoviePersonLinkRequest {
  personId: number
  group: string
  roleLabel: string | null
}

export interface MovieTmdbCreditCandidate {
  personTmdbId: string
  name: string
  profileUrl: string | null
  creditType: MovieCreditType
  department: string | null
  job: string | null
  characterName: string | null
  billingOrder: number | null
  roleLabel: string
}

export interface MovieTmdbCreditCandidateGroup {
  id: string
  title: string
  items: MovieTmdbCreditCandidate[]
}

export interface MovieTmdbCreditCandidatesResponse {
  movieId: number
  reconciledCount: number
  candidateCount: number
  groups: MovieTmdbCreditCandidateGroup[]
}

export interface MovieTmdbCreditImportRequest {
  personTmdbId: string
  creditType: MovieCreditType
  department: string | null
  job: string | null
  characterName: string | null
  billingOrder: number | null
}

export interface MoviePersonDetailsResponse {
  personId: number
  tmdbId: string
  name: string
  slug: string
  profileUrl: string | null
  roles: string[]
  movieCount: number
  watchedMoviesCount: number
  movies: MovieLibraryCardDto[]
}

export interface MovieCompanyDetailsResponse {
  companyId: number
  tmdbId: string
  name: string
  slug: string
  logoUrl: string | null
  originCountry: string | null
  companyType: MovieCompanyType
  movieCount: number
  watchedMoviesCount: number
  movies: MovieLibraryCardDto[]
}

export interface MovieListDetailsResponse {
  listId: number
  name: string
  slug: string
  description: string | null
  coverMovieId: number | null
  coverUrl: string | null
  movieCount: number
  watchedMoviesCount: number
  movies: MovieLibraryCardDto[]
}

export interface MovieCreditsSyncResponse {
  movieId: number
  syncedCount: number
  visibleCount: number
}

export interface MovieCreditsBatchSyncResponse {
  requestedLimit: number
  candidates: number
  processed: number
  synced: number
  failed: number
}

export interface MovieCompaniesSyncResponse {
  movieId: number
  syncedCount: number
  visibleCount: number
}

export interface MovieCompaniesBatchSyncResponse {
  requestedLimit: number
  candidates: number
  processed: number
  synced: number
  failed: number
}

export interface MovieListCreateRequest {
  name: string
  description: string | null
}

export interface MovieListAttachRequest {
  listId: number | null
  name: string | null
  description: string | null
}

export interface MovieListOrderUpdateRequest {
  movieIds: number[]
}

export interface MovieListCoverUpdateRequest {
  coverMovieId: number | null
}

export interface MovieDetailsResponse {
  movieId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  description: string | null
  coverUrl: string | null
  images: MovieImageDto[]
  watches: MovieWatchDto[]
  externalIds: MovieExternalIdDto[]
  lists: MovieListSummaryDto[]
  companies: MovieCompanyDto[]
  people: MoviePersonCreditDto[]
  terms: MovieTermDto[]
  collection: MovieCollectionDto | null
}

export interface MovieTermCreateRequest {
  name: string
  kind: MovieTermKind
}

export interface MovieTermVisibilityRequest {
  hidden: boolean
}

export interface MovieTermsSyncResponse {
  movieId: number
  syncedCount: number
  visibleCount: number
}

export type MovieEnrichmentField = 'TITLE' | 'YEAR' | 'DESCRIPTION' | 'TMDB_ID' | 'IMDB_ID' | 'IMAGES'
export type MovieEnrichmentApplyMode = 'MISSING' | 'SELECTED'

export interface ManualMovieExternalIdView {
  provider: string
  externalId: string
}

export interface ManualMovieCatalogCreateRequest {
  title: string
  year: number | null
  tmdbId: string | null
  imdbId: string | null
}

export interface ManualMovieCatalogCreateResponse {
  movieId: number
  slug: string | null
  title: string
  year: number | null
  coverUrl: string | null
  createdMovie: boolean
  coverAssigned: boolean
  externalIds: ManualMovieExternalIdView[]
}

export interface MovieCatalogSuggestion {
  tmdbId: string
  title: string
  originalTitle: string | null
  year: number | null
  overview: string | null
  posterUrl: string | null
}

export interface MovieCatalogSuggestionsResponse {
  query: string
  suggestions: MovieCatalogSuggestion[]
}

export interface MovieCollectionMembersResponse {
  collectionId: number
  tmdbId: string
  name: string
  overview: string | null
  posterUrl: string | null
  backdropUrl: string | null
  members: MovieCollectionMember[]
}

export interface MovieCollectionMember {
  tmdbId: string
  title: string
  originalTitle: string | null
  year: number | null
  overview: string | null
  posterUrl: string | null
  backdropUrl: string | null
  tmdbUrl: string
  localMovieId: number | null
  localSlug: string | null
  inCatalog: boolean
}

export interface MoviePersonFilmographyResponse {
  personId: number
  tmdbId: string
  name: string
  profileUrl: string | null
  members: MoviePersonFilmographyMember[]
}

export interface MoviePersonFilmographyMember {
  tmdbId: string
  title: string
  originalTitle: string | null
  year: number | null
  overview: string | null
  posterUrl: string | null
  backdropUrl: string | null
  tmdbUrl: string
  localMovieId: number | null
  localSlug: string | null
  inCatalog: boolean
  roleLabel: string
}

export interface MovieCompanyMembersResponse {
  companyId: number
  tmdbId: string
  name: string
  logoUrl: string | null
  originCountry: string | null
  members: MovieCompanyMember[]
}

export interface MovieCompanyMember {
  tmdbId: string
  title: string
  originalTitle: string | null
  year: number | null
  overview: string | null
  posterUrl: string | null
  backdropUrl: string | null
  tmdbUrl: string
  localMovieId: number | null
  localSlug: string | null
  inCatalog: boolean
}

export interface MovieEnrichmentPreviewResponse {
  movieId: number
  resolvedTmdbId: string
  title: string
  fields: Array<{
    field: MovieEnrichmentField
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

export interface MovieEnrichmentApplyRequest {
  tmdbId: string | null
  mode: MovieEnrichmentApplyMode
  fields: MovieEnrichmentField[]
  imageSelection?: {
    selectedKeys: string[]
    primaryKey: string | null
  } | null
}

export interface MovieEnrichmentApplyResponse {
  movieId: number
  slug: string | null
  title: string
  appliedFields: MovieEnrichmentField[]
  coverAssigned: boolean
  externalIds: ManualMovieExternalIdView[]
}

export interface ExistingMovieWatchCreateRequest {
  watchedAt: string
}

export interface MovieWatchEntryModel {
  id: string
  watchId: number
  title: string
  meta: string
  relativeWatchedAt: string
  source: string
}

export interface MoviePageData {
  movieId: number
  slug: string
  title: string
  originalTitle: string
  year: number | null
  description: string | null
  coverUrl: string | null
  gallery: string[]
  heroMeta: string[]
  stats: {
    totalWatches: number
    firstWatch: string | null
    latestWatch: string | null
    latestWatchRelative: string
  }
  identifiers: Array<{
    id: string
    provider: string
    externalId: string
  }>
  lists: {
    summary: string
    visibleCount: number
    items: Array<{
      id: string
      listId: number
      name: string
      href: string
      description: string | null
      coverMovieId: number | null
      coverImageUrl: string | null
      itemCount: number
      previewMovies: Array<{
        id: string
        title: string
        href: string | null
        imageUrl: string | null
      }>
    }>
  }
  companies: {
    summary: string
    visibleCount: number
    items: Array<{
      id: string
      companyId: number
      name: string
      href: string
      logoUrl: string | null
      originCountry: string | null
      typeLabel: string
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
  terms: {
    summary: string
    visibleCount: number
    hiddenCount: number
    groups: Array<{
      id: string
      title: string
      description: string
      items: Array<{
        id: string
        termId: number
        name: string
        href: string
        kind: MovieTermKind
        source: MovieTermSource
        hiddenGlobally: boolean
        hiddenForMovie: boolean
        active: boolean
        stateLabel: string
      }>
    }>
  }
  collection: {
    id: string
    name: string
    tmdbId: string
    posterUrl: string | null
    backdropUrl: string | null
    progressLabel: string
    movies: Array<{
      id: string
      title: string
      subtitle: string
      href: string | null
      imageUrl: string | null
      watched: boolean
      current: boolean
    }>
  } | null
  recentWatches: MovieWatchEntryModel[]
}

export interface MovieTermPageData {
  kind: MovieTermKind
  name: string
  slug: string
  heroMeta: string[]
  stats: {
    movieCount: number
    watchedMoviesCount: number
  }
  movies: MovieLibraryCardModel[]
}

export interface MoviePersonPageData {
  personId: number
  tmdbId: string
  name: string
  slug: string
  profileUrl: string | null
  heroMeta: string[]
  roles: string[]
  stats: {
    movieCount: number
    watchedMoviesCount: number
  }
  movies: MovieLibraryCardModel[]
}

export interface MovieCompanyPageData {
  companyId: number
  tmdbId: string
  name: string
  slug: string
  logoUrl: string | null
  originCountry: string | null
  typeLabel: string
  heroMeta: string[]
  stats: {
    movieCount: number
    watchedMoviesCount: number
  }
  movies: MovieLibraryCardModel[]
}

export interface MovieListPageData {
  listId: number
  name: string
  slug: string
  description: string | null
  coverMovieId: number | null
  coverImageUrl: string | null
  heroMeta: string[]
  stats: {
    movieCount: number
    watchedMoviesCount: number
  }
  movies: MovieLibraryCardModel[]
}

export interface MovieListsIndexPageData {
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
  summary: string
  items: Array<{
    id: string
    listId: number
    name: string
    href: string
    description: string | null
    coverMovieId: number | null
    coverImageUrl: string | null
    itemCount: number
    previewMovies: Array<{
      id: string
      title: string
      href: string | null
      imageUrl: string | null
    }>
  }>
}

export interface MoviesStatsResponse {
  total: {
    watchesCount: number
    uniqueMoviesCount: number
  }
  unwatchedCount: number
  years: Array<{
    year: number
    watchesCount: number
    uniqueMoviesCount: number
    rewatchesCount: number
  }>
  latestWatchAt: string | null
  firstWatchAt: string | null
}

export interface MovieCollectionContextMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface MovieCollectionData {
  generatedAt: string
  hero: {
    title: string
    intro: string
    lead: EditorialHighlight | null
    supporting: EditorialHighlight[]
  }
  featuredSessions: EditorialShelfItem[]
  recentMoments: EditorialShelfItem[]
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: MovieCollectionContextMetric[]
  }
}

export interface MovieLibraryCardDto {
  movieId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  watchCount: number
  lastWatchedAt: string | null
}

export interface MoviesLibraryResponse {
  items: MovieLibraryCardDto[]
  nextCursor: string | null
}

export interface MoviesSearchResponse {
  movies: Array<{
    movieId: number
    title: string
    originalTitle: string
    slug: string | null
    year: number | null
    coverUrl: string | null
    watchedAt: string | null
  }>
}

export interface MoviesByYearResponse {
  year: number
  range: {
    start: string
    end: string
  }
  stats: {
    watchesCount: number
    uniqueMoviesCount: number
    rewatchesCount: number
  }
  watched: Array<{
    movieId: number
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
    movieId: number
    slug: string | null
    title: string
    originalTitle: string
    year: number | null
    coverUrl: string | null
  }>
}

export interface MovieLibraryMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface MovieLibraryYearChip {
  year: number
  label: string
  watches: string
}

export interface MovieLibraryCardModel {
  id: string
  movieId: number
  title: string
  subtitle: string
  href: string | null
  imageUrl: string | null
  sessionsLabel: string
  activityLabel: string
  aside: string
  isDormant?: boolean
}

export interface MovieLibraryPageData {
  hero: {
    title: string
    intro: string
    backLink: string
    backLabel: string
    accentLink: string
    accentLabel: string
    utilityLink?: string | null
    utilityLabel?: string | null
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
    years: MovieLibraryYearChip[]
  }
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: MovieLibraryMetric[]
  }
  sections: Array<{
    id: string
    eyebrow: string
    title: string
    description: string
    summary: string
    items: MovieLibraryCardModel[]
    emptyMessage?: string
  }>
  libraryCursor: string | null
  mode: 'library' | 'search' | 'year'
}
