import type { MovieTermDetailsResponse, MovieTermPageData } from '~/types/movies'
import { buildMovieTermPageData } from '~/utils/movies'

export async function fetchMovieTermPageData(kind: string, slug: string): Promise<MovieTermPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MovieTermDetailsResponse>(`/api/movies/terms/${kind}/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildMovieTermPageData(response)
}

export function useMovieTermPageData(kind: string, slug: string) {
  return useAsyncData(`movie-term-page-${kind}-${slug}`, () => fetchMovieTermPageData(kind, slug))
}
