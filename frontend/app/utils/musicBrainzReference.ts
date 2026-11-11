export interface MusicBrainzAlbumReference {
  mbid: string
  entityType: 'release-group' | 'release'
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
export function parseMusicBrainzAlbumReference(value: string): MusicBrainzAlbumReference | null {
  const input = value.trim()
  if (uuid.test(input)) return { mbid: input.toLowerCase(), entityType: 'release-group' }
  try {
    const url = new URL(input)
    if (
      !['https:', 'http:'].includes(url.protocol) ||
      !['musicbrainz.org', 'www.musicbrainz.org'].includes(url.hostname) ||
      url.username ||
      url.password ||
      url.port
    )
      return null
    const match = url.pathname.match(/^\/(release-group|release)\/([^/]+)\/?$/)
    if (!match || !uuid.test(match[2]!)) return null
    return { entityType: match[1] as MusicBrainzAlbumReference['entityType'], mbid: match[2]!.toLowerCase() }
  } catch {
    return null
  }
}
