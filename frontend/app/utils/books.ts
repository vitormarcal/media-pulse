import type {
  BookCollectionContextMetric,
  BookCollectionData,
  BookDetailsResponse,
  BookEditionModel,
  BookLibraryCardDto,
  BookLibraryCardModel,
  BookLibraryMetric,
  BookLibraryPageData,
  BookLibraryYearChip,
  BookPageData,
  BookReadEntryModel,
  BooksLibraryResponse,
  BooksSearchResponse,
  BooksStatsResponse,
  ReadCardDto,
  YearReadsResponse,
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

function buildAuthorsLine(authors: Array<{ name: string }>) {
  return authors.map((author) => author.name).join(', ') || 'Autoria não informada'
}

function statusBadge(status: string | null) {
  switch (status) {
    case 'READ':
      return 'Fechado'
    case 'CURRENTLY_READING':
      return 'Aberto'
    case 'PAUSED':
      return 'Pausado'
    case 'WANT_TO_READ':
      return 'Na pilha'
    case 'DID_NOT_FINISH':
      return 'Interrompido'
    default:
      return 'Arquivo'
  }
}

function isDormantLibraryBook(book: BookLibraryCardDto) {
  return book.currentStatus === 'WANT_TO_READ' || book.readsCount === 0
}

function progressLabelForLibrary(book: BookLibraryCardDto) {
  if (book.currentStatus === 'WANT_TO_READ') {
    return 'Ainda sem leitura iniciada'
  }

  if (book.currentStatus === 'CURRENTLY_READING' && book.activeProgressPct != null) {
    return `${Math.round(book.activeProgressPct)}% em leitura`
  }

  if (book.readsCount > 0) {
    return `${book.readsCount} registros · ${book.completedCount} concluídos`
  }

  return 'Sem leitura registrada ainda'
}

function buildLibraryCardModel(book: BookLibraryCardDto): BookLibraryCardModel {
  const dormant = isDormantLibraryBook(book)

  return {
    id: `library-${book.bookId}`,
    title: book.title,
    subtitle: buildAuthorsLine(book.authors),
    href: `/books/${book.slug}`,
    imageUrl: book.coverUrl,
    progressLabel: progressLabelForLibrary(book),
    activityLabel:
      dormant
        ? (book.lastActivityAt ? `Entrou na pilha ${formatRelativeDate(book.lastActivityAt)}` : 'Ainda sem passagem pela mesa')
        : (book.lastActivityAt ? `Última atividade ${formatRelativeDate(book.lastActivityAt)}` : 'Sem passagem pela mesa ainda'),
    aside: statusBadge(book.currentStatus),
    isDormant: dormant,
  }
}

function buildSearchCardModel(book: BooksSearchResponse['books'][number]): BookLibraryCardModel {
  return {
    id: `search-${book.bookId}`,
    title: book.title,
    subtitle: buildAuthorsLine(book.authors),
    href: `/books/${book.slug}`,
    imageUrl: book.coverUrl,
    progressLabel: book.reviewedAt ? 'Encontrado pela busca' : 'Entrada do catálogo',
    activityLabel: book.reviewedAt ? `Última atividade ${formatRelativeDate(book.reviewedAt)}` : 'Sem atividade recente visível',
    aside: 'Busca',
  }
}

function buildYearCardModel(read: ReadCardDto, prefix: string): BookLibraryCardModel {
  const timestamp = read.finishedAt ?? read.startedAt

  return {
    id: `${prefix}-${read.readId}`,
    title: read.book.title,
    subtitle: buildAuthorsLine(read.book.authors),
    href: `/books/${read.book.slug}`,
    imageUrl: read.book.coverUrl,
    progressLabel: buildReadContext(read),
    activityLabel: timestamp ? `Atividade ${formatRelativeDate(timestamp)}` : 'Sem data relevante',
    aside: statusBadge(read.status),
  }
}

function buildLibraryMetrics(stats: BooksStatsResponse): BookLibraryMetric[] {
  return [
    {
      id: 'books',
      label: 'Livros no arquivo',
      value: formatShortNumber(stats.total.booksCount),
      note: 'o tamanho bruto da estante já incorporada ao arquivo',
    },
    {
      id: 'reads',
      label: 'Registros acumulados',
      value: formatShortNumber(stats.total.readsCount),
      note: 'toda passagem de leitura já anotada, sem depender do recorte recente',
    },
    {
      id: 'finished',
      label: 'Concluídos acumulados',
      value: formatShortNumber(stats.total.completedCount),
      note: 'o que realmente fechou ao longo do arquivo inteiro',
    },
    {
      id: 'dormant',
      label: 'Ainda quietos',
      value: formatShortNumber(stats.unreadCount),
      note: 'o pedaço da biblioteca que já existe, mas ainda não virou leitura',
    },
  ]
}

function buildYearChips(stats: BooksStatsResponse): BookLibraryYearChip[] {
  return stats.years
    .slice()
    .sort((a, b) => b.year - a.year)
    .slice(0, 12)
    .map((year) => ({
      year: year.year,
      label: String(year.year),
      detail: `${formatShortNumber(year.finishedCount)} concluídos`,
    }))
}

function buildSpotlightFromCard(
  card: BookLibraryCardModel | undefined,
  fallbackTitle: string,
  fallbackNote: string,
) {
  if (!card) return null

  return {
    title: card.title,
    subtitle: card.subtitle,
    imageUrl: card.imageUrl,
    href: card.href,
    meta: card.progressLabel,
    note: card.activityLabel || fallbackNote,
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

export function buildBookLibraryCards(items: BookLibraryCardDto[]): BookLibraryCardModel[] {
  return items.map(buildLibraryCardModel)
}

export function buildBookLibraryPageData(payload: {
  stats: BooksStatsResponse
  library: BooksLibraryResponse
  query: string
  selectedYear: number | null
  searchResults: BooksSearchResponse | null
  yearResults: YearReadsResponse | null
}): BookLibraryPageData {
  const years = buildYearChips(payload.stats)
  const featuredYear = years[0]?.year ?? new Date().getFullYear()

  if (payload.selectedYear && payload.yearResults) {
    const currentItems = payload.yearResults.currentlyReading.map((read) => buildYearCardModel(read, 'current'))
    const finishedItems = payload.yearResults.finished.map((read) => buildYearCardModel(read, 'finished'))
    const pausedItems = payload.yearResults.paused.map((read) => buildYearCardModel(read, 'paused'))
    const wantItems = payload.yearResults.wantToRead.map((read) => buildYearCardModel(read, 'want'))
    const dnfItems = payload.yearResults.didNotFinish.map((read) => buildYearCardModel(read, 'dnf'))

    return {
      hero: {
        title: `A biblioteca de livros em ${payload.selectedYear}`,
        intro: 'Um corte anual da estante para ver o que realmente andou, fechou, pausou ou apenas entrou na pilha durante esse período.',
        backLink: '/books',
        backLabel: 'Voltar ao recorte',
        accentLink: '/books/library',
        accentLabel: 'Ver biblioteca inteira',
        spotlight: buildSpotlightFromCard(
          currentItems[0] ?? finishedItems[0] ?? pausedItems[0] ?? wantItems[0] ?? dnfItems[0],
          `A biblioteca de livros em ${payload.selectedYear}`,
          'O primeiro ponto de entrada para esse recorte anual.',
        ),
      },
      filters: {
        query: payload.query,
        selectedYear: payload.selectedYear,
        years,
      },
      context: {
        eyebrow: 'Ano',
        title: `O que ${payload.selectedYear} concentrou`,
        description: 'Uma leitura do ano como memória de estante, não como relatório frio.',
        summary: `${formatShortNumber(payload.yearResults.stats.finishedCount)} concluídos e ${formatShortNumber(payload.yearResults.stats.currentlyReadingCount)} leituras em curso apareceram nesse recorte.`,
        metrics: [
          {
            id: 'year-finished',
            label: 'Concluídos no ano',
            value: formatShortNumber(payload.yearResults.stats.finishedCount),
            note: 'o que de fato saiu da pilha naquele período',
          },
          {
            id: 'year-reading',
            label: 'Em leitura no ano',
            value: formatShortNumber(payload.yearResults.stats.currentlyReadingCount),
            note: 'livros que passaram pelo ano sem necessariamente fechar',
          },
          {
            id: 'year-paused',
            label: 'Pausados no ano',
            value: formatShortNumber(payload.yearResults.stats.pausedCount),
            note: 'leituras que perderam ritmo naquele recorte',
          },
          {
            id: 'year-pages',
            label: 'Páginas fechadas',
            value: payload.yearResults.stats.pagesFinished != null ? formatShortNumber(payload.yearResults.stats.pagesFinished) : 'Sem total',
            note: 'uma medida bruta do volume concluído quando as edições permitiram contar',
          },
        ],
      },
      sections: [
        {
          id: 'year-current',
          eyebrow: 'Em curso',
          title: 'Os que seguiram abertos nesse ano',
          description: 'Leituras que permaneceram na mesa em algum ponto do recorte.',
          summary: 'Mais permanência do que fechamento.',
          items: currentItems,
          emptyMessage: 'Nenhuma leitura em curso apareceu nesse ano.',
        },
        {
          id: 'year-finished',
          eyebrow: 'Concluídos',
          title: 'Os que realmente fecharam no período',
          description: 'O miolo do recorte anual quando a pergunta é o que saiu da pilha.',
          summary: 'Fechamento antes de catálogo.',
          items: finishedItems,
          emptyMessage: 'Nenhum livro foi concluído nesse ano.',
        },
        {
          id: 'year-paused',
          eyebrow: 'Pausados e interrompidos',
          title: 'O que perdeu ritmo no caminho',
          description: 'Leituras que ficaram em suspenso ou foram abandonadas nesse recorte.',
          summary: 'Ausência também conta a história do ano.',
          items: [...pausedItems, ...dnfItems],
          emptyMessage: 'Nada ficou em suspenso nesse ano.',
        },
        {
          id: 'year-want',
          eyebrow: 'Entrou na pilha',
          title: 'O que apareceu como intenção de leitura',
          description: 'Livros que passaram a existir na estante mental mesmo sem terem sido abertos.',
          summary: 'Catálogo e desejo também entram no retrato anual.',
          items: wantItems,
          emptyMessage: 'Nenhum título entrou na pilha nesse ano.',
        },
      ],
      libraryCursor: null,
      mode: 'year',
    }
  }

  if (payload.query && payload.searchResults) {
    const searchItems = payload.searchResults.books.map(buildSearchCardModel)

    return {
      hero: {
        title: 'A biblioteca de livros, puxada pela busca',
        intro: 'Quando você já sabe o que está tentando reencontrar, a página vira arquivo de consulta sem perder a mesma superfície editorial.',
        backLink: '/books',
        backLabel: 'Voltar ao recorte',
        accentLink: '/books/library',
        accentLabel: 'Limpar busca',
        spotlight: buildSpotlightFromCard(
          searchItems[0],
          'A biblioteca de livros, puxada pela busca',
          'O primeiro resultado vira a porta de entrada visual deste recorte.',
        ),
      },
      filters: {
        query: payload.query,
        selectedYear: payload.selectedYear,
        years,
      },
      context: {
        eyebrow: 'Busca',
        title: 'A estante inteira, afunilada pelo título',
        description: 'Sem esconder o resto da biblioteca; só aproximando o que você quer achar agora.',
        summary: `${formatShortNumber(payload.searchResults.books.length)} livros encontrados para "${payload.query}".`,
        metrics: buildLibraryMetrics(payload.stats),
      },
      sections: [
        {
          id: 'search-results',
          eyebrow: 'Resultados',
          title: 'O que respondeu à busca',
          description: 'Uma prateleira curta para ir direto ao que interessa.',
          summary: 'Busca primeiro, contexto depois.',
          items: searchItems,
          emptyMessage: 'Nada apareceu para essa busca.',
        },
      ],
      libraryCursor: null,
      mode: 'search',
    }
  }

  const libraryItems = payload.library.items.map(buildLibraryCardModel)
  const activeItems = libraryItems.filter(item => !item.isDormant)
  const dormantItems = libraryItems.filter(item => item.isDormant)

  return {
    hero: {
      title: 'A biblioteca inteira de livros',
      intro: 'O arquivo completo para quando a mesa do momento já não basta e você quer atravessar a estante inteira com mais calma.',
      backLink: '/books',
      backLabel: 'Voltar ao recorte',
      accentLink: `/books/library?year=${featuredYear}`,
      accentLabel: 'Abrir um recorte por ano',
      spotlight: buildSpotlightFromCard(
        activeItems[0] ?? dormantItems[0],
        'A biblioteca inteira de livros',
        'Um ponto de entrada imagético para atravessar o arquivo.',
      ),
    },
    filters: {
      query: payload.query,
      selectedYear: payload.selectedYear,
      years,
    },
    context: {
      eyebrow: 'Arquivo',
      title: 'A estante inteira vista pelo estado atual',
      description: 'Uma visão larga da biblioteca, agora ancorada no arquivo inteiro e não só no recorte recente.',
      summary: `${formatShortNumber(payload.stats.total.booksCount)} livros no arquivo, ${formatShortNumber(payload.stats.total.readsCount)} registros acumulados e ${formatShortNumber(payload.stats.unreadCount)} ainda esperando a primeira leitura.`,
      metrics: buildLibraryMetrics(payload.stats),
    },
    sections: [
      {
        id: 'active-library',
        eyebrow: 'Com rastro',
        title: 'Os livros que já deixaram marca',
        description: 'Os que já têm algum histórico e por isso funcionam melhor como entrada para a estante.',
        summary: 'É o arquivo inteiro, mas começando pelos que já têm memória associada.',
        items: activeItems,
        emptyMessage: 'Ainda não há livros com atividade registrada.',
      },
      {
        id: 'dormant-library',
        eyebrow: 'Ainda quietos',
        title: 'O que ainda espera a primeira leitura',
        description: 'Parte da biblioteca que já existe no arquivo, mas ainda não virou passagem de leitura.',
        summary: 'Mais catálogo do que hábito, por enquanto.',
        items: dormantItems,
        emptyMessage: 'Nada ficou sem leitura na biblioteca atual.',
      },
    ],
    libraryCursor: payload.library.nextCursor,
    mode: 'library',
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
