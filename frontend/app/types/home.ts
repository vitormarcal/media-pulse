export type EditorialMediaType = 'music' | 'show' | 'movie' | 'book'

export interface ApiRange {
  start: string
  end: string
}

export interface MusicSummaryResponse {
  range: ApiRange
  artistsCount: number
  albumsCount: number
  tracksCount: number
}

export interface RecentAlbumResponse {
  albumId: number
  albumTitle: string
  artistId: number
  artistName: string
  year: number | null
  coverUrl: string | null
  lastPlayed: string
  playCount: number
}

export interface RecentAlbumsPageResponse {
  items: RecentAlbumResponse[]
  nextCursor: string | null
}

export interface MoviesSummaryResponse {
  range: ApiRange
  watchesCount: number
  uniqueMoviesCount: number
}

export interface MovieCardDto {
  movieId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  watchedAt: string | null
}

export interface MoviesRecentResponse {
  items: MovieCardDto[]
  nextCursor: string | null
}

export interface ShowsSummaryResponse {
  range: ApiRange
  watchesCount: number
  uniqueShowsCount: number
}

export interface ShowProgressDto {
  episodesCount: number
  watchedEpisodesCount: number
  seasonsCount: number
  completedSeasonsCount: number
  completed: boolean
  inProgress: boolean
}

export interface CurrentlyWatchingShowDto {
  showId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  lastWatchedAt: string
  progress: ShowProgressDto
}

export interface ShowCardDto {
  showId: number
  title: string
  originalTitle: string
  slug: string | null
  year: number | null
  coverUrl: string | null
  watchedAt: string | null
}

export interface ShowsRecentResponse {
  items: ShowCardDto[]
  nextCursor: string | null
}

export interface BooksSummaryResponse {
  range: ApiRange
  counts: {
    finished: number
    reading: number
    want: number
    dnf: number
    paused: number
    total: number
  }
  topAuthors: Array<{
    authorId: number
    authorName: string
    finishedCount: number
  }>
}

export interface AuthorDto {
  id: number
  name: string
}

export interface BookCardDto {
  bookId: number
  slug: string
  title: string
  coverUrl: string | null
  releaseDate: string | null
  rating: number | null
  reviewedAt: string | null
  authors: AuthorDto[]
}

export interface ReadCardDto {
  readId: number
  status: string
  startedAt: string | null
  finishedAt: string | null
  progressPct: number | null
  progressPages: number | null
  source: string
  book: BookCardDto
}

export interface BooksListResponse {
  items: ReadCardDto[]
  nextCursor: string | null
}

export interface EditorialHighlight {
  id: string
  type: EditorialMediaType
  title: string
  subtitle: string
  eyebrow: string
  imageUrl: string | null
  href: string | null
  timestamp: string
  meta: string
}

export interface EditorialShelfItem {
  id: string
  type: EditorialMediaType
  title: string
  subtitle: string
  imageUrl: string | null
  href: string | null
  meta: string
  detail: string
  timestamp?: string | null
}

export interface HomePageData {
  generatedAt: string
  hero: {
    title: string
    intro: string
    lead: EditorialHighlight | null
    supporting: EditorialHighlight[]
  }
  inProgress: EditorialShelfItem[]
  recentMoments: EditorialShelfItem[]
  sections: Array<{
    id: string
    eyebrow: string
    title: string
    description: string
    summary: string
    items: EditorialShelfItem[]
  }>
}
