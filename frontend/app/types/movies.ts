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
