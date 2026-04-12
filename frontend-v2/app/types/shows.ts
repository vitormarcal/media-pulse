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
