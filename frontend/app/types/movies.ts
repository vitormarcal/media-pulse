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
}

export interface MovieWatchEntryModel {
  id: string
  title: string
  meta: string
  relativeWatchedAt: string
  source: string
}

export interface MoviePageData {
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
  recentWatches: MovieWatchEntryModel[]
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
