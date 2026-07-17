import type { AlbumListDetailsResponse, AlbumListSummaryDto } from '~/types/music'

export function useAlbumListsData() {
  const config = useRuntimeConfig()
  return useAsyncData('album-lists', () =>
    $fetch<AlbumListSummaryDto[]>('/api/music/lists', { baseURL: config.public.apiBase }),
  )
}

export function useAlbumListData(slug: string) {
  const config = useRuntimeConfig()
  return useAsyncData(`album-list-${slug}`, () =>
    $fetch<AlbumListDetailsResponse>(`/api/music/lists/${slug}`, { baseURL: config.public.apiBase }),
  )
}
