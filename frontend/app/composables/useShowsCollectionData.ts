import type { CurrentlyWatchingShowDto, ShowsRecentResponse, ShowsSummaryResponse } from '~/types/home'
import type { ShowCollectionData } from '~/types/shows'
import { buildShowCollectionData } from '~/utils/shows'

export async function fetchShowsCollectionData(): Promise<ShowCollectionData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase

  const [summary, currentShows, recentShows] = await Promise.all([
    $fetch<ShowsSummaryResponse>('/api/shows/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<CurrentlyWatchingShowDto[]>('/api/shows/currently-watching', {
      baseURL: apiBase,
      query: { limit: 12, activeWithinDays: 120 },
    }),
    $fetch<ShowsRecentResponse>('/api/shows/recent', { baseURL: apiBase, query: { limit: 18 } }),
  ])

  return buildShowCollectionData({
    summary,
    currentShows,
    recentShows,
  })
}

export function useShowsCollectionData() {
  return useAsyncData('shows-collection-data', fetchShowsCollectionData)
}
