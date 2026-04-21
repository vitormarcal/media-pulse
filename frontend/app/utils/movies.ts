import type {
  MovieCollectionContextMetric,
  MovieCollectionData,
  MovieLibraryCardDto,
  MovieLibraryCardModel,
  MovieLibraryMetric,
  MovieLibraryPageData,
  MovieDetailsResponse,
  MoviesByYearResponse,
  MoviesLibraryResponse,
  MoviesSearchResponse,
  MoviePageData,
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

export function buildMovieLibraryCards(items: MovieLibraryCardDto[]): MovieLibraryCardModel[] {
  return items.map(buildLibraryCardModel)
}

export function buildMovieLibraryPageData(payload: {
  stats: MoviesStatsResponse
  library: MoviesLibraryResponse
  query: string
  selectedYear: number | null
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
        title: `A biblioteca de filmes em ${payload.selectedYear}`,
        intro:
          'Um corte do arquivo completo para ver o que realmente voltou à tela nesse ano e o que ficou apenas como parte do catálogo.',
        backLink: '/movies',
        backLabel: 'Voltar ao recorte',
        accentLink: '/movies/library',
        accentLabel: 'Ver biblioteca inteira',
        spotlight: buildSpotlightFromCard(
          watchedItems[0] ?? unwatchedItems[0],
          `A biblioteca de filmes em ${payload.selectedYear}`,
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
        description: 'Uma leitura do ano como memória de sessões, não como relatório frio.',
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
          description: 'O miolo do recorte anual, em vez de uma lista seca.',
          summary: 'Aqui vale mais a lembrança de presença do que a ideia de completude.',
          items: watchedItems,
          emptyMessage: 'Nenhum filme foi marcado nesse ano.',
        },
        {
          id: 'unwatched',
          eyebrow: 'Fora da rotação',
          title: 'O que ficou fora desse período',
          description: 'Ainda parte da biblioteca, mas sem ter entrado no ritmo do ano.',
          summary: 'Esse bloco serve para situar ausência, não para cobrar retorno.',
          items: unwatchedItems,
          emptyMessage: 'Tudo entrou no recorte desse ano.',
        },
      ],
      libraryCursor: null,
      mode: 'year',
    }
  }

  if (payload.query && payload.searchResults) {
    const searchItems = payload.searchResults.movies.map(buildSearchCardModel)

    return {
      hero: {
        title: 'A biblioteca de filmes, puxada pela busca',
        intro:
          'Quando você já sabe o que está tentando reencontrar, a página vira arquivo de consulta sem perder a mesma superfície editorial.',
        backLink: '/movies',
        backLabel: 'Voltar ao recorte',
        accentLink: '/movies/library',
        accentLabel: 'Limpar busca',
        utilityLink: payload.query
          ? `/movies/library?q=${encodeURIComponent(payload.query)}&add=1`
          : '/movies/library?add=1',
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
        years,
      },
      context: {
        eyebrow: 'Busca',
        title: 'O arquivo inteiro, afunilado pelo nome',
        description: 'Sem esconder o resto da biblioteca; só aproximando o que você quer achar agora.',
        summary: `${formatShortNumber(payload.searchResults.movies.length)} filmes encontrados para "${payload.query}".`,
        metrics: buildStatsMetrics(payload.stats),
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
  const activeItems = libraryItems.filter((item) => !item.isDormant)
  const dormantItems = libraryItems.filter((item) => item.isDormant)

  return {
    hero: {
      title: 'A biblioteca inteira de filmes',
      intro:
        'O arquivo completo para quando a memória curta já não basta e você quer percorrer a filmoteca toda com mais calma.',
      backLink: '/movies',
      backLabel: 'Voltar ao recorte',
      accentLink: '/movies/library?year=' + (years[0]?.year ?? new Date().getFullYear()),
      accentLabel: 'Abrir um recorte por ano',
      utilityLink: '/movies/library?add=1',
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
      years,
    },
    context: {
      eyebrow: 'Arquivo',
      title: 'O tamanho do catálogo e do hábito',
      description: 'Uma visão larga do que já entrou nessa filmoteca, sem perder a leitura pessoal do conjunto.',
      summary: `${formatShortNumber(payload.stats.total.uniqueMoviesCount)} filmes no arquivo e ${formatShortNumber(payload.stats.total.watchesCount)} sessões acumuladas até aqui.`,
      metrics: buildStatsMetrics(payload.stats),
    },
    sections: [
      {
        id: 'active-library',
        eyebrow: 'Com rastro',
        title: 'Os filmes que já deixaram marca',
        description: 'Os que já têm alguma sessão e por isso funcionam melhor como entrada para o catálogo.',
        summary: 'É o arquivo inteiro, mas começando pelos que já têm memória associada.',
        items: activeItems,
        emptyMessage: 'Ainda não há filmes com sessão registrada.',
      },
      {
        id: 'dormant-library',
        eyebrow: 'Ainda quietos',
        title: 'O que ainda espera a primeira sessão',
        description: 'Parte da biblioteca que já existe no arquivo, mas ainda sem ter virado lembrança de tela.',
        summary: 'Mais catálogo do que hábito, por enquanto.',
        items: dormantItems,
        emptyMessage: 'Nada ficou sem sessão na biblioteca atual.',
      },
    ],
    libraryCursor: payload.library.nextCursor,
    mode: 'library',
  }
}

export function buildMoviePageData(movie: MovieDetailsResponse): MoviePageData {
  const latestWatch = movie.watches[0]?.watchedAt ?? null
  const firstWatch = movie.watches[movie.watches.length - 1]?.watchedAt ?? null
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
    recentWatches: movie.watches.slice(0, 24).map(mapWatch),
  }
}
