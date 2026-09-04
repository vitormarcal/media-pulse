import type { ShowListDetailsResponse, ShowListSummaryDto } from '~/types/shows'
export function useShowLists() {
  const config = useRuntimeConfig()
  return useAsyncData('show-lists', () =>
    $fetch<ShowListSummaryDto[]>('/api/shows/lists', { baseURL: config.public.apiBase }),
  )
}
export function useShowList(slug: string) {
  const config = useRuntimeConfig()
  return useAsyncData(`show-list-${slug}`, () =>
    $fetch<ShowListDetailsResponse>(`/api/shows/lists/${slug}`, { baseURL: config.public.apiBase }),
  )
}
