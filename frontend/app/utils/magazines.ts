import type { MagazineIssue, MagazineReadRequest, MagazineReadStatus } from '~/types/magazines'
import type { EditorialHighlight, EditorialShelfItem } from '~/types/home'
import type { BookLibraryCardModel } from '~/types/books'

export const magazineStates: Record<MagazineReadStatus, string> = {
  WANT_TO_READ: 'Quero ler',
  CURRENTLY_READING: 'Lendo',
  READ: 'Lida',
  DID_NOT_FINISH: 'Abandonada',
}
export function localToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
export function magazineDate(value: string | null | undefined) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('pt-BR').format(new Date(`${value}T12:00:00`))
}
export function magazineNumber(issue: MagazineIssue) {
  return [
    issue.number ? `Nº ${issue.number}` : null,
    issue.coverDate ? issue.coverDate.split('-').reverse().join('/') : null,
  ]
    .filter(Boolean)
    .join(' · ')
}
export function magazineCard(issue: MagazineIssue): BookLibraryCardModel {
  return {
    id: String(issue.id),
    title: issue.publication.name,
    subtitle: magazineNumber(issue),
    href: `/magazines/${issue.id}`,
    imageUrl: issue.coverUrl,
    progressLabel: issue.latestRead ? magazineStates[issue.latestRead.status] : 'Sem leitura',
    activityLabel: magazineDate(issue.activityDate),
    aside: issue.latestRead ? `${Math.round(issue.latestRead.progressPct)}%` : '',
  }
}
export function magazineError(error: unknown) {
  const response = error as { data?: { detail?: string }; message?: string }
  return response.data?.detail || response.message || 'Não foi possível salvar. Tente novamente.'
}
export function readPayload(read: MagazineReadRequest): MagazineReadRequest {
  if (read.status === 'WANT_TO_READ') return { status: read.status }
  return {
    status: read.status,
    startedAt: read.startedAt || null,
    finishedAt: read.status === 'READ' ? read.finishedAt || null : null,
    ...(read.status === 'READ'
      ? {}
      : typeof read.currentPage === 'number'
        ? { currentPage: read.currentPage }
        : { progressPct: typeof read.progressPct === 'number' ? read.progressPct : 0 }),
  }
}

export function magazineShelf(issue: MagazineIssue): EditorialShelfItem {
  const card = magazineCard(issue)
  return {
    id: card.id,
    type: 'magazine',
    title: card.title,
    subtitle: card.subtitle,
    imageUrl: card.imageUrl,
    href: card.href,
    meta: card.progressLabel,
    detail: card.aside,
    timestamp: issue.activityDate,
  }
}
export function magazineHighlight(issue: MagazineIssue): EditorialHighlight {
  const card = magazineCard(issue)
  return {
    id: card.id,
    type: 'magazine',
    title: card.title,
    subtitle: card.subtitle,
    imageUrl: card.imageUrl,
    href: card.href,
    eyebrow: card.progressLabel,
    timestamp: issue.activityDate,
    meta: card.activityLabel,
  }
}
