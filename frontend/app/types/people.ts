export type PersonHistoryCategory = 'CAST' | 'DIRECTING' | 'WRITING'

export interface PersonHistoryItemDto {
  personId: number
  name: string
  slug: string
  profileUrl: string | null
  watchedWorksCount: number
}

export interface PersonHistoryPageResponse {
  items: PersonHistoryItemDto[]
  nextOffset: number | null
}
