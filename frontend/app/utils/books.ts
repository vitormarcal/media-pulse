import type {
  BookCollectionContextMetric,
  BookCollectionData,
  BookDetailsResponse,
  BookEditionModel,
  BookPageData,
  BookReadEntryModel,
  ReadCardDto,
} from '~/types/books'
import type { BooksListResponse, BooksSummaryResponse, EditorialHighlight, EditorialShelfItem } from '~/types/home'
import { formatAbsoluteDate, formatRelativeDate, formatShortNumber } from '~/utils/formatting'

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

function bookToShelfItem(read: ReadCardDto): EditorialShelfItem {
  const timestamp = read.finishedAt ?? read.startedAt
  const authors = read.book.authors.map((author) => author.name).join(', ')

  let meta = mapStatus(read.status)

  if (read.progressPct != null) {
    meta = `${meta} · ${Math.round(read.progressPct)}%`
  } else if (read.progressPages != null) {
    meta = `${meta} · ${read.progressPages} páginas`
  }

  return {
    id: `book-${read.readId}`,
    type: 'book',
    title: read.book.title,
    subtitle: authors || 'Autoria não informada',
    imageUrl: read.book.coverUrl,
    href: `/books/${read.book.slug}`,
    meta,
    detail: timestamp ? formatRelativeDate(timestamp) : 'Sem data registrada',
    timestamp,
  }
}

function sortByTimestamp<T extends { timestamp?: string | null }>(items: T[]) {
  return [...items].sort((a, b) => {
    const aTime = a.timestamp ? new Date(a.timestamp).getTime() : 0
    const bTime = b.timestamp ? new Date(b.timestamp).getTime() : 0
    return bTime - aTime
  })
}

function toHighlight(item: EditorialShelfItem): EditorialHighlight {
  return {
    id: item.id,
    type: item.type,
    title: item.title,
    subtitle: item.subtitle,
    eyebrow: 'Livro',
    imageUrl: item.imageUrl,
    href: item.href,
    timestamp: item.timestamp ?? new Date().toISOString(),
    meta: `${item.meta} · ${item.detail}`,
  }
}

function buildContextMetrics(payload: {
  summary: BooksSummaryResponse
  readingBooks: BooksListResponse
  pausedBooks: BooksListResponse
  finishedBooks: BooksListResponse
}): BookCollectionContextMetric[] {
  const topAuthor = payload.summary.topAuthors[0]

  return [
    {
      id: 'reading',
      label: 'Leituras em curso',
      value: formatShortNumber(payload.summary.counts.reading),
      note: 'o pedaço da estante que ainda está pedindo retorno',
    },
    {
      id: 'finished',
      label: 'Concluídos no recorte',
      value: formatShortNumber(payload.summary.counts.finished),
      note: 'o que realmente saiu da pilha mental nesse período',
    },
    {
      id: 'paused',
      label: 'Pausados agora',
      value: formatShortNumber(payload.summary.counts.paused),
      note: 'leituras que seguem por perto, mas perderam ritmo por enquanto',
    },
    {
      id: 'author',
      label: 'Autor mais presente',
      value: topAuthor ? topAuthor.authorName : 'Sem destaque',
      note: topAuthor ? `${formatShortNumber(topAuthor.finishedCount)} fechamentos no recorte` : 'ainda sem concentração suficiente para destacar alguém',
    },
  ]
}

export function buildBookCollectionData(payload: {
  summary: BooksSummaryResponse
  readingBooks: BooksListResponse
  pausedBooks: BooksListResponse
  finishedBooks: BooksListResponse
}): BookCollectionData {
  const inProgress = [
    ...payload.readingBooks.items.map(bookToShelfItem),
    ...payload.pausedBooks.items.map(bookToShelfItem),
  ].slice(0, 8)

  const recentFinishes = sortByTimestamp(payload.finishedBooks.items.map(bookToShelfItem)).slice(0, 18)
  const heroCandidates = sortByTimestamp([
    ...inProgress,
    ...recentFinishes,
  ])

  return {
    generatedAt: new Date().toISOString(),
    hero: {
      title: 'Os livros que ainda seguem puxando você de volta',
      intro:
        'Um recorte do que continua ocupando espaço na mesa: o que ainda está em curso, o que acabou de fechar e o que continua merecendo ficar à vista.',
      lead: heroCandidates[0] ? toHighlight(heroCandidates[0]) : null,
      supporting: heroCandidates.slice(1, 5).map(toHighlight),
    },
    inProgress,
    recentFinishes,
    context: {
      eyebrow: 'Recorte do mês',
      title: 'O tamanho dessa mesa de leitura',
      description: 'Não para resumir a estante inteira, só para situar o volume e lembrar o que ainda continua em volta.',
      summary: `${formatShortNumber(payload.summary.counts.reading)} leituras em curso e ${formatShortNumber(payload.summary.counts.finished)} livros concluídos formam o recorte recente.`,
      metrics: buildContextMetrics(payload),
    },
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
