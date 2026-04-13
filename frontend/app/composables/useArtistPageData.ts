import type { ArtistPageData, ArtistPageResponse } from '~/types/music'
import { buildArtistPageData } from '~/utils/music'

export async function fetchArtistPageData(id: string): Promise<ArtistPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<ArtistPageResponse>(`/api/music/artists/${id}`, {
    baseURL: config.public.apiBase,
  })

  return buildArtistPageData(response)
}

export function useArtistPageData(id: string) {
  return useAsyncData(`artist-page-${id}`, () => fetchArtistPageData(id))
}
