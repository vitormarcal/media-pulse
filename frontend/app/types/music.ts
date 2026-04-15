import type { EditorialHighlight, EditorialShelfItem } from '~/types/home'

export interface AlbumTrackRow {
  trackId: number
  title: string
  discNumber: number | null
  trackNumber: number | null
  playCount: number
  lastPlayed: string | null
}

export interface PlaysByDayRow {
  day: string
  plays: number
}

export interface AlbumPageResponse {
  albumId: number
  albumTitle: string
  artistId: number
  artistName: string
  year: number | null
  coverUrl: string | null
  lastPlayed: string | null
  totalPlays: number
  tracks: AlbumTrackRow[]
  playsByDay: PlaysByDayRow[]
}

export interface ArtistPageResponse {
  artistId: number
  artistName: string
  totalPlays: number
  uniqueTracksPlayed: number
  uniqueAlbumsPlayed: number
  libraryAlbumsCount: number
  libraryTracksCount: number
  lastPlayed: string | null
  albums: Array<{
    albumId: number
    albumTitle: string
    year: number | null
    coverUrl: string | null
    totalTracks: number
    playedTracks: number
    playCount: number
    lastPlayed: string | null
  }>
  topTracks: Array<{
    trackId: number
    title: string
    albumId: number | null
    albumTitle: string | null
    playCount: number
    lastPlayed: string | null
  }>
  playsByDay: PlaysByDayRow[]
}

export interface TopArtistResponse {
  artistId: number
  artistName: string
  playCount: number
  albumId: number | null
  albumTitle: string | null
  coverUrl: string | null
}

export interface TopAlbumResponse {
  albumId: number
  albumTitle: string
  artistId: number
  artistName: string
  playCount: number
}

export interface TopTrackResponse {
  trackId: number
  title: string
  albumId: number
  albumTitle: string
  artistId: number
  artistName: string
  playCount: number
}

export interface ArtistLibraryRow {
  artistId: number
  artistName: string
  coverUrl: string | null
  totalPlays: number
  albumsCount: number
  tracksCount: number
  lastPlayed: string | null
}

export interface ArtistLibraryPageResponse {
  items: ArtistLibraryRow[]
  nextCursor: string | null
}

export interface AlbumLibraryRow {
  albumId: number
  albumTitle: string
  artistId: number
  artistName: string
  coverUrl: string | null
  year: number | null
  totalTracks: number
  playedTracks: number
  playCount: number
  lastPlayed: string | null
}

export interface AlbumLibraryPageResponse {
  items: AlbumLibraryRow[]
  nextCursor: string | null
}

export interface TrackLibraryRow {
  trackId: number
  title: string
  artistId: number
  artistName: string
  albumId: number | null
  albumTitle: string | null
  coverUrl: string | null
  totalPlays: number
  lastPlayed: string | null
}

export interface TrackLibraryPageResponse {
  items: TrackLibraryRow[]
  nextCursor: string | null
}

export interface MusicSearchResponse {
  artists: Array<{
    id: number
    name: string
  }>
  albums: Array<{
    id: number
    title: string
    artistName: string
    year: number | null
  }>
  tracks: Array<{
    id: number
    title: string
    artistName: string
    albumTitle: string
  }>
}

export interface MusicByYearResponse {
  year: number
  range: {
    start: string
    end: string
  }
  stats: {
    playsCount: number
    uniqueArtistsCount: number
    uniqueAlbumsCount: number
    uniqueTracksCount: number
  }
  albums: AlbumLibraryRow[]
  artists: TopArtistResponse[]
  tracks: TopTrackResponse[]
}

export interface MusicStatsResponse {
  total: {
    playsCount: number
    uniqueArtistsCount: number
    uniqueAlbumsCount: number
    uniqueTracksCount: number
  }
  years: Array<{
    year: number
    playsCount: number
    uniqueArtistsCount: number
    uniqueAlbumsCount: number
    uniqueTracksCount: number
  }>
  latestPlayAt: string | null
  firstPlayAt: string | null
}

export interface DuplicateTrackCandidateResponse {
  trackId: number
  title: string
  durationMs: number | null
  discNumber: number | null
  trackNumber: number | null
  playbackCount: number
  lastPlayed: string | null
  hasMusicBrainz: boolean
  hasSpotify: boolean
  externalIdentifiers: string[]
}

