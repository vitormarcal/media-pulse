import type { MusicSummaryResponse } from '~/types/home'
import type {
  AlbumLibraryPageResponse,
  ArtistLibraryPageResponse,
  MusicLibraryKind,
  MusicLibraryPageData,
  MusicSearchResponse,
  TrackLibraryPageResponse,
} from '~/types/music'
import { buildMusicLibraryPageData } from '~/utils/music'

export interface MusicLibraryQuery {
  q?: string
  kind?: MusicLibraryKind
}

async function fetchLibraryKindPage(kind: MusicLibraryKind, limit: number, cursor?: string) {
  const config = useRuntimeConfig()
  const query = cursor ? { limit, cursor } : { limit }

  if (kind === 'artists') {
    return $fetch<ArtistLibraryPageResponse>('/api/music/library/artists', {
      baseURL: config.public.apiBase,
      query,
    })
  }

  if (kind === 'albums') {
    return $fetch<AlbumLibraryPageResponse>('/api/music/library/albums', {
      baseURL: config.public.apiBase,
      query,
    })
  }

  return $fetch<TrackLibraryPageResponse>('/api/music/library/tracks', {
    baseURL: config.public.apiBase,
    query,
  })
}

export async function fetchMusicLibraryPageData(query: MusicLibraryQuery = {}): Promise<MusicLibraryPageData> {
  const config = useRuntimeConfig()
  const q = query.q?.trim() || ''
  const selectedKind = query.kind ?? 'albums'

  const [summary, libraryPage, searchResults] = await Promise.all([
    $fetch<MusicSummaryResponse>('/api/music/summary', { baseURL: config.public.apiBase, query: { range: 'month' } }),
    q ? Promise.resolve(null) : fetchLibraryKindPage(selectedKind, 24),
    q
      ? $fetch<MusicSearchResponse>('/api/music/search', { baseURL: config.public.apiBase, query: { q, limit: 40 } })
      : Promise.resolve(null),
  ])

  return buildMusicLibraryPageData({
    summary,
    selectedKind,
    query: q,
    artists: selectedKind === 'artists' ? (libraryPage as ArtistLibraryPageResponse | null) : null,
    albums: selectedKind === 'albums' ? (libraryPage as AlbumLibraryPageResponse | null) : null,
    tracks: selectedKind === 'tracks' ? (libraryPage as TrackLibraryPageResponse | null) : null,
    searchResults,
  })
}

export async function fetchMusicLibraryNextPage(kind: MusicLibraryKind, cursor: string) {
  return fetchLibraryKindPage(kind, 24, cursor)
}

export function useMusicLibraryData(query: MusicLibraryQuery = {}) {
  const key = `music-library-${query.kind ?? 'albums'}-${query.q ?? ''}`
  return useAsyncData(key, () => fetchMusicLibraryPageData(query))
}
