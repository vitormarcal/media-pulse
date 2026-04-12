import type { AlbumPageData, AlbumPageResponse, AlbumTrackModel } from '~/types/music'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'

function trackPosition(track: AlbumPageResponse['tracks'][number]) {
  const disc = track.discNumber != null ? `${track.discNumber}.` : ''
  const number = track.trackNumber != null ? String(track.trackNumber).padStart(2, '0') : '00'
  return `${disc}${number}`
}

function mapTrack(track: AlbumPageResponse['tracks'][number]): AlbumTrackModel {
  return {
    id: `track-${track.trackId}`,
    title: track.title,
    position: trackPosition(track),
    meta: `${track.playCount} plays`,
    lastPlayed: track.lastPlayed ? formatRelativeDate(track.lastPlayed) : 'Ainda sem play',
  }
}

export function buildAlbumPageData(album: AlbumPageResponse): AlbumPageData {
  return {
    id: String(album.albumId),
    title: album.albumTitle,
    artistName: album.artistName,
    year: album.year,
    coverUrl: album.coverUrl,
    heroMeta: [
      album.year ? String(album.year) : null,
      album.artistName,
      `${album.totalPlays} plays`,
      album.lastPlayed ? `Último ${formatRelativeDate(album.lastPlayed)}` : 'Sem play recente',
    ].filter(Boolean) as string[],
    stats: {
      totalPlays: album.totalPlays,
      tracksCount: album.tracks.length,
      latestPlay: album.lastPlayed ? formatRelativeDate(album.lastPlayed) : 'Sem play recente',
      latestPlayAbsolute: album.lastPlayed ? formatAbsoluteDate(album.lastPlayed) : null,
    },
    tracks: album.tracks.map(mapTrack),
    recentDays: album.playsByDay.slice(-12).map((day) => ({
      id: day.day,
      label: day.day,
      plays: day.plays,
    })),
  }
}
