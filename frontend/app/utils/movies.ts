import type {
  MovieCollectionContextMetric,
  MovieCollectionData,
  MovieCollectionsIndexPageData,
  MovieCollectionSummaryDto,
  MovieCompanyDetailsResponse,
  MovieLibraryCardDto,
  MovieLibraryCardModel,
  MovieLibraryMetric,
  MovieListDetailsResponse,
  MovieListSummaryDto,
  MovieListsIndexPageData,
  MoviePersonDetailsResponse,
  MovieLibraryPageData,
  MovieDetailsResponse,
  MoviesByYearResponse,
  MoviesLibraryResponse,
  MoviesSearchResponse,
  MoviePageData,
  MoviePersonCreditDto,
  MoviesStatsResponse,
  MovieWatchEntryModel,
} from '~/types/movies'
import type { EditorialHighlight, EditorialShelfItem, MoviesRecentResponse, MoviesSummaryResponse } from '~/types/home'
import { formatAbsoluteDate, formatRelativeDate, formatShortNumber } from '~/utils/formatting'

function mapWatch(watch: MovieDetailsResponse['watches'][number], index: number): MovieWatchEntryModel {
  return {
    id: `watch-${watch.watchId}`,
    watchId: watch.watchId,
    title: index === 0 ? 'Última sessão registrada' : `Sessão ${index + 1}`,
    meta: formatAbsoluteDate(watch.watchedAt),
    relativeWatchedAt: formatRelativeDate(watch.watchedAt),
    source: watch.source,
  }
}

function recentMovieToShelfItem(movie: MoviesRecentResponse['items'][number]): EditorialShelfItem {
  return {
    id: `movie-${movie.movieId}`,
    type: 'movie',
    title: movie.title,
    subtitle: movie.year ? String(movie.year) : movie.originalTitle || 'Filme',
    imageUrl: movie.coverUrl,
    href: movie.slug ? `/movies/${movie.slug}` : null,
    meta: movie.originalTitle && movie.originalTitle !== movie.title ? movie.originalTitle : 'Sessão registrada',
    detail: movie.watchedAt ? formatRelativeDate(movie.watchedAt) : 'Sem data recente',
    timestamp: movie.watchedAt,
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
    eyebrow: 'Filme',
    imageUrl: item.imageUrl,
    href: item.href,
    timestamp: item.timestamp ?? new Date().toISOString(),
    meta: `${item.meta} · ${item.detail}`,
  }
}

function movieHref(slug: string | null) {
  return slug ? `/movies/${slug}` : null
}

function formatMovieSubtitle(title: string, originalTitle: string, year: number | null) {
  if (originalTitle && originalTitle !== title && year) {
    return `${originalTitle} · ${year}`
  }

  if (originalTitle && originalTitle !== title) {
    return originalTitle
  }

  return year ? String(year) : 'Filme'
}

function buildLibraryCardModel(movie: MovieLibraryCardDto): MovieLibraryCardModel {
  return {
    id: `library-${movie.movieId}`,
    movieId: movie.movieId,
    title: movie.title,
    subtitle: formatMovieSubtitle(movie.title, movie.originalTitle, movie.year),
    href: movieHref(movie.slug),
    imageUrl: movie.coverUrl,
    sessionsLabel: movie.watchCount > 0 ? `${movie.watchCount} sessões registradas` : 'Sem sessão registrada ainda',
    activityLabel: movie.lastWatchedAt
      ? `Última sessão ${formatRelativeDate(movie.lastWatchedAt)}`
      : 'Sem passagem pela tela ainda',
    aside: movie.watchCount > 1 ? 'Retornou' : movie.watchCount === 1 ? 'Visto' : 'Intocado',
    isDormant: !movie.lastWatchedAt,
  }
}

function uniquePeopleById(items: MoviePersonCreditDto[]) {
  const seen = new Set<number>()
  return items.filter((item) => {
    if (seen.has(item.personId)) return false
    seen.add(item.personId)
    return true
  })
}

function buildSearchCardModel(movie: MoviesSearchResponse['movies'][number]): MovieLibraryCardModel {
  return {
    id: `search-${movie.movieId}`,
    title: movie.title,
    subtitle: formatMovieSubtitle(movie.title, movie.originalTitle, movie.year),
    href: movieHref(movie.slug),
    imageUrl: movie.coverUrl,
    sessionsLabel: movie.watchedAt ? 'Encontrado pela busca' : 'Sem sessão recente no índice',
    activityLabel: movie.watchedAt ? `Último registro ${formatRelativeDate(movie.watchedAt)}` : 'Sem registro recente',
    aside: 'Busca',
  }
}

