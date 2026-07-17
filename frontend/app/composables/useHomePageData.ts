import type {
  BooksListResponse,
  BooksSummaryResponse,
  CurrentlyWatchingShowDto,
  HomePageData,
  MoviesRecentResponse,
  MoviesSummaryResponse,
  MusicSummaryResponse,
  RecentAlbumsPageResponse,
  ShowsRecentResponse,
  ShowsSummaryResponse,
} from '~/types/home'
import type { GamesLibraryResponse, GamesStatsResponse } from '~/types/games'
import { buildHomePageData } from '~/utils/home'

export async function fetchHomePageData(): Promise<HomePageData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase

  const [
    musicSummary,
    recentAlbums,
    movieSummary,
    recentMovies,
    showSummary,
    currentShows,
    recentShows,
    booksSummary,
    readingBooks,
    finishedBooks,
    gamesStats,
    gamesLibrary,
  ] = await Promise.all([
    $fetch<MusicSummaryResponse>('/api/music/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<RecentAlbumsPageResponse>('/api/music/recent-albums', { baseURL: apiBase, query: { limit: 6 } }),
    $fetch<MoviesSummaryResponse>('/api/movies/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<MoviesRecentResponse>('/api/movies/recent', { baseURL: apiBase, query: { limit: 6 } }),
    $fetch<ShowsSummaryResponse>('/api/shows/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<CurrentlyWatchingShowDto[]>('/api/shows/currently-watching', {
      baseURL: apiBase,
      query: { limit: 6, activeWithinDays: 90 },
    }),
    $fetch<ShowsRecentResponse>('/api/shows/recent', { baseURL: apiBase, query: { limit: 6 } }),
    $fetch<BooksSummaryResponse>('/api/books/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<BooksListResponse>('/api/books/list', {
      baseURL: apiBase,
      query: { status: 'CURRENTLY_READING', limit: 4 },
    }),
    $fetch<BooksListResponse>('/api/books/list', { baseURL: apiBase, query: { status: 'READ', limit: 4 } }),
    $fetch<GamesStatsResponse>('/api/games/stats', { baseURL: apiBase }),
    $fetch<GamesLibraryResponse>('/api/games/library', { baseURL: apiBase, query: { limit: 6 } }),
  ])

  return buildHomePageData({
    musicSummary,
    recentAlbums,
    movieSummary,
    recentMovies,
    showSummary,
    currentShows,
    recentShows,
    booksSummary,
    readingBooks,
    finishedBooks,
    gamesStats,
    gamesLibrary,
  })
}

export function useHomePageData() {
  return useAsyncData('home-page-data', fetchHomePageData)
}
