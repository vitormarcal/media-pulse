import type { BookDetailsResponse, BookPageData } from '~/types/books'
import { buildBookPageData } from '~/utils/books'

export async function fetchBookPageData(slug: string): Promise<BookPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<BookDetailsResponse>(`/api/books/slug/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildBookPageData(response)
}

export function useBookPageData(slug: string) {
  return useAsyncData(`book-page-${slug}`, () => fetchBookPageData(slug))
}