function buildWatchedYearCardModel(movie: MoviesByYearResponse['watched'][number]): MovieLibraryCardModel {
  return {
    id: `year-watched-${movie.movieId}`,
    title: movie.title,
    subtitle: formatMovieSubtitle(movie.title, movie.originalTitle, movie.year),
    href: movieHref(movie.slug),
    imageUrl: movie.coverUrl,
    sessionsLabel: `${movie.watchCountInYear} sessões no ano`,
    activityLabel: `Última ${formatRelativeDate(movie.lastWatchedAt)}`,
    aside: 'Visto no ano',
  }
}

function buildUnwatchedYearCardModel(movie: MoviesByYearResponse['unwatched'][number]): MovieLibraryCardModel {
  return {
    id: `year-unwatched-${movie.movieId}`,
    title: movie.title,
    subtitle: formatMovieSubtitle(movie.title, movie.originalTitle, movie.year),
    href: movieHref(movie.slug),
    imageUrl: movie.coverUrl,
    sessionsLabel: 'Sem sessão nesse recorte',
    activityLabel: 'Ficou fora desse ano',
    aside: 'Sem sessão',
    isDormant: true,
  }
}

function buildStatsMetrics(stats: MoviesStatsResponse): MovieLibraryMetric[] {
  return [
    {
      id: 'catalog',
      label: 'Filmes no arquivo',
      value: formatShortNumber(stats.total.uniqueMoviesCount),
      note: 'o tamanho do acervo já reconhecido por aqui',
    },
    {
      id: 'watches',
      label: 'Sessões acumuladas',
      value: formatShortNumber(stats.total.watchesCount),
      note: 'quantas vezes esse catálogo já voltou para a tela',
    },
    {
      id: 'unwatched',
      label: 'Ainda sem sessão',
      value: formatShortNumber(stats.unwatchedCount),
      note: 'o pedaço do arquivo que ainda existe mais como promessa do que memória',
    },
    {
      id: 'span',
      label: 'Janela do histórico',
      value: stats.firstWatchAt ? formatAbsoluteDate(stats.firstWatchAt) : 'Sem início',
      note: stats.latestWatchAt ? `até ${formatAbsoluteDate(stats.latestWatchAt)}` : 'sem sessão recente consolidada',
    },
  ]
}

function buildSpotlightFromCard(card: MovieLibraryCardModel | undefined, fallbackTitle: string, fallbackNote: string) {
  if (!card) return null

  return {
    title: card.title,
    subtitle: card.subtitle,
    imageUrl: card.imageUrl,
    href: card.href,
    meta: card.sessionsLabel,
    note: card.activityLabel || fallbackNote,
  }
}

function buildContextMetrics(payload: {
  summary: MoviesSummaryResponse
  recentMovies: MoviesRecentResponse
  stats: MoviesStatsResponse
}): MovieCollectionContextMetric[] {
  const rewatchCount = Math.max(payload.stats.total.watchesCount - payload.stats.total.uniqueMoviesCount, 0)

  return [
    {
      id: 'catalog',
      label: 'Filmes no arquivo',
      value: formatShortNumber(payload.stats.total.uniqueMoviesCount),
      note: 'o tamanho atual do catálogo reconhecido por aqui',
    },
    {
      id: 'month',
      label: 'Sessões no recorte',
      value: formatShortNumber(payload.summary.watchesCount),
      note: 'o quanto a tela andou neste período mais recente',
    },
    {
      id: 'rewatches',
      label: 'Retornos acumulados',
      value: formatShortNumber(rewatchCount),
      note: 'quando um filme reaparece e deixa de ser só passagem',
    },
    {
      id: 'unwatched',
      label: 'Ainda sem sessão',
      value: formatShortNumber(payload.stats.unwatchedCount),
      note: 'a parte do acervo que segue mais catálogo do que memória',
    },
  ]
}

export function buildMovieCollectionData(payload: {
  summary: MoviesSummaryResponse
  recentMovies: MoviesRecentResponse
  stats: MoviesStatsResponse
}): MovieCollectionData {
  const recentMoments = sortByTimestamp(payload.recentMovies.items.map(recentMovieToShelfItem)).slice(0, 18)
  const featuredSessions = recentMoments.slice(0, 6)
  const heroLead = featuredSessions[0] ? toHighlight(featuredSessions[0]) : null
  const heroSupporting = featuredSessions.slice(1, 5).map(toHighlight)

  return {
    generatedAt: new Date().toISOString(),
    hero: {
      title: 'Os filmes que ainda ocupam a memória recente',
      intro:
        'Uma primeira página para a parte mais próxima da filmoteca: o que voltou, o que marcou presença e o que ainda merece ficar visível antes de entrar no arquivo completo.',
      lead: heroLead,
      supporting: heroSupporting,
    },
    featuredSessions,
    recentMoments,
    context: {
      eyebrow: 'Panorama',
      title: 'O tamanho e o ritmo desse recorte',
      description:
        'Um contexto curto para entender quanto a filmoteca girou e quanto dela ainda está só esperando a primeira sessão.',
      summary: `${formatShortNumber(payload.summary.uniqueMoviesCount)} filmes circularam no recorte recente e ${formatShortNumber(payload.stats.total.watchesCount)} sessões já ficaram registradas no histórico total`,
      metrics: buildContextMetrics(payload),
    },
  }
}

