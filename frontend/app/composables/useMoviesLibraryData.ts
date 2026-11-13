import type {
  MovieLibraryPageData,
  MoviesByYearResponse,
  MoviesLibraryResponse,
  MoviesSearchResponse,
  MoviesStatsResponse,
} from '~/types/movies'
import { buildMovieLibraryPageData } from '~/utils/movies'
import { movieBrowseCards, movieBrowseSearchCards, movieBrowseYearCards } from '~/utils/movieBrowse'

export interface MoviesLibraryQuery {
  q?: string
  year?: number | null
  unwatched?: boolean
}

export async function fetchMoviesLibraryPageData(query: MoviesLibraryQuery = {}): Promise<MovieLibraryPageData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase
  const q = query.q?.trim() || ''
  const year = query.year ?? null
  const unwatched = query.unwatched ?? false

  const [stats, library, searchResults, yearResults] = await Promise.all([
    $fetch<MoviesStatsResponse>('/api/movies/stats', { baseURL: apiBase }),
    $fetch<MoviesLibraryResponse>('/api/movies/library', { baseURL: apiBase, query: { limit: 24, unwatched } }),
    q
      ? $fetch<MoviesSearchResponse>('/api/movies/search', { baseURL: apiBase, query: { q, limit: 40 } })
      : Promise.resolve(null),
    year
      ? $fetch<MoviesByYearResponse>(`/api/movies/year/${year}`, {
          baseURL: apiBase,
          query: { limitWatched: 80, limitUnwatched: 80 },
        })
      : Promise.resolve(null),
  ])

  return buildMovieLibraryPageData({
    stats,
    library,
    query: q,
    selectedYear: year,
    selectedUnwatched: unwatched,
    searchResults,
    yearResults,
  })
}

export async function fetchMoviesLibraryNextPage(cursor: string, unwatched = false): Promise<MoviesLibraryResponse> {
  const config = useRuntimeConfig()

  return $fetch<MoviesLibraryResponse>('/api/movies/library', {
    baseURL: config.public.apiBase,
    query: { limit: 24, cursor, unwatched },
  })
}

export function useMoviesLibraryData(query: MoviesLibraryQuery = {}) {
  const key = `movies-library-${query.q ?? ''}-${query.year ?? 'all'}-${query.unwatched ? 'unwatched' : 'all-status'}`
  return useAsyncData(key, () => fetchMoviesLibraryPageData(query))
}

// This view requests only the catalog branch being displayed; optional history loads independently.
export async function fetchMoviesBrowsePage(query: MoviesLibraryQuery = {}) {
  const baseURL = useRuntimeConfig().public.apiBase
  const q = query.q?.trim() || ''
  if (q) {
    const response = await $fetch<MoviesSearchResponse>('/api/movies/search', { baseURL, query: { q, limit: 40 } })
    return {
      items: movieBrowseSearchCards(response.movies.filter((movie) => !query.unwatched || !movie.watchedAt)),
      nextCursor: null,
      truncated: response.movies.length >= 40,
      yearStats: null,
    }
  }
  if (query.year) {
    const response = await $fetch<MoviesByYearResponse>(`/api/movies/year/${query.year}`, {
      baseURL,
      query: { limitWatched: 1000, limitUnwatched: 1 },
    })
    return {
      items: movieBrowseYearCards(response.watched),
      nextCursor: null,
      truncated: response.watched.length < response.stats.uniqueMoviesCount,
      yearStats: response.stats,
    }
  }
  const response = await $fetch<MoviesLibraryResponse>('/api/movies/library', {
    baseURL,
    query: { limit: 24, unwatched: query.unwatched ?? false },
  })
  return { items: movieBrowseCards(response.items), nextCursor: response.nextCursor, truncated: false, yearStats: null }
}
