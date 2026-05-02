import type {
  MovieListDetailsResponse,
  MovieListPageData,
  MovieListSummaryDto,
  MovieListsIndexPageData,
} from '~/types/movies'
import { buildMovieListPageData, buildMovieListsIndexPageData } from '~/utils/movies'

export async function fetchMovieListPageData(slug: string): Promise<MovieListPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MovieListDetailsResponse>(`/api/movies/lists/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildMovieListPageData(response)
}

export function useMovieListPageData(slug: string) {
  return useAsyncData(`movie-list-page-${slug}`, () => fetchMovieListPageData(slug))
}

export async function fetchMovieListsIndexPageData(): Promise<MovieListsIndexPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MovieListSummaryDto[]>('/api/movies/lists', {
    baseURL: config.public.apiBase,
  })

  return buildMovieListsIndexPageData(response)
}

export function useMovieListsIndexPageData() {
  return useAsyncData('movie-lists-index-page', () => fetchMovieListsIndexPageData())
}