export function buildMovieCollectionPageData(
  payload: import('~/types/movies').MovieCollectionMembersResponse,
): import('~/types/movies').MovieCollectionPageData {
  const members = payload.members.map((member) => ({
    id: `collection-${payload.collectionId}-${member.tmdbId}`,
    tmdbId: member.tmdbId,
    title: member.title,
    subtitle: member.year ? String(member.year) : 'Sem ano',
    overview: member.overview,
    imageUrl: member.posterUrl ?? member.backdropUrl,
    href: member.localSlug ? movieHref(member.localSlug) : null,
    tmdbUrl: member.tmdbUrl,
    inCatalog: member.inCatalog,
    statusLabel: member.inCatalog ? 'Catálogo local' : 'Sugestão TMDb',
    meta: member.inCatalog ? 'Já existe no arquivo' : 'Ainda fora do catálogo',
  }))

  const leadMember = members.find((member) => member.inCatalog) ?? members[0] ?? null
  const supporting = members
    .filter((member) => member.id !== leadMember?.id)
    .slice(0, 4)
    .map((member) => ({
      id: member.id,
      type: 'movie' as const,
      title: member.title,
      subtitle: member.subtitle,
      eyebrow: member.inCatalog ? 'Catálogo' : 'TMDb',
      imageUrl: member.imageUrl,
      href: member.href,
      timestamp: '',
      meta: member.meta,
    }))

  const cataloguedCount = members.filter((member) => member.inCatalog).length
  const missingCount = members.length - cataloguedCount
  const imagedCount = members.filter((member) => member.imageUrl).length
  const coveragePct = members.length ? Math.round((cataloguedCount / members.length) * 100) : 0

  return {
    collectionId: payload.collectionId,
    tmdbId: payload.tmdbId,
    name: payload.name,
    overview: payload.overview,
    posterUrl: payload.posterUrl,
    backdropUrl: payload.backdropUrl,
    hero: {
      title: payload.name,
      intro:
        payload.overview ||
        'Uma página própria para percorrer a coleção inteira sem depender de entrar primeiro por um filme específico.',
      backLink: '/movies',
      backLabel: 'Voltar para filmes',
      accentLink: '/movies',
      accentLabel: 'Abrir filmes',
      lead: leadMember
        ? {
            id: leadMember.id,
            type: 'movie',
            title: leadMember.title,
            subtitle: leadMember.subtitle,
            eyebrow: leadMember.inCatalog ? 'Catálogo local' : 'Entrada da coleção',
            imageUrl: leadMember.imageUrl,
            href: leadMember.href,
            timestamp: '',
            meta: leadMember.meta,
          }
        : null,
      supporting,
    },
    context: {
      eyebrow: 'Coleção',
      title: 'O estado atual desse recorte',
      description:
        'Uma leitura curta do tamanho da franquia, do que já entrou no catálogo e do que ainda existe só como referência externa.',
      summary: members.length
        ? `${formatShortNumber(members.length)} filmes na coleção, ${formatShortNumber(cataloguedCount)} já presentes no catálogo local e ${formatShortNumber(missingCount)} ainda fora dele.`
        : 'Ainda não há membros retornados para esta coleção.',
      metrics: [
        {
          id: 'collection-total',
          label: 'Filmes na coleção',
          value: formatShortNumber(members.length),
          note: 'o tamanho completo do recorte vindo do TMDb',
        },
        {
          id: 'collection-catalogued',
          label: 'No catálogo',
          value: formatShortNumber(cataloguedCount),
          note: 'o quanto da coleção já entrou de fato no arquivo',
        },
        {
          id: 'collection-missing',
          label: 'Fora do catálogo',
          value: formatShortNumber(missingCount),
          note: 'o que ainda aparece só como referência externa',
        },
        {
          id: 'collection-coverage',
          label: 'Cobertura local',
          value: `${coveragePct}%`,
          note: `${formatShortNumber(imagedCount)} entradas com imagem útil para navegação visual`,
        },
      ],
    },
    members,
  }
}

