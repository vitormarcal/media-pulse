import type { ShowSeasonDetailsResponse, ShowSeasonPageData } from '~/types/shows'
import { buildShowSeasonPageData } from '~/utils/shows'

export async function fetchShowSeasonPageData(slug: string, seasonNumber: number): Promise<ShowSeasonPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<ShowSeasonDetailsResponse>(`/api/shows/slug/${slug}/seasons/${seasonNumber}`, {
    baseURL: config.public.apiBase,
  })

  return buildShowSeasonPageData(response)
}

export function useShowSeasonPageData(slug: string, seasonNumber: number) {
  return useAsyncData(`show-season-page-${slug}-${seasonNumber}`, () => fetchShowSeasonPageData(slug, seasonNumber))
}
