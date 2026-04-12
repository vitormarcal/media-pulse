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
  ] = await Promise.all([
    $fetch<MusicSummaryResponse>('/api/music/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<RecentAlbumsPageResponse>('/api/music/recent-albums', { baseURL: apiBase, query: { limit: 6 } }),
    $fetch<MoviesSummaryResponse>('/api/movies/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<MoviesRecentResponse>('/api/movies/recent', { baseURL: apiBase, query: { limit: 6 } }),
    $fetch<ShowsSummaryResponse>('/api/shows/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<CurrentlyWatchingShowDto[]>('/api/shows/currently-watching', { baseURL: apiBase, query: { limit: 6, activeWithinDays: 90 } }),
    $fetch<ShowsRecentResponse>('/api/shows/recent', { baseURL: apiBase, query: { limit: 6 } }),
    $fetch<BooksSummaryResponse>('/api/books/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<BooksListResponse>('/api/books/list', { baseURL: apiBase, query: { status: 'READING', limit: 4 } }),
    $fetch<BooksListResponse>('/api/books/list', { baseURL: apiBase, query: { status: 'FINISHED', limit: 4 } }),
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
  })
}

export function useHomePageData() {
  return useAsyncData('home-page-data', fetchHomePageData)
}