export function buildMovieCollectionsIndexPageData(
  collections: MovieCollectionSummaryDto[],
): MovieCollectionsIndexPageData {
  const items = collections.map((collection) => ({
    id: `collection-${collection.id}`,
    collectionId: collection.id,
    name: collection.name,
    href: `/movies/collections/${collection.id}`,
    posterUrl: collection.posterUrl,
    backdropUrl: collection.backdropUrl,
    movieCount: collection.movieCount,
    watchedMoviesCount: collection.watchedMoviesCount,
    previewMovies: collection.previewMovies.map((preview) => ({
      id: `collection-${collection.id}-preview-${preview.movieId}`,
      title: preview.title,
      href: movieHref(preview.slug),
      imageUrl: preview.coverUrl,
    })),
  }))

  const lead = items[0]
  const supporting = items.slice(1, 5)

  return {
    hero: {
      title: 'As coleções já abertas na filmoteca',
      intro:
        'Franquias e conjuntos que já têm presença local suficiente para virar uma navegação própria, sem depender de começar por um título solto.',
      backLink: '/movies',
      backLabel: 'Voltar para filmes',
      accentLink: '/movies/lists',
      accentLabel: 'Ver listas manuais',
      lead: lead
        ? {
            id: lead.id,
            type: 'movie',
            title: lead.name,
            subtitle: `${lead.movieCount} filmes`,
            eyebrow: 'Coleção',
            imageUrl: lead.posterUrl ?? lead.backdropUrl ?? lead.previewMovies[0]?.imageUrl ?? null,
            href: lead.href,
            timestamp: '',
            meta: `${lead.watchedMoviesCount} com sessão`,
          }
        : null,
      supporting: supporting.map((item) => ({
        id: item.id,
        type: 'movie' as const,
        title: item.name,
        subtitle: `${item.movieCount} filmes`,
        eyebrow: 'Coleção',
        imageUrl: item.posterUrl ?? item.backdropUrl ?? item.previewMovies[0]?.imageUrl ?? null,
        href: item.href,
        timestamp: '',
        meta: `${item.watchedMoviesCount} com sessão`,
      })),
    },
    summary: items.length
      ? `${formatShortNumber(items.length)} coleções locais já funcionam como porta de entrada para a filmoteca.`
      : 'Nenhuma coleção local foi consolidada ainda.',
    items,
  }
}

export function buildMovieLibraryCards(items: MovieLibraryCardDto[]): MovieLibraryCardModel[] {
  return items.map(buildLibraryCardModel)
}

