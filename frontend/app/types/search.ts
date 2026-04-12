export type SearchResultKind = 'show' | 'movie' | 'book' | 'album' | 'artist' | 'track' | 'author'

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

export interface SearchResultItem {
  id: string
  kind: SearchResultKind
  title: string
  subtitle: string
  href: string | null
}

export interface GlobalSearchData {
  groups: Array<{
    id: string
    title: string
    items: SearchResultItem[]
  }>
  total: number
}
