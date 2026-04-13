import type { BookDetailsResponse, BookEditionModel, BookPageData, BookReadEntryModel, ReadCardDto } from '~/types/books'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'

function mapStatus(status: string) {
  switch (status) {
    case 'READ':
      return 'Concluído'
    case 'CURRENTLY_READING':
      return 'Em leitura'
    case 'WANT_TO_READ':
      return 'Quero ler'
    case 'DID_NOT_FINISH':
      return 'Interrompido'
    case 'PAUSED':
      return 'Pausado'
    default:
      return 'Sem status definido'
  }
}

function buildReadContext(read: ReadCardDto) {
  const parts = [mapStatus(read.status)]

  if (read.progressPct != null) {
    parts.push(`${Math.round(read.progressPct)}%`)
  } else if (read.progressPages != null) {
    parts.push(`${read.progressPages} páginas`)
  }

  return parts.join(' · ')
}

function mapRead(read: ReadCardDto, index: number): BookReadEntryModel {
  const anchorDate = read.finishedAt ?? read.startedAt

  return {
    id: `read-${read.readId}`,
    title: index === 0 ? 'Leitura mais recente' : `Registro ${index + 1}`,
    context: buildReadContext(read),
    meta: anchorDate ? formatAbsoluteDate(anchorDate) : 'Sem data definida',
    relativeDate: anchorDate ? formatRelativeDate(anchorDate) : 'Sem atividade recente',
    source: read.source,
  }
}

function mapEdition(edition: BookDetailsResponse['editions'][number]): BookEditionModel {
  return {
    id: `edition-${edition.id}`,
    title: edition.title || edition.publisher || 'Edição sem título',
    meta: [
      edition.format,
      edition.publisher,
      edition.language,
      edition.pages ? `${edition.pages} páginas` : null,
    ].filter(Boolean) as string[],
  }
}

export function buildBookPageData(book: BookDetailsResponse): BookPageData {
  const authorsLine = book.authors.map((author) => author.name).join(', ') || 'Autoria não informada'
  const latestRead = book.reads[0]
  const latestActivity = latestRead ? formatRelativeDate(latestRead.finishedAt ?? latestRead.startedAt) : 'Sem atividade recente'

  return {
    slug: book.slug,
    title: book.title,
    description: book.description,
    coverUrl: book.coverUrl,
    authorsLine,
    subtitle: book.releaseDate ? `${authorsLine} · ${book.releaseDate.slice(0, 4)}` : authorsLine,
    heroMeta: [
      book.releaseDate ? book.releaseDate.slice(0, 4) : null,
      book.rating != null ? `${book.rating.toFixed(1)} de nota` : null,
      `${book.reads.length} registros`,
      latestActivity ? `Último ${latestActivity}` : null,
    ].filter(Boolean) as string[],
    stats: {
      totalReads: book.reads.length,
      currentStatus: latestRead ? mapStatus(latestRead.status) : 'Sem leitura registrada',
      latestActivity,
      ratingText: book.rating != null ? `${book.rating.toFixed(1)} / 5` : null,
    },
    editions: book.editions.slice(0, 8).map(mapEdition),
    recentReads: book.reads.slice(0, 24).map(mapRead),
    reviewRaw: book.reviewRaw,
  }
}
