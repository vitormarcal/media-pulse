import type { MoviePersonDetailsResponse, MoviePersonPageData } from '~/types/movies'
import { buildMoviePersonPageData } from '~/utils/movies'

export async function fetchMoviePersonPageData(slug: string): Promise<MoviePersonPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MoviePersonDetailsResponse>(`/api/movies/people/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildMoviePersonPageData(response)
}

export function useMoviePersonPageData(slug: string) {
  return useAsyncData(`movie-person-page-${slug}`, () => fetchMoviePersonPageData(slug))
}
