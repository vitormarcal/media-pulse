import type { MovieListDetailsResponse, MovieListPageData } from '~/types/movies'
import { buildMovieListPageData } from '~/utils/movies'

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
