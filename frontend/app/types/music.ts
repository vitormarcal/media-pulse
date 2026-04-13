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
