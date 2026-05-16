import type { MediaCommentDto } from '~/types/comments'
import type { MediaRatingDto } from '~/types/ratings'

export type GameSessionStatus = 'PLAYING' | 'BACKLOG' | 'COMPLETED' | 'ABANDONED'

export interface GameLibraryCardDto {
  gameId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  sessionCount: number
  latestSessionAt: string | null
  currentStatus: GameSessionStatus | null
}

export interface GamesLibraryResponse {
  items: GameLibraryCardDto[]
  nextCursor: string | null
}

export interface GamesStatsResponse {
  totalGamesCount: number
  sessionsCount: number
  backlogCount: number
  activeCount: number
  completedCount: number
  abandonedCount: number
  latestSessionAt: string | null
}

export interface GamesSearchResponse {
  games: GameLibraryCardDto[]
}

export interface GameImageDto {
  id: number
  url: string
  kind: string
  isPrimary: boolean
}

export interface GameExternalIdDto {
  provider: string
  externalId: string
}

export interface GameSessionDto {
  sessionId: number
  status: GameSessionStatus
  startedAt: string
  endedAt: string | null
  notes: string | null
}

export interface GameDetailsResponse {
  gameId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  description: string | null
  coverUrl: string | null
  images: GameImageDto[]
  sessions: GameSessionDto[]
  externalIds: GameExternalIdDto[]
  rating: MediaRatingDto | null
  comments: MediaCommentDto[]
}

export interface GameCatalogSuggestion {
  igdbId: string
  title: string
  year: number | null
  overview: string | null
  coverUrl: string | null
}

export interface GameCatalogSuggestionsResponse {
  query: string
  suggestions: GameCatalogSuggestion[]
}

export interface ManualGameCatalogCreateResponse {
  gameId: number
  slug: string | null
  title: string
  year: number | null
  coverUrl: string | null
  createdGame: boolean
  coverAssigned: boolean
  externalIds: Array<{ provider: string; externalId: string }>
}

export interface GameSessionCreateRequest {
  status: GameSessionStatus
  startedAt: string
  endedAt: string | null
  notes: string | null
}

export interface GameSessionCreateResponse {
  session: GameSessionDto
}

export interface GameSessionUpdateRequest {
  status: GameSessionStatus
  startedAt: string
  endedAt: string | null
  notes: string | null
}

export interface GameLibraryCardModel {
  id: string
  gameId: number
  title: string
  subtitle: string
  href: string
  imageUrl: string | null
  meta: string
  isDormant: boolean
}

export interface GamePageData {
  gameId: number
  slug: string
  title: string
  originalTitle: string
  year: number | null
  description: string | null
  coverUrl: string | null
  gallery: string[]
  heroMeta: string[]
  rating: MediaRatingDto | null
  stats: {
    totalSessions: number
    latestSessionAt: string | null
    latestSessionRelative: string
  }
  identifiers: Array<{ id: string; provider: string; externalId: string }>
  sessions: Array<{
    id: string
    sessionId: number
    title: string
    status: GameSessionStatus
    statusLabel: string
    startedAt: string
    endedAt: string | null
    relativeStartedAt: string
    meta: string
    notes: string | null
  }>
  comments: MediaCommentDto[]
}

export interface GamesLibraryPageData {
  hero: {
    title: string
    intro: string
    accentLink: string
    accentLabel: string
    spotlight: GameLibraryCardModel | null
  }
  query: string
  stats: Array<{ id: string; label: string; value: string; note: string }>
  items: GameLibraryCardModel[]
  libraryCursor: string | null
  mode: 'library' | 'search'
}
