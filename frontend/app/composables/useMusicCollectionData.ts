import type { MusicSummaryResponse, RecentAlbumsPageResponse } from '~/types/home'
import type { MusicCollectionData, TopAlbumResponse, TopArtistResponse, TopTrackResponse } from '~/types/music'
import { buildMusicCollectionData } from '~/utils/music'

function isoDate(daysAgo: number) {
  const date = new Date()
  date.setUTCDate(date.getUTCDate() - daysAgo)
  return date.toISOString()
}

export async function fetchMusicCollectionData(): Promise<MusicCollectionData> {
  const config = useRuntimeConfig()
  const apiBase = config.public.apiBase
  const start = isoDate(30)
  const end = new Date().toISOString()

  const [summary, recentAlbums, topArtists, topTracks, neverPlayedAlbums] = await Promise.all([
    $fetch<MusicSummaryResponse>('/api/music/summary', { baseURL: apiBase, query: { range: 'month' } }),
    $fetch<RecentAlbumsPageResponse>('/api/music/recent-albums', { baseURL: apiBase, query: { limit: 18 } }),
    $fetch<TopArtistResponse[]>('/api/music/tops/artists', { baseURL: apiBase, query: { start, end, limit: 6 } }),
    $fetch<TopTrackResponse[]>('/api/music/tops/tracks', { baseURL: apiBase, query: { start, end, limit: 6 } }),
    $fetch<TopAlbumResponse[]>('/api/music/albums/never-played', { baseURL: apiBase, query: { limit: 8 } }),
  ])

  return buildMusicCollectionData({
    summary,
    recentAlbums,
    topArtists,
    topTracks,
    neverPlayedAlbums,
  })
}

export function useMusicCollectionData() {
  return useAsyncData('music-collection-data', fetchMusicCollectionData)
}
