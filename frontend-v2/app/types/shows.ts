import type { EditorialHighlight, EditorialShelfItem, ShowProgressDto } from '~/types/home'

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

export interface ShowExternalIdDto {
  provider: string
  externalId: string
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
}

export interface ShowSeasonCardModel {
  id: string
  title: string
  progressLabel: string
  progressValue: number
  detail: string
  isComplete: boolean
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
  slug: string
  title: string
  originalTitle: string
  year: number | null
  description: string | null
  coverUrl: string | null
  gallery: string[]
  progress: {
    watchedEpisodes: number
    totalEpisodes: number
    watchedSeasons: number
    totalSeasons: number
    completionPct: number
    statusText: string
  }
  heroMeta: string[]
  seasons: ShowSeasonCardModel[]
  recentWatches: ShowWatchEntryModel[]
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
