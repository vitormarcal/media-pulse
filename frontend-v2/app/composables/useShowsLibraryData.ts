import type {
  ShowsByYearResponse,
  ShowsLibraryResponse,
  ShowsSearchResponse,
  ShowsStatsResponse,
  ShowLibraryPageData,
} from '~/types/shows'
import { buildShowLibraryPageData } from '~/utils/shows'

export interface ShowsLibraryQuery {
  q?: string
  year?: number | null
}

export async function fetchShowsLibraryPageData(query: ShowsLibraryQuery = {}): Promise<ShowLibraryPageData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase
  const q = query.q?.trim() || ''
  const year = query.year ?? null

  const [stats, library, searchResults, yearResults] = await Promise.all([
    $fetch<ShowsStatsResponse>('/api/shows/stats', { baseURL: apiBase }),
    $fetch<ShowsLibraryResponse>('/api/shows/library', { baseURL: apiBase, query: { limit: 24 } }),
    q
      ? $fetch<ShowsSearchResponse>('/api/shows/search', { baseURL: apiBase, query: { q, limit: 40 } })
      : Promise.resolve(null),
    year
      ? $fetch<ShowsByYearResponse>(`/api/shows/year/${year}`, {
          baseURL: apiBase,
          query: { limitWatched: 80, limitUnwatched: 80 },
        })
      : Promise.resolve(null),
  ])

  return buildShowLibraryPageData({
    stats,
    library,
    query: q,
    selectedYear: year,
    searchResults,
    yearResults,
  })
}

export async function fetchShowsLibraryNextPage(cursor: string): Promise<ShowsLibraryResponse> {
  const config = useRuntimeConfig()

  return $fetch<ShowsLibraryResponse>('/api/shows/library', {
    baseURL: config.public.apiBase,
    query: { limit: 24, cursor },
  })
}

export function useShowsLibraryData(query: ShowsLibraryQuery = {}) {
  const key = `shows-library-${query.q ?? ''}-${query.year ?? 'all'}`
  return useAsyncData(key, () => fetchShowsLibraryPageData(query))
}
