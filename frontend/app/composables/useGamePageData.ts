import type { GameDetailsResponse, GamePageData } from '~/types/games'
import { buildGamePageData } from '~/utils/games'

export async function fetchGamePageData(slug: string): Promise<GamePageData> {
  const config = useRuntimeConfig()
  const response = await $fetch<GameDetailsResponse>(`/api/games/slug/${slug}`, {
    baseURL: config.public.apiBase,
  })

  return buildGamePageData(response)
}

export function useGamePageData(slug: string) {
  return useAsyncData(`game-page-${slug}`, () => fetchGamePageData(slug))
}
