import type { AlbumTermDetailsResponse, AlbumTermPageData } from '~/types/music'
import { buildAlbumTermPageData } from '~/utils/music'

export async function fetchAlbumTermPageData(kind: string, slug: string): Promise<AlbumTermPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<AlbumTermDetailsResponse>(`/api/music/terms/${kind}/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildAlbumTermPageData(response)
}

export function useAlbumTermPageData(kind: string, slug: string) {
  return useAsyncData(`album-term-page-${kind}-${slug}`, () => fetchAlbumTermPageData(kind, slug))
}
