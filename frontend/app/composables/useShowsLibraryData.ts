import { showBrowseCards, showBrowseSearchCards, showBrowseYearCards } from '~/utils/showBrowse'
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
  unwatched?: boolean
}

export async function fetchShowsLibraryPageData(query: ShowsLibraryQuery = {}): Promise<ShowLibraryPageData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase
  const q = query.q?.trim() || ''
  const year = query.year ?? null
  const unwatched = query.unwatched ?? false

  const [stats, library, searchResults, yearResults] = await Promise.all([
    $fetch<ShowsStatsResponse>('/api/shows/stats', { baseURL: apiBase }),
    $fetch<ShowsLibraryResponse>('/api/shows/library', { baseURL: apiBase, query: { limit: 24, unwatched } }),
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
    selectedUnwatched: unwatched,
    searchResults,
    yearResults,
  })
}

export async function fetchShowsLibraryNextPage(cursor: string, unwatched = false): Promise<ShowsLibraryResponse> {
  const config = useRuntimeConfig()

  return $fetch<ShowsLibraryResponse>('/api/shows/library', {
    baseURL: config.public.apiBase,
    query: { limit: 24, cursor, unwatched },
  })
}

export function useShowsLibraryData(query: ShowsLibraryQuery = {}) {
  const key = `shows-library-${query.q ?? ''}-${query.year ?? 'all'}-${query.unwatched ? 'unwatched' : 'all-status'}`
  return useAsyncData(key, () => fetchShowsLibraryPageData(query))
}

// This view requests only the catalog branch being displayed; optional history loads independently.
export async function fetchShowsBrowsePage(query: ShowsLibraryQuery = {}) {
  const baseURL = useRuntimeConfig().public.apiBase
  const q = query.q?.trim() || ''
  if (q) {
    const response = await $fetch<ShowsSearchResponse>('/api/shows/search', { baseURL, query: { q, limit: 40 } })
    return {
      items: showBrowseSearchCards(response.shows.filter((show) => !query.unwatched || !show.watchedAt)),
      nextCursor: null,
      truncated: response.shows.length >= 40,
      yearStats: null,
    }
  }
  if (query.year) {
    const response = await $fetch<ShowsByYearResponse>(`/api/shows/year/${query.year}`, {
      baseURL,
      query: { limitWatched: 1000, limitUnwatched: 1 },
    })
    return {
      items: showBrowseYearCards(response.watched),
      nextCursor: null,
      truncated: response.watched.length < response.stats.uniqueShowsCount,
      yearStats: response.stats,
    }
  }
  const response = await $fetch<ShowsLibraryResponse>('/api/shows/library', {
    baseURL,
    query: { limit: 24, unwatched: query.unwatched ?? false },
  })
  return { items: showBrowseCards(response.items), nextCursor: response.nextCursor, truncated: false, yearStats: null }
}
