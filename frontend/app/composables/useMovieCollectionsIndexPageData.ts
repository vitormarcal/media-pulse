import type { MovieCollectionsIndexPageData, MovieCollectionSummaryDto } from '~/types/movies'
import { buildMovieCollectionsIndexPageData } from '~/utils/movies'

export async function fetchMovieCollectionsIndexPageData(): Promise<MovieCollectionsIndexPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MovieCollectionSummaryDto[]>('/api/movies/collections', {
    baseURL: config.public.apiBase,
  })

  return buildMovieCollectionsIndexPageData(response)
}

export function useMovieCollectionsIndexPageData() {
  return useAsyncData('movie-collections-index-page', fetchMovieCollectionsIndexPageData)
}
