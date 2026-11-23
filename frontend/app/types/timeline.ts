export const timelineDomains = {
  MUSIC: 'Música',
  BOOKS: 'Livros',
  MAGAZINES: 'Revistas',
  MOVIES: 'Filmes',
  SHOWS: 'Séries',
  GAMES: 'Games',
  ARTWORKS: 'Artes visuais',
  WORKOUTS: 'Treinos',
} as const
export type TimelineDomain = keyof typeof timelineDomains
export type TimelineKind = 'ACTIVITY' | 'ADDITION'
export interface TimelineLink {
  title: string
  href: string
  imageUrl?: string | null
  subtitle?: string | null
}
export interface TimelineEvent extends TimelineLink {
  id: string
  domain: TimelineDomain
  kind: TimelineKind
  date: string
  occurredAt: string | null
  precision: 'DAY' | 'INSTANT'
  action: string
  relatedItems: TimelineLink[]
}
export interface TimelinePage {
  items: TimelineEvent[]
  nextCursor: string | null
  timeZone: string
}
export interface TimelineMonth {
  month: string
  count: number
}
export interface TimelinePeriods {
  months: TimelineMonth[]
  timeZone: string
}
