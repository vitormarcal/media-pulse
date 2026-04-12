import type { AlbumPageData, AlbumPageResponse } from '~/types/music'
import { buildAlbumPageData } from '~/utils/music'

export async function fetchAlbumPageData(id: string): Promise<AlbumPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<AlbumPageResponse>(`/api/music/albums/${id}`, {
    baseURL: config.public.apiBase,
  })

  return buildAlbumPageData(response)
}

export function useAlbumPageData(id: string) {
  return useAsyncData(`album-page-${id}`, () => fetchAlbumPageData(id))
}
