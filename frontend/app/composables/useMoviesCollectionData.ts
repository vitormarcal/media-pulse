import type { MoviesRecentResponse, MoviesSummaryResponse } from '~/types/home'
import type { MovieCollectionData, MoviesStatsResponse } from '~/types/movies'
import { buildMovieCollectionData } from '~/utils/movies'

export async function fetchMoviesCollectionData(): Promise<MovieCollectionData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase

  const [summary, recentMovies, stats] = await Promise.all([
    $fetch<MoviesSummaryResponse>('/api/movies/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<MoviesRecentResponse>('/api/movies/recent', { baseURL: apiBase, query: { limit: 18 } }),
    $fetch<MoviesStatsResponse>('/api/movies/stats', { baseURL: apiBase }),
  ])

  return buildMovieCollectionData({
    summary,
    recentMovies,
    stats,
  })
}

export function useMoviesCollectionData() {
  return useAsyncData('movies-collection-data', fetchMoviesCollectionData)
}
