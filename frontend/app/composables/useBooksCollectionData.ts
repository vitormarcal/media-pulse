import type { BooksListResponse, BooksSummaryResponse } from '~/types/home'
import type { BookCollectionData } from '~/types/books'
import { buildBookCollectionData } from '~/utils/books'

export async function fetchBooksCollectionData(): Promise<BookCollectionData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase

  const [summary, readingBooks, pausedBooks, finishedBooks] = await Promise.all([
    $fetch<BooksSummaryResponse>('/api/books/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<BooksListResponse>('/api/books/list', {
      baseURL: apiBase,
      query: { status: 'CURRENTLY_READING', limit: 8 },
    }),
    $fetch<BooksListResponse>('/api/books/list', { baseURL: apiBase, query: { status: 'PAUSED', limit: 4 } }),
    $fetch<BooksListResponse>('/api/books/list', { baseURL: apiBase, query: { status: 'READ', limit: 18 } }),
  ])

  return buildBookCollectionData({
    summary,
    readingBooks,
    pausedBooks,
    finishedBooks,
  })
}

export function useBooksCollectionData() {
  return useAsyncData('books-collection-data', fetchBooksCollectionData)
}