export function buildMovieLibraryPageData(payload: {
  stats: MoviesStatsResponse
  library: MoviesLibraryResponse
  query: string
  selectedYear: number | null
  selectedUnwatched: boolean
  searchResults: MoviesSearchResponse | null
  yearResults: MoviesByYearResponse | null
}): MovieLibraryPageData {
  const years = payload.stats.years
    .slice()
    .sort((a, b) => b.year - a.year)
    .slice(0, 12)
    .map((year) => ({
      year: year.year,
      label: String(year.year),
      watches: `${formatShortNumber(year.watchesCount)} sessões`,
    }))

  if (payload.selectedYear && payload.yearResults) {
    const watchedItems = payload.yearResults.watched.map(buildWatchedYearCardModel)
    const unwatchedItems = payload.yearResults.unwatched.map(buildUnwatchedYearCardModel)

    return {
      hero: {
        title: `Os filmes em ${payload.selectedYear}`,
        intro: 'Um corte anual para ver o que realmente voltou à tela e o que ficou fora desse período.',
        backLink: '/movies',
        backLabel: 'Voltar ao recorte',
        accentLink: '/movies',
        accentLabel: 'Ver todos os filmes',
        spotlight: buildSpotlightFromCard(
          watchedItems[0] ?? unwatchedItems[0],
          `A biblioteca de filmes em ${payload.selectedYear}`,
          'O primeiro ponto de entrada para esse recorte anual.',
        ),
      },
      filters: {
        query: payload.query,
        selectedYear: payload.selectedYear,
        selectedUnwatched: false,
        years,
      },
      context: {
        eyebrow: 'Ano',
        title: `O que ${payload.selectedYear} concentrou`,
        description: '',
        summary: `${formatShortNumber(payload.yearResults.stats.uniqueMoviesCount)} filmes e ${formatShortNumber(payload.yearResults.stats.watchesCount)} sessões apareceram neste recorte.`,
        metrics: [
          {
            id: 'year-movies',
            label: 'Filmes vistos no ano',
            value: formatShortNumber(payload.yearResults.stats.uniqueMoviesCount),
            note: 'o quanto esse ano realmente teve presença na tela',
          },
          {
            id: 'year-watches',
            label: 'Sessões no ano',
            value: formatShortNumber(payload.yearResults.stats.watchesCount),
            note: 'a intensidade do retorno aos filmes naquele período',
          },
          {
            id: 'year-rewatches',
            label: 'Revisitas no ano',
            value: formatShortNumber(payload.yearResults.stats.rewatchesCount),
            note: 'quando o mesmo título voltou mais de uma vez',
          },
          {
            id: 'year-unwatched',
            label: 'Fora desse recorte',
            value: formatShortNumber(payload.yearResults.unwatched.length),
            note: 'parte do catálogo que não entrou em cena naquele ano',
          },
        ],
      },
      sections: [
        {
          id: 'watched',
          eyebrow: 'Vistos',
          title: 'Os que realmente passaram por esse ano',
          description: '',
          summary: '',
          items: watchedItems,
          emptyMessage: 'Nenhum filme foi marcado nesse ano.',
        },
        {
          id: 'unwatched',
          eyebrow: 'Fora da rotação',
          title: 'O que ficou fora desse período',
          description: '',
          summary: '',
          items: unwatchedItems,
          emptyMessage: 'Tudo entrou no recorte desse ano.',
        },
      ],
      libraryCursor: null,
      mode: 'year',
    }
  }

  if (payload.query && payload.searchResults) {
    const searchItems = payload.searchResults.movies
      .filter((movie) => !payload.selectedUnwatched || !movie.watchedAt)
      .map(buildSearchCardModel)

    return {
      hero: {
        title: payload.selectedUnwatched ? 'Filmes não vistos pela busca' : 'Filmes encontrados pela busca',
        intro: payload.selectedUnwatched
          ? 'A busca agora mostra só o que já está no catálogo, mas ainda não teve nenhuma sessão.'
          : 'Quando você já sabe o nome, a página encurta o caminho e puxa o recorte certo.',
        backLink: '/movies',
        backLabel: 'Voltar ao recorte',
        accentLink: '/movies',
        accentLabel: 'Limpar busca',
        utilityLink: payload.query
          ? `/movies?q=${encodeURIComponent(payload.query)}${payload.selectedUnwatched ? '&unwatched=1' : ''}&add=1`
          : `/movies?${payload.selectedUnwatched ? 'unwatched=1&' : ''}add=1`,
        utilityLabel: 'Adicionar filme',
        spotlight: buildSpotlightFromCard(
          searchItems[0],
          'A biblioteca de filmes, puxada pela busca',
          'O primeiro resultado vira a porta de entrada visual deste recorte.',
        ),
      },
      filters: {
        query: payload.query,
        selectedYear: payload.selectedYear,
        selectedUnwatched: payload.selectedUnwatched,
        years,
      },
      context: {
        eyebrow: payload.selectedUnwatched ? 'Busca + não vistos' : 'Busca',
        title: payload.selectedUnwatched
          ? 'Os filmes sem sessão que responderam à busca'
          : 'O arquivo inteiro, afunilado pelo nome',
        description: '',
        summary: `${formatShortNumber(searchItems.length)} filmes encontrados para "${payload.query}".`,
        metrics: buildStatsMetrics(payload.stats),
      },
      sections: [
        {
          id: 'search-results',
          eyebrow: 'Resultados',
          title: payload.selectedUnwatched ? 'Os não vistos que responderam à busca' : 'O que respondeu à busca',
          description: '',
          summary: '',
          items: searchItems,
          emptyMessage: 'Nada apareceu para essa busca.',
        },
      ],
      libraryCursor: null,
      mode: 'search',
    }
  }

  const libraryItems = payload.library.items.map(buildLibraryCardModel)
  const activeItems = libraryItems.filter((item) => !item.isDormant)
  const dormantItems = libraryItems.filter((item) => item.isDormant)

  return {
    hero: {
      title: payload.selectedUnwatched ? 'Filmes ainda não vistos' : 'Todos os filmes',
      intro: payload.selectedUnwatched
        ? 'Um corte do catálogo para ver só o que já entrou no arquivo, mas ainda não passou pela tela.'
        : 'O arquivo completo para quando você quer atravessar a filmoteca inteira de uma vez.',
      backLink: '/movies',
      backLabel: 'Voltar ao recorte',
      accentLink: payload.selectedUnwatched
        ? '/movies'
        : '/movies?year=' + (years[0]?.year ?? new Date().getFullYear()),
      accentLabel: payload.selectedUnwatched ? 'Ver todos os filmes' : 'Abrir um recorte por ano',
      utilityLink: payload.selectedUnwatched ? '/movies?unwatched=1&add=1' : '/movies?add=1',
      utilityLabel: 'Adicionar filme',
      spotlight: buildSpotlightFromCard(
        activeItems[0] ?? dormantItems[0],
        'A biblioteca inteira de filmes',
        'Um ponto de entrada imagético para atravessar o arquivo.',
      ),
    },
    filters: {
      query: payload.query,
      selectedYear: payload.selectedYear,
      selectedUnwatched: payload.selectedUnwatched,
      years,
    },
    context: {
      eyebrow: payload.selectedUnwatched ? 'Não vistos' : 'Arquivo',
      title: payload.selectedUnwatched ? 'O que ainda não ganhou sessão' : 'O tamanho do catálogo e do hábito',
      description: '',
      summary: payload.selectedUnwatched
        ? `${formatShortNumber(dormantItems.length)} filmes já estão no catálogo sem nenhuma sessão registrada.`
        : `${formatShortNumber(payload.stats.total.uniqueMoviesCount)} filmes no arquivo e ${formatShortNumber(payload.stats.total.watchesCount)} sessões acumuladas até aqui.`,
      metrics: buildStatsMetrics(payload.stats),
    },
    sections: [
      {
        id: 'library-catalog',
        eyebrow: payload.selectedUnwatched ? 'Não vistos' : 'Arquivo',
        title: payload.selectedUnwatched
          ? 'Os filmes que ainda esperam a primeira sessão'
          : 'A parede completa do catálogo',
        description: '',
        summary: '',
        items: payload.selectedUnwatched ? dormantItems : [...activeItems, ...dormantItems],
        emptyMessage: payload.selectedUnwatched
          ? 'Nada ficou sem sessão no catálogo atual.'
          : 'Ainda não há filmes no catálogo.',
      },
    ],
    libraryCursor: payload.library.nextCursor,
    mode: 'library',
  }
}

