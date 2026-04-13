import type { AuthorDetailsResponse, AuthorPageData } from '~/types/books'
import { buildAuthorPageData } from '~/utils/books'

export async function fetchAuthorPageData(id: string): Promise<AuthorPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<AuthorDetailsResponse>(`/api/books/authors/${id}`, {
    baseURL: config.public.apiBase,
  })

  return buildAuthorPageData(response)
}

export function useAuthorPageData(id: string) {
  return useAsyncData(`author-page-${id}`, () => fetchAuthorPageData(id))
}
