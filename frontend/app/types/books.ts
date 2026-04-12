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
