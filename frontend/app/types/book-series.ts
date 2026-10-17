export type BookGroupKind = 'series' | 'universes'
export interface BookGroup {
  id: number
  name: string
  itemCount: number
}
export interface BookGroupMember {
  id: number
  name: string
  position: number
  slug: string | null
  coverUrl: string | null
}
export interface BookGroupDetails {
  id: number
  name: string
  items: BookGroupMember[]
  universes: BookGroup[]
}
export interface BookSeriesSuggestion {
  hardcoverId: number
  name: string
  books: BookGroupMember[]
}
