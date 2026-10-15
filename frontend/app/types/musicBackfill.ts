import type { AlbumArtistCredit, MusicSearchResponse } from '~/types/music'

export type AlbumCreditBackfillStatus =
  | 'PENDING'
  | 'READY'
  | 'SKIPPED'
  | 'ERROR'
  | 'APPLIED'
  | 'CONFLICT'
  | 'UNDONE'
  | 'UNDO_CONFLICT'

export interface AlbumCreditBackfillArtist {
  externalId: string
  name: string
  creditedName: string
  joinPhrase: string
}

export interface AlbumCreditBackfillItem {
  albumId: number
  albumTitle: string
  status: AlbumCreditBackfillStatus
  reason: string | null
  before: AlbumArtistCredit[]
  hadStoredCredits: boolean
  beforeRevision: number
  provider: 'SPOTIFY' | 'MUSICBRAINZ_CONFIRMED' | null
  providerId: string | null
  proposed: AlbumCreditBackfillArtist[]
  after: AlbumArtistCredit[] | null
  afterRevision: number | null
}

export interface AlbumCreditBackfillBatch {
  batchId: string
  createdAt: string
  items: AlbumCreditBackfillItem[]
}

export type BackfillSearchAlbum = MusicSearchResponse['albums'][number]

export const backfillStatusLabels: Record<AlbumCreditBackfillStatus, string> = {
  PENDING: 'Consulta pendente',
  READY: 'Pronto para aplicar',
  SKIPPED: 'Preservado',
  ERROR: 'Falha',
  APPLIED: 'Aplicado',
  CONFLICT: 'Precisa de revisão',
  UNDONE: 'Desfeito',
  UNDO_CONFLICT: 'Desfazimento recusado',
}
