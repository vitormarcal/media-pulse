import type { ShowTermDetailsResponse } from '~/types/shows'
import { buildShowTermPageData } from '~/utils/shows'

export function useShowTermPageData(kind: string, termId: string, slug: string) {
  const config = useRuntimeConfig()
  return useAsyncData(`show-term-${kind}-${termId}-${slug}`, async () => {
    const response = await $fetch<ShowTermDetailsResponse>(`/api/shows/terms/${kind}/${termId}/${slug}`, {
      baseURL: config.public.apiBase,
    })
    return buildShowTermPageData(response)
  })
}