export function buildMoviePageData(movie: MovieDetailsResponse): MoviePageData {
  const latestWatch = movie.watches[0]?.watchedAt ?? null
  const firstWatch = movie.watches[movie.watches.length - 1]?.watchedAt ?? null
  const watchedCollectionMovies = movie.collection?.movies.filter((item) => item.watched).length ?? 0
  const directors = uniquePeopleById(movie.people.filter((credit) => credit.job === 'Director'))
  const writers = uniquePeopleById(
    movie.people.filter((credit) => ['Writer', 'Screenplay', 'Story'].includes(credit.job ?? '')),
  )
  const cast = uniquePeopleById(movie.people.filter((credit) => credit.creditType === 'CAST'))
  const uniquePeople = uniquePeopleById(movie.people)
  const visibleTerms = movie.terms.filter((term) => term.active)
  const hiddenTerms = movie.terms.filter((term) => !term.active)
  const orderedGallery = [
    ...(movie.coverUrl ? [movie.coverUrl] : []),
    ...movie.images
      .slice()
      .sort((left, right) => {
        if (left.isPrimary !== right.isPrimary) {
          return left.isPrimary ? -1 : 1
        }

        return right.id - left.id
      })
      .map((image) => image.url),
  ].filter((value, index, array) => array.indexOf(value) === index)

  return {
    movieId: movie.movieId,
    slug: movie.slug ?? String(movie.movieId),
    title: movie.title,
    originalTitle: movie.originalTitle,
    year: movie.year,
    description: movie.description,
    coverUrl: movie.coverUrl,
    gallery: orderedGallery.slice(0, 4),
    heroMeta: [
      movie.year ? String(movie.year) : null,
      `${movie.watches.length} sessões`,
      latestWatch ? `Última ${formatRelativeDate(latestWatch)}` : 'Sem registro de sessão',
    ].filter(Boolean) as string[],
    stats: {
      totalWatches: movie.watches.length,
      firstWatch,
      latestWatch,
      latestWatchRelative: latestWatch ? formatRelativeDate(latestWatch) : 'Sem registro recente',
    },
    identifiers: movie.externalIds.map((identifier) => ({
      id: `${identifier.provider}-${identifier.externalId}`,
      provider: identifier.provider,
      externalId: identifier.externalId,
    })),
    lists: {
      summary: movie.lists.length
        ? `${movie.lists.length} listas manuais já incluem este filme.`
        : 'Este filme ainda não entrou em nenhuma lista manual.',
      visibleCount: movie.lists.length,
      items: movie.lists.map((list) => ({
        id: `list-${list.listId}`,
        listId: list.listId,
        name: list.name,
        href: `/movies/lists/${list.slug}`,
        description: list.description,
        coverMovieId: list.coverMovieId,
        coverImageUrl: list.coverUrl,
        itemCount: list.itemCount,
        previewMovies: list.previewMovies.map((preview) => ({
          id: `list-${list.listId}-preview-${preview.movieId}`,
          title: preview.title,
          href: movieHref(preview.slug),
          imageUrl: preview.coverUrl,
        })),
      })),
    },
    companies: {
      summary: movie.companies.length
        ? `${movie.companies.length} estúdios ou produtoras ligados a este filme.`
        : 'Ainda não há empresas locais ligadas a este filme.',
      visibleCount: movie.companies.length,
      items: movie.companies.map((company) => ({
        id: `company-${company.companyId}`,
        companyId: company.companyId,
        name: company.name,
        href: `/movies/companies/${company.slug}`,
        logoUrl: company.logoUrl,
        originCountry: company.originCountry,
        typeLabel: company.companyType === 'PRODUCTION' ? 'Produção' : company.companyType,
      })),
    },
    people: {
      summary: uniquePeople.length
        ? `${uniquePeople.length} pessoas locais entre direção, roteiro e elenco principal.`
        : 'Ainda não há créditos locais puxados do TMDb para este filme.',
      visibleCount: uniquePeople.length,
      groups: [
        {
          id: 'directors',
          title: 'Direção',
          items: directors.map((person) => ({
            id: `person-${person.personId}-director`,
            personId: person.personId,
            name: person.name,
            href: `/movies/people/${person.slug}`,
            roleLabel: 'Direção',
            profileUrl: person.profileUrl,
          })),
        },
        {
          id: 'writers',
          title: 'Roteiro',
          items: writers.map((person) => ({
            id: `person-${person.personId}-writer`,
            personId: person.personId,
            name: person.name,
            href: `/movies/people/${person.slug}`,
            roleLabel: 'Roteiro',
            profileUrl: person.profileUrl,
          })),
        },
        {
          id: 'cast',
          title: 'Elenco',
          items: cast.map((person) => ({
            id: `person-${person.personId}-cast`,
            personId: person.personId,
            name: person.name,
            href: `/movies/people/${person.slug}`,
            roleLabel: person.characterName || 'Elenco',
            profileUrl: person.profileUrl,
          })),
        },
      ].filter((group) => group.items.length),
    },
    terms: {
      summary: visibleTerms.length
        ? `${visibleTerms.length} termos ativos entre classificação ampla e recortes mais pessoais.`
        : 'Ainda não há termos ativos costurando esse filme.',
      visibleCount: visibleTerms.length,
      hiddenCount: hiddenTerms.length,
      groups: [
        {
          id: 'genre',
          title: 'Gêneros',
          description: 'A camada mais estável da classificação, boa para filtros e listas inteligentes.',
          items: movie.terms
            .filter((term) => term.kind === 'GENRE')
            .map((term) => ({
              id: `term-${term.id}`,
              termId: term.id,
              name: term.name,
              href: `/movies/terms/${term.kind.toLowerCase()}/${term.slug}`,
              kind: term.kind,
              source: term.source,
              hiddenGlobally: term.hiddenGlobally,
              hiddenForMovie: term.hiddenForMovie,
              active: term.active,
              stateLabel: term.active
                ? term.source === 'TMDB'
                  ? 'TMDb'
                  : 'Manual'
                : term.hiddenGlobally
                  ? 'Oculto globalmente'
                  : 'Oculto neste filme',
            })),
        },
        {
          id: 'tag',
          title: 'Tags',
          description: 'Recortes mais específicos e livres, vindos do TMDb ou da sua própria curadoria.',
          items: movie.terms
            .filter((term) => term.kind === 'TAG')
            .map((term) => ({
              id: `term-${term.id}`,
              termId: term.id,
              name: term.name,
              href: `/movies/terms/${term.kind.toLowerCase()}/${term.slug}`,
              kind: term.kind,
              source: term.source,
              hiddenGlobally: term.hiddenGlobally,
              hiddenForMovie: term.hiddenForMovie,
              active: term.active,
              stateLabel: term.active
                ? term.source === 'TMDB'
                  ? 'TMDb'
                  : 'Manual'
                : term.hiddenGlobally
                  ? 'Oculto globalmente'
                  : 'Oculto neste filme',
            })),
        },
      ],
    },
    collection: movie.collection
      ? {
          id: String(movie.collection.id),
          name: movie.collection.name,
          tmdbId: movie.collection.tmdbId,
          posterUrl: movie.collection.posterUrl,
          backdropUrl: movie.collection.backdropUrl,
          progressLabel: `${watchedCollectionMovies}/${movie.collection.movies.length} assistidos`,
          movies: movie.collection.movies.map((item) => ({
            id: String(item.movieId),
            title: item.title,
            subtitle: [item.year ? String(item.year) : null, item.watched ? 'Assistido' : 'Na fila']
              .filter(Boolean)
              .join(' · '),
            href: item.slug ? `/movies/${item.slug}` : null,
            imageUrl: item.coverUrl,
            watched: item.watched,
            current: item.current,
          })),
        }
      : null,
    recentWatches: movie.watches.slice(0, 24).map(mapWatch),
  }
}

