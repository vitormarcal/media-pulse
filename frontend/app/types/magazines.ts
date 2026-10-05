import type { MediaCommentDto } from './comments'

export type MagazineReadStatus = 'WANT_TO_READ' | 'CURRENTLY_READING' | 'READ' | 'DID_NOT_FINISH'
export interface MagazinePublication {
  id: number
  name: string
  issn: string | null
  numbersCount: number
}
export interface MagazineReadRequest {
  status: MagazineReadStatus
  startedAt?: string | null
  finishedAt?: string | null
  progressPct?: number | null
  currentPage?: number | null
}
export interface MagazineRead extends MagazineReadRequest {
  id: number
  issueId: number
  progressPct: number
  createdAt: string
}
export interface MagazineIssue {
  id: number
  publication: MagazinePublication
  number: string | null
  coverDate: string | null
  totalPages: number | null
  coverUrl: string | null
  activityDate: string
  latestRead: MagazineRead | null
}
export interface MagazineDetails {
  issue: MagazineIssue
  reads: MagazineRead[]
  comments: MediaCommentDto[]
}
export interface MagazineLibrary {
  items: MagazineIssue[]
  nextPage: number | null
}
export interface MagazineOverview {
  inProgress: MagazineIssue[]
  recent: { issue: MagazineIssue; read: MagazineRead }[]
  numbersCount: number
  completedReadsCount: number
}
