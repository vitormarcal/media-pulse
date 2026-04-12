import type { ShowDetailsResponse, ShowPageData } from '~/types/shows'
import { buildShowPageData } from '~/utils/shows'

export async function fetchShowPageData(slug: string): Promise<ShowPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<ShowDetailsResponse>(`/api/shows/slug/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildShowPageData(response)
}

export function useShowPageData(slug: string) {
  return useAsyncData(`show-page-${slug}`, () => fetchShowPageData(slug))
}