export function buildMoviePersonPageData(
  person: MoviePersonDetailsResponse,
): import('~/types/movies').MoviePersonPageData {
  return {
    personId: person.personId,
    tmdbId: person.tmdbId,
    name: person.name,
    slug: person.slug,
    profileUrl: person.profileUrl,
    heroMeta: [`${person.movieCount} filmes`, `${person.watchedMoviesCount} com sessão`, ...person.roles.slice(0, 3)],
    roles: person.roles,
    stats: {
      movieCount: person.movieCount,
      watchedMoviesCount: person.watchedMoviesCount,
    },
    movies: person.movies.map(buildLibraryCardModel),
  }
}

export function buildMovieCompanyPageData(
  company: MovieCompanyDetailsResponse,
): import('~/types/movies').MovieCompanyPageData {
  return {
    companyId: company.companyId,
    tmdbId: company.tmdbId,
    name: company.name,
    slug: company.slug,
    logoUrl: company.logoUrl,
    originCountry: company.originCountry,
    typeLabel: company.companyType === 'PRODUCTION' ? 'Produção' : company.companyType,
    heroMeta: [
      `${company.movieCount} filmes`,
      `${company.watchedMoviesCount} com sessão`,
      company.originCountry,
    ].filter(Boolean) as string[],
    stats: {
      movieCount: company.movieCount,
      watchedMoviesCount: company.watchedMoviesCount,
    },
    movies: company.movies.map(buildLibraryCardModel),
  }
}

