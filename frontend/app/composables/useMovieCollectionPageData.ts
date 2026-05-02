import type { MovieCollectionMembersResponse, MovieCollectionPageData } from '~/types/movies'
import { buildMovieCollectionPageData } from '~/utils/movies'

export async function fetchMovieCollectionPageData(collectionId: string): Promise<MovieCollectionPageData> {
  const config = useRuntimeConfig()

  const response = await $fetch<MovieCollectionMembersResponse>(
    `/api/movies/collections/${collectionId}/tmdb-members`,
    {
      baseURL: config.public.apiBase,
    },
  )

  return buildMovieCollectionPageData(response)
}

export function useMovieCollectionPageData(collectionId: string) {
  return useAsyncData(`movie-collection-page-${collectionId}`, () => fetchMovieCollectionPageData(collectionId))
}
