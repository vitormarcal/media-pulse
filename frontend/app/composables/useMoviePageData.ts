import type { MovieDetailsResponse, MoviePageData } from '~/types/movies'
import { buildMoviePageData } from '~/utils/movies'

export async function fetchMoviePageData(slug: string): Promise<MoviePageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MovieDetailsResponse>(`/api/movies/slug/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildMoviePageData(response)
}

export function useMoviePageData(slug: string) {
  return useAsyncData(`movie-page-${slug}`, () => fetchMoviePageData(slug))
}
