export type BookGroupKind = 'series' | 'universes'
export interface BookSeriesAuthor {
  id: number
  name: string
}
export interface BookGroup {
  id: number
  name: string
  itemCount: number
  description: string | null
  authors: BookSeriesAuthor[]
  coverUrls: string[]
  hardcoverId: number | null
}
export interface BookGroupMember {
  id: number
  name: string
  position: number
  slug: string | null
  coverUrl: string | null
}
export interface BookSeriesMetadata {
  slug: string | null
  primaryAuthor: BookSeriesAuthor | null
  isCompleted: boolean | null
  booksCount: number | null
  primaryBooksCount: number | null
}
export interface BookGroupDetails extends Omit<BookGroup, 'itemCount'> {
  items: BookGroupMember[]
  universes: BookGroup[]
  hardcover: BookSeriesMetadata
}
export interface BookSeriesSuggestion {
  hardcoverId: number
  name: string
  books: BookGroupMember[]
  description: string | null
  hardcover: BookSeriesMetadata
}
export type BookSeriesMetadataField =
  | 'NAME'
  | 'DESCRIPTION'
  | 'PRIMARY_AUTHOR'
  | 'PUBLICATION_STATUS'
  | 'COUNTS'
  | 'EXTERNAL_SLUG'
export interface BookSeriesMetadataPreview {
  current: BookGroupDetails
  suggested: BookSeriesSuggestion
}
