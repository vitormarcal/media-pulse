import type { PersonDetailsResponse, PersonPageData, PersonTmdbProfileDto } from '~/types/movies'
import { buildPersonPageData } from '~/utils/movies'

export async function fetchPersonPageData(slug: string): Promise<PersonPageData> {
  const config = useRuntimeConfig()

  const [local, tmdbProfile] = await Promise.all([
    $fetch<PersonDetailsResponse>(`/api/people/${slug}`, {
      baseURL: config.public.apiBase,
    }),
    $fetch<PersonTmdbProfileDto | null>(`/api/people/${slug}/tmdb-profile`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    }),
  ])

  const response: PersonDetailsResponse = {
    ...local,
    profileUrl: tmdbProfile?.profileUrl ?? local.profileUrl,
    tmdbProfile,
  }

  return buildPersonPageData(response)
}

export function usePersonPageData(slug: string) {
  return useAsyncData(`person-page-${slug}`, () => fetchPersonPageData(slug))
}
