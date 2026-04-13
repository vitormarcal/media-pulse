import type {
  AlbumLibraryPageResponse,
  ArtistLibraryPageResponse,
  MusicByYearResponse,
  MusicLibraryKind,
  MusicLibraryPageData,
  MusicSearchResponse,
  MusicStatsResponse,
  TrackLibraryPageResponse,
} from '~/types/music'
import { buildMusicLibraryPageData } from '~/utils/music'

export interface MusicLibraryQuery {
  q?: string
  kind?: MusicLibraryKind
  year?: number | null
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
  const year = query.year ?? null

  const [stats, libraryPage, searchResults, yearResults] = await Promise.all([
    $fetch<MusicStatsResponse>('/api/music/stats', { baseURL: config.public.apiBase }),
    q || year ? Promise.resolve(null) : fetchLibraryKindPage(selectedKind, 24),
    q
      ? $fetch<MusicSearchResponse>('/api/music/search', { baseURL: config.public.apiBase, query: { q, limit: 40 } })
      : Promise.resolve(null),
    year
      ? $fetch<MusicByYearResponse>(`/api/music/year/${year}`, {
          baseURL: config.public.apiBase,
          query: { limitAlbums: 80, limitArtists: 12, limitTracks: 12 },
        })
      : Promise.resolve(null),
  ])

  return buildMusicLibraryPageData({
    stats,
    selectedKind: year ? 'albums' : selectedKind,
    selectedYear: year,
    query: q,
    artists: selectedKind === 'artists' ? (libraryPage as ArtistLibraryPageResponse | null) : null,
    albums: selectedKind === 'albums' ? (libraryPage as AlbumLibraryPageResponse | null) : null,
    tracks: selectedKind === 'tracks' ? (libraryPage as TrackLibraryPageResponse | null) : null,
    searchResults,
    yearResults,
  })
}

export async function fetchMusicLibraryNextPage(kind: MusicLibraryKind, cursor: string) {
  return fetchLibraryKindPage(kind, 24, cursor)
}

export function useMusicLibraryData(query: MusicLibraryQuery = {}) {
  const key = `music-library-${query.kind ?? 'albums'}-${query.q ?? ''}-${query.year ?? 'all'}`
  return useAsyncData(key, () => fetchMusicLibraryPageData(query))
}