export interface DuplicateTrackGroupResponse {
  albumId: number
  albumTitle: string
  albumYear: number | null
  albumCoverUrl: string | null
  artistId: number
  artistName: string
  groupKey: string
  normalizedTitle: string
  ignored: boolean
  confidence: string
  suggestionReason: string
  suggestedTargetTrackId: number
  candidates: DuplicateTrackCandidateResponse[]
}

export interface DuplicateTrackReviewPageResponse {
  items: DuplicateTrackGroupResponse[]
  nextCursor: string | null
}

export interface DuplicateTrackMergeResponse {
  albumId: number
  groupKey: string
  targetTrackId: number
  mergedTrackIds: number[]
  deletedDuplicatePlaybacks: number
  movedPlaybacks: number
  linkedExternalIdentifiers: number
  migratedAlbumLinks: number
}

export interface DuplicateTrackBatchMergeResponse {
  processedGroups: number
  results: DuplicateTrackMergeResponse[]
}

export interface AlbumTrackModel {
  id: string
  title: string
  position: string
  meta: string
  lastPlayed: string
}

export interface AlbumDayModel {
  id: string
  label: string
  plays: number
}

export interface AlbumPageData {
  id: string
  title: string
  artistName: string
  artistHref: string
  year: number | null
  coverUrl: string | null
  heroMeta: string[]
  stats: {
    totalPlays: number
    tracksCount: number
    latestPlay: string
    latestPlayAbsolute: string | null
  }
  tracks: AlbumTrackModel[]
  recentDays: AlbumDayModel[]
}

export interface ArtistTrackModel {
  id: string
  title: string
  albumTitle: string | null
  albumHref: string | null
  meta: string
  lastPlayed: string
}

export interface ArtistPageData {
  id: string
  title: string
  coverUrl: string | null
  heroMeta: string[]
  stats: {
    totalPlays: number
    libraryAlbumsCount: number
    libraryTracksCount: number
    uniqueAlbumsPlayed: number
    uniqueTracksPlayed: number
    latestPlay: string
    latestPlayAbsolute: string | null
  }
  albums: MusicLibraryCardModel[]
  topTracks: ArtistTrackModel[]
  recentDays: AlbumDayModel[]
}

export interface MusicCollectionContextMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface MusicCollectionData {
  generatedAt: string
  hero: {
    title: string
    intro: string
    lead: EditorialHighlight | null
    supporting: EditorialHighlight[]
  }
  featuredAlbums: EditorialShelfItem[]
  topArtists: EditorialShelfItem[]
  topTracks: EditorialShelfItem[]
  discoveryAlbums: EditorialShelfItem[]
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: MusicCollectionContextMetric[]
  }
}

export type MusicLibraryKind = 'artists' | 'albums' | 'tracks'

export interface MusicLibraryMetric {
  id: string
  label: string
  value: string
  note: string
}

export interface MusicLibraryTab {
  kind: MusicLibraryKind
  label: string
  summary: string
}

export interface MusicLibraryYearChip {
  year: number
  label: string
  detail: string
}

export interface MusicLibraryCardModel {
  id: string
  kind: MusicLibraryKind
  title: string
  subtitle: string
  href: string | null
  imageUrl: string | null
  primaryMeta: string
  secondaryMeta: string
  aside: string
  isDormant?: boolean
}

export interface MusicLibraryPageData {
  hero: {
    title: string
    intro: string
    backLink: string
    backLabel: string
    accentLink: string
    accentLabel: string
    spotlight: {
      title: string
      subtitle: string
      imageUrl: string | null
      href: string | null
      meta: string
      note: string
    } | null
  }
  filters: {
    query: string
    selectedKind: MusicLibraryKind
    selectedYear: number | null
    tabs: MusicLibraryTab[]
    years: MusicLibraryYearChip[]
  }
  context: {
    eyebrow: string
    title: string
    description: string
    summary: string
    metrics: MusicLibraryMetric[]
  }
  sections: Array<{
    id: string
    eyebrow: string
    title: string
    description: string
    summary: string
    items: MusicLibraryCardModel[]
    emptyMessage?: string
  }>
  libraryCursor: string | null
  mode: 'library' | 'search' | 'year'
}
