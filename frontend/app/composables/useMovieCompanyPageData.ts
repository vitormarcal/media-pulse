import type { MovieCompanyDetailsResponse, MovieCompanyPageData } from '~/types/movies'
import { buildMovieCompanyPageData } from '~/utils/movies'

export async function fetchMovieCompanyPageData(slug: string): Promise<MovieCompanyPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MovieCompanyDetailsResponse>(`/api/movies/companies/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildMovieCompanyPageData(response)
}

export function useMovieCompanyPageData(slug: string) {
  return useAsyncData(`movie-company-page-${slug}`, () => fetchMovieCompanyPageData(slug))
}
