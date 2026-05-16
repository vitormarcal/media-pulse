import type { GamesLibraryPageData, GamesLibraryResponse, GamesSearchResponse, GamesStatsResponse } from '~/types/games'
import { buildGamesLibraryPageData } from '~/utils/games'

export async function fetchGamesLibraryPageData(query = ''): Promise<GamesLibraryPageData> {
  const config = useRuntimeConfig()
  const q = query.trim()
  const [stats, library, searchResults] = await Promise.all([
    $fetch<GamesStatsResponse>('/api/games/stats', { baseURL: config.public.apiBase }),
    $fetch<GamesLibraryResponse>('/api/games/library', { baseURL: config.public.apiBase, query: { limit: 24 } }),
    q
      ? $fetch<GamesSearchResponse>('/api/games/search', { baseURL: config.public.apiBase, query: { q, limit: 40 } })
      : Promise.resolve(null),
  ])

  return buildGamesLibraryPageData({ stats, library, query: q, searchResults })
}

export async function fetchGamesLibraryNextPage(cursor: string): Promise<GamesLibraryResponse> {
  const config = useRuntimeConfig()

  return $fetch<GamesLibraryResponse>('/api/games/library', {
    baseURL: config.public.apiBase,
    query: { limit: 24, cursor },
  })
}
