import type { EditorialHighlight, EditorialShelfItem } from '~/types/home'

export interface AuthorDto {
  id: number
  name: string
}

export interface EditionDto {
  id: number
  title: string | null
  isbn10: string | null
  isbn13: string | null
  pages: number | null
  language: string | null
  publisher: string | null
  format: string | null
  editionInformation: string | null
  coverUrl: string | null
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
  edition: EditionDto | null
}

export interface BookDetailsResponse {
  bookId: number
  slug: string
  title: string
  description: string | null
  coverUrl: string | null
  releaseDate: string | null
  rating: number | null
  reviewRaw: string | null
  reviewedAt: string | null
  authors: AuthorDto[]
  editions: EditionDto[]
  reads: ReadCardDto[]
}

export interface BookReadEntryModel {
  id: string
  title: string
  context: string
  meta: string
  relativeDate: string
  source: string
}

export interface BookEditionModel {
  id: string
  title: string
  meta: string[]
}

export interface BookPageData {
  slug: string
  title: string
  description: string | null
  coverUrl: string | null
  authorsLine: string
  subtitle: string | null
  heroMeta: string[]
  stats: {
    totalReads: number
    currentStatus: string
    latestActivity: string
    ratingText: string | null
  }
  editions: BookEditionModel[]
  recentReads: BookReadEntryModel[]
  reviewRaw: string | null
}

export interface BookCollectionContextMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface BookCollectionData {
  generatedAt: string
  hero: {
    title: string
    intro: string
    lead: EditorialHighlight | null
    supporting: EditorialHighlight[]
  }
  inProgress: EditorialShelfItem[]
  recentFinishes: EditorialShelfItem[]
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: BookCollectionContextMetric[]
  }
}

export interface BookLibraryCardDto {
  bookId: number
  slug: string
  title: string
  coverUrl: string | null
  authors: AuthorDto[]
  readsCount: number
  completedCount: number
  currentStatus: string | null
  activeProgressPct: number | null
  lastActivityAt: string | null
}

export interface BooksLibraryResponse {
  items: BookLibraryCardDto[]
  nextCursor: string | null
}

export interface BooksSearchResponse {
  books: BookCardDto[]
  authors: AuthorDto[]
}

export interface YearReadsResponse {
  year: number
  range: {
    start: string
    end: string
  }
  currentlyReading: ReadCardDto[]
  finished: ReadCardDto[]
  paused: ReadCardDto[]
  didNotFinish: ReadCardDto[]
  wantToRead: ReadCardDto[]
  unknown: ReadCardDto[]
  stats: {
    finishedCount: number
    currentlyReadingCount: number
    wantCount: number
    didNotFinishCount: number
    pausedCount: number
    pagesFinished: number | null
  }
}

export interface BookLibraryMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface BookLibraryYearChip {
  year: number
  label: string
  detail: string
}

export interface BookLibraryCardModel {
  id: string
  title: string
  subtitle: string
  href: string
  imageUrl: string | null
  progressLabel: string
  activityLabel: string
  aside: string
  isDormant?: boolean
}

export interface BookLibraryPageData {
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
      href: string
      meta: string
      note: string
    } | null
  }
  filters: {
    query: string
    selectedYear: number | null
    years: BookLibraryYearChip[]
  }
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: BookLibraryMetric[]
  }
  sections: Array<{
    id: string
    eyebrow: string
    title: string
    description: string
    summary: string
    items: BookLibraryCardModel[]
    emptyMessage?: string
  }>
  libraryCursor: string | null
  mode: 'library' | 'search' | 'year'
}
