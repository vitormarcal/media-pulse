import type {
  BookLibraryPageData,
  BooksLibraryResponse,
  BooksSearchResponse,
  YearReadsResponse,
} from '~/types/books'
import type { BooksSummaryResponse } from '~/types/home'
import { buildBookLibraryPageData } from '~/utils/books'

export interface BooksLibraryQuery {
  q?: string
  year?: number | null
}

export async function fetchBooksLibraryPageData(query: BooksLibraryQuery = {}): Promise<BookLibraryPageData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase
  const q = query.q?.trim() || ''
  const year = query.year ?? null

  const [summary, library, searchResults, yearResults] = await Promise.all([
    $fetch<BooksSummaryResponse>('/api/books/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<BooksLibraryResponse>('/api/books/library', { baseURL: apiBase, query: { limit: 24 } }),
    q
      ? $fetch<BooksSearchResponse>('/api/books/search', { baseURL: apiBase, query: { q, limit: 40 } })
      : Promise.resolve(null),
    year
      ? $fetch<YearReadsResponse>(`/api/books/year/${year}`, { baseURL: apiBase })
      : Promise.resolve(null),
  ])

  return buildBookLibraryPageData({
    summary,
    library,
    query: q,
    selectedYear: year,
    searchResults,
    yearResults,
  })
}

export async function fetchBooksLibraryNextPage(cursor: string): Promise<BooksLibraryResponse> {
  const config = useRuntimeConfig()

  return $fetch<BooksLibraryResponse>('/api/books/library', {
    baseURL: config.public.apiBase,
    query: { limit: 24, cursor },
  })
}

export function useBooksLibraryData(query: BooksLibraryQuery = {}) {
  const key = `books-library-${query.q ?? ''}-${query.year ?? 'all'}`
  return useAsyncData(key, () => fetchBooksLibraryPageData(query))
}
