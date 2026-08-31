import type { PersonDetailsResponse, PersonPageData } from '~/types/movies'
import { buildPersonPageData } from '~/utils/movies'

export async function fetchPersonPageData(slug: string): Promise<PersonPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<PersonDetailsResponse>(`/api/people/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildPersonPageData(response)
}

export function usePersonPageData(slug: string) {
  return useAsyncData(`person-page-${slug}`, () => fetchPersonPageData(slug))
}