export function buildMovieListPageData(list: MovieListDetailsResponse): import('~/types/movies').MovieListPageData {
  return {
    listId: list.listId,
    name: list.name,
    slug: list.slug,
    description: list.description,
    coverMovieId: list.coverMovieId,
    coverImageUrl: list.coverUrl,
    heroMeta: [`${list.movieCount} filmes`, `${list.watchedMoviesCount} com sessão`],
    stats: {
      movieCount: list.movieCount,
      watchedMoviesCount: list.watchedMoviesCount,
    },
    movies: list.movies.map(buildLibraryCardModel),
  }
}

export function buildMovieListsIndexPageData(lists: MovieListSummaryDto[]): MovieListsIndexPageData {
  const items = lists.map((list) => ({
    id: `list-${list.listId}`,
    listId: list.listId,
    name: list.name,
    href: `/movies/lists/${list.slug}`,
    description: list.description,
    coverMovieId: list.coverMovieId,
    coverImageUrl: list.coverUrl,
    itemCount: list.itemCount,
    previewMovies: list.previewMovies.map((preview) => ({
      id: `list-${list.listId}-preview-${preview.movieId}`,
      title: preview.title,
      href: movieHref(preview.slug),
      imageUrl: preview.coverUrl,
    })),
  }))

  const spotlight = items[0]

  return {
    hero: {
      title: 'Os recortes manuais da filmoteca',
      intro:
        'Uma estante de listas feitas à mão para quando a biblioteca inteira é grande demais e você quer entrar por afinidade, ocasião ou obsessão.',
      backLink: '/movies',
      backLabel: 'Voltar para filmes',
      accentLink: '/movies?add=1',
      accentLabel: 'Adicionar filme',
      spotlight: spotlight
        ? {
            title: spotlight.name,
            subtitle: spotlight.description || `${spotlight.itemCount} filmes ligados a este recorte.`,
            imageUrl: spotlight.coverImageUrl ?? spotlight.previewMovies[0]?.imageUrl ?? null,
            href: spotlight.href,
            meta: `${spotlight.itemCount} filmes`,
            note: 'O recorte mais recentemente atualizado vira a primeira entrada visual da estante.',
          }
        : null,
    },
    summary: items.length
      ? `${formatShortNumber(items.length)} listas manuais ativas somando portas de entrada próprias para a biblioteca.`
      : 'Nenhuma lista manual foi criada ainda.',
    items,
  }
}

export function buildMovieTermPageData(
  term: import('~/types/movies').MovieTermDetailsResponse,
): import('~/types/movies').MovieTermPageData {
  return {
    kind: term.kind,
    name: term.name,
    slug: term.slug,
    heroMeta: [
      term.kind === 'GENRE' ? 'Gênero' : 'Tag',
      `${term.movieCount} filmes`,
      `${term.watchedMoviesCount} com sessão`,
    ],
    stats: {
      movieCount: term.movieCount,
      watchedMoviesCount: term.watchedMoviesCount,
    },
    movies: term.movies.map(buildLibraryCardModel),
  }
}
