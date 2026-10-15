import type { AlbumArtistCredit } from '~/types/music'

export function albumArtistLabel(artists: AlbumArtistCredit[] | undefined, fallback = ''): string {
  return artists?.length ? artists.map((artist) => artist.creditedName + artist.joinPhrase).join('') : fallback
}
