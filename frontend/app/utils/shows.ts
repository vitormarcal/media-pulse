import type {
  ShowCollectionData,
  ShowCollectionContextMetric,
  ShowLibraryCardDto,
  ShowLibraryCardModel,
  ShowLibraryMetric,
  ShowLibraryPageData,
  ShowDetailsResponse,
  ShowPageData,
  ShowSeasonCardModel,
  ShowSeasonDetailsResponse,
  ShowSeasonPageData,
  ShowWatchDto,
  ShowWatchEntryModel,
  ShowsByYearResponse,
  ShowsLibraryResponse,
  ShowsSearchResponse,
  ShowsStatsResponse,
} from '~/types/shows'
import type {
  CurrentlyWatchingShowDto,
  EditorialHighlight,
  EditorialShelfItem,
  ShowsRecentResponse,
  ShowsSummaryResponse,
} from '~/types/home'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'
import { formatShortNumber } from '~/utils/formatting'

function formatEpisodeContext(watch: ShowWatchDto) {
  const season = watch.seasonNumber != null ? `T${String(watch.seasonNumber).padStart(2, '0')}` : 'Especial'
  const episode = watch.episodeNumber != null ? `E${String(watch.episodeNumber).padStart(2, '0')}` : 'Episódio'
  return `${season} · ${episode}`
}

function formatProgressStatus(show: ShowDetailsResponse) {
  if (!show.progress || show.progress.episodesCount === 0) {
    return 'Sem progresso consolidado ainda'
  }

  if (show.progress.completed) {
    return 'Série concluída'
  }

  if (show.progress.inProgress) {
    return 'Em andamento'
  }

  return 'Ainda sem avanço suficiente'
}

function mapSeason(
  season: ShowDetailsResponse['seasons'][number],
  showSlug: string | null,
): ShowSeasonCardModel {
  const progressValue =
    season.episodesCount > 0
      ? Math.round((season.watchedEpisodesCount / season.episodesCount) * 100)
      : 0

  const label =
    season.seasonNumber != null
      ? `Temporada ${season.seasonNumber}`
      : (season.seasonTitle || 'Especiais')

  return {
    id: `${season.seasonNumber ?? 'special'}-${season.seasonTitle ?? 'season'}`,
    title: season.seasonTitle || label,
    progressLabel: `${season.watchedEpisodesCount}/${season.episodesCount} episódios`,
    progressValue,
    detail: season.lastWatchedAt
      ? `Último avanço ${formatRelativeDate(season.lastWatchedAt)}`
      : 'Sem episódio visto ainda',
    isComplete: season.completed,
    href: showSlug && season.seasonNumber != null ? `/shows/${showSlug}/seasons/${season.seasonNumber}` : null,
  }
}

function mapWatch(watch: ShowWatchDto): ShowWatchEntryModel {
  return {
    id: `watch-${watch.watchId}`,
    title: watch.episodeTitle,
    context: formatEpisodeContext(watch),
    meta: formatAbsoluteDate(watch.watchedAt),
    watchedAt: watch.watchedAt,
    relativeWatchedAt: formatRelativeDate(watch.watchedAt),
    source: watch.source,
  }
}

function sortByTimestamp<T extends { timestamp?: string | null }>(items: T[]) {
  return [...items].sort((a, b) => {
    const aTime = a.timestamp ? new Date(a.timestamp).getTime() : 0
    const bTime = b.timestamp ? new Date(b.timestamp).getTime() : 0
    return bTime - aTime
  })
}

function currentShowToShelfItem(show: CurrentlyWatchingShowDto): EditorialShelfItem {
  const watched = show.progress.watchedEpisodesCount
  const total = show.progress.episodesCount

  return {
    id: `show-current-${show.showId}`,
    type: 'show',
    title: show.title,
    subtitle: show.year ? String(show.year) : 'Série em andamento',
    imageUrl: show.coverUrl,
    href: show.slug ? `/shows/${show.slug}` : null,
    meta: `${watched}/${total} episódios`,
    detail: formatRelativeDate(show.lastWatchedAt),
    timestamp: show.lastWatchedAt,
  }
}

function recentShowToShelfItem(show: ShowsRecentResponse['items'][number]): EditorialShelfItem {
  return {
    id: `show-recent-${show.showId}`,
    type: 'show',
    title: show.title,
    subtitle: show.year ? String(show.year) : 'Série',
    imageUrl: show.coverUrl,
    href: show.slug ? `/shows/${show.slug}` : null,
    meta: 'Último episódio marcado',
    detail: formatRelativeDate(show.watchedAt),
    timestamp: show.watchedAt,
  }
}

function toHighlight(item: EditorialShelfItem): EditorialHighlight {
  return {
    id: item.id,
    type: item.type,
    title: item.title,
    subtitle: item.subtitle,
    eyebrow: 'Série',
    imageUrl: item.imageUrl,
    href: item.href,
    timestamp: item.timestamp ?? new Date().toISOString(),
    meta: `${item.meta} · ${item.detail}`,
  }
}

function showHref(slug: string | null) {
  return slug ? `/shows/${slug}` : null
}

function formatShowSubtitle(title: string, originalTitle: string, year: number | null) {
  if (originalTitle && originalTitle !== title && year) {
    return `${originalTitle} · ${year}`
  }

  if (originalTitle && originalTitle !== title) {
    return originalTitle
  }

  return year ? String(year) : 'Série'
}

function buildLibraryCardModel(show: ShowLibraryCardDto): ShowLibraryCardModel {
  const totalEpisodes = show.episodesCount || 0
  const watchedEpisodes = show.watchedEpisodesCount || 0
  const progressValue = totalEpisodes > 0 ? Math.round((watchedEpisodes / totalEpisodes) * 100) : 0
  const complete = totalEpisodes > 0 && watchedEpisodes >= totalEpisodes

  return {
    id: `library-${show.showId}`,
    title: show.title,
    subtitle: formatShowSubtitle(show.title, show.originalTitle, show.year),
    href: showHref(show.slug),
    imageUrl: show.coverUrl,
    progressLabel: totalEpisodes > 0 ? `${watchedEpisodes}/${totalEpisodes} episódios` : 'Sem total consolidado',
    progressValue,
    activityLabel: show.lastWatchedAt ? `Último avanço ${formatRelativeDate(show.lastWatchedAt)}` : 'Sem watch registrado ainda',
    aside: complete ? 'Fechada' : progressValue > 0 ? 'Aberta' : 'Intocada',
    isDormant: !show.lastWatchedAt,
  }
}

export function buildShowLibraryCards(items: ShowLibraryCardDto[]): ShowLibraryCardModel[] {
  return items.map(buildLibraryCardModel)
}

function buildSearchCardModel(show: ShowsSearchResponse['shows'][number]): ShowLibraryCardModel {
  return {
    id: `search-${show.showId}`,
    title: show.title,
    subtitle: formatShowSubtitle(show.title, show.originalTitle, show.year),
    href: showHref(show.slug),
    imageUrl: show.coverUrl,
    progressLabel: show.watchedAt ? 'Encontrada pela busca' : 'Sem watch recente no índice',
    progressValue: 0,
    activityLabel: show.watchedAt ? `Último registro ${formatRelativeDate(show.watchedAt)}` : 'Sem registro recente',
    aside: 'Busca',
  }
}

function buildWatchedYearCardModel(show: ShowsByYearResponse['watched'][number]): ShowLibraryCardModel {
  return {
    id: `year-watched-${show.showId}`,
    title: show.title,
    subtitle: formatShowSubtitle(show.title, show.originalTitle, show.year),
    href: showHref(show.slug),
    imageUrl: show.coverUrl,
    progressLabel: `${show.watchCountInYear} registros no ano`,
    progressValue: 100,
    activityLabel: `Último ${formatRelativeDate(show.lastWatchedAt)}`,
    aside: 'Vista no ano',
  }
}

function buildUnwatchedYearCardModel(show: ShowsByYearResponse['unwatched'][number]): ShowLibraryCardModel {
  return {
    id: `year-unwatched-${show.showId}`,
    title: show.title,
    subtitle: formatShowSubtitle(show.title, show.originalTitle, show.year),
    href: showHref(show.slug),
    imageUrl: show.coverUrl,
    progressLabel: 'Sem registro nesse recorte',
    progressValue: 0,
    activityLabel: 'Ficou fora desse ano',
    aside: 'Sem watch',
    isDormant: true,
  }
}

function buildStatsMetrics(stats: ShowsStatsResponse): ShowLibraryMetric[] {
  return [
    {
      id: 'catalog',
      label: 'Séries no arquivo',
      value: formatShortNumber(stats.total.uniqueShowsCount),
      note: 'o tamanho do acervo já reconhecido por aqui',
    },
    {
      id: 'watches',
      label: 'Registros acumulados',
      value: formatShortNumber(stats.total.watchesCount),
      note: 'quanto esse arquivo já foi percorrido ao longo do tempo',
    },
    {
      id: 'unwatched',
      label: 'Ainda sem watch',
      value: formatShortNumber(stats.unwatchedCount),
      note: 'o pedaço da biblioteca que ainda existe mais como catálogo do que como memória',
    },
    {
      id: 'span',
      label: 'Janela do histórico',
      value: stats.firstWatchAt ? formatAbsoluteDate(stats.firstWatchAt) : 'Sem início',
      note: stats.latestWatchAt ? `até ${formatAbsoluteDate(stats.latestWatchAt)}` : 'sem watch recente consolidado',
    },
  ]
}

function buildSpotlightFromCard(
  card: ShowLibraryCardModel | undefined,
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
  summary: ShowsSummaryResponse
  currentShows: CurrentlyWatchingShowDto[]
  recentShows: ShowsRecentResponse
}): ShowCollectionContextMetric[] {
  return [
    {
      id: 'unique',
      label: 'Séries que passaram pelo recorte',
      value: formatShortNumber(payload.summary.uniqueShowsCount),
      note: 'o bastante para lembrar o tamanho da rotação do mês',
    },
    {
      id: 'watches',
      label: 'Episódios marcados',
      value: formatShortNumber(payload.summary.watchesCount),
      note: 'a medida real do quanto esse catálogo andou',
    },
    {
      id: 'open',
      label: 'Ainda abertas agora',
      value: formatShortNumber(payload.currentShows.length),
      note: 'as que ainda estão pedindo o próximo episódio',
    },
    {
      id: 'recent',
      label: 'Memória curta disponível',
      value: formatShortNumber(payload.recentShows.items.length),
      note: 'entradas recentes o bastante para recolocar o fio da meada',
    },
  ]
}

export function buildShowPageData(show: ShowDetailsResponse): ShowPageData {
  const progress = show.progress ?? {
    episodesCount: 0,
    watchedEpisodesCount: 0,
    seasonsCount: 0,
    completedSeasonsCount: 0,
    completed: false,
    inProgress: false,
  }

  const completionPct =
    progress.episodesCount > 0
      ? Math.round((progress.watchedEpisodesCount / progress.episodesCount) * 100)
      : 0

  const heroMeta = [
    show.year ? String(show.year) : null,
    `${progress.watchedEpisodesCount}/${progress.episodesCount} episódios`,
    `${progress.completedSeasonsCount}/${progress.seasonsCount} temporadas`,
    formatProgressStatus(show),
  ].filter(Boolean) as string[]

  return {
    showId: show.showId,
    slug: show.slug ?? String(show.showId),
    title: show.title,
    originalTitle: show.originalTitle,
    year: show.year,
    description: show.description,
    coverUrl: show.coverUrl,
    gallery: [
      ...(show.coverUrl ? [show.coverUrl] : []),
      ...show.images.map((image) => image.url),
    ].filter((value, index, array) => array.indexOf(value) === index).slice(0, 4),
    progress: {
      watchedEpisodes: progress.watchedEpisodesCount,
      totalEpisodes: progress.episodesCount,
      watchedSeasons: progress.completedSeasonsCount,
      totalSeasons: progress.seasonsCount,
      completionPct,
      statusText: formatProgressStatus(show),
    },
    heroMeta,
    seasons: show.seasons.map((season) => mapSeason(season, show.slug)),
    recentWatches: show.watches.slice(0, 24).map(mapWatch),
  }
}

function formatDuration(durationMs: number | null) {
  if (!durationMs) return null
  const minutes = Math.round(durationMs / 60000)
  return `${minutes} min`
}

function formatEpisodeNumber(episodeNumber: number | null) {
  return episodeNumber != null ? `E${String(episodeNumber).padStart(2, '0')}` : 'Episódio'
}

export function buildShowSeasonPageData(season: ShowSeasonDetailsResponse): ShowSeasonPageData {
  const completionPct =
    season.episodesCount > 0
      ? Math.round((season.watchedEpisodesCount / season.episodesCount) * 100)
      : 0
  const seasonTitle =
    season.seasonTitle ||
    (season.seasonNumber != null ? `Temporada ${season.seasonNumber}` : 'Especiais')
  const statusText = season.completed ? 'Temporada concluída' : season.watchedEpisodesCount > 0 ? 'Temporada em andamento' : 'Ainda sem episódio visto'
  const lastWatchedLabel = season.lastWatchedAt ? `Último avanço ${formatRelativeDate(season.lastWatchedAt)}` : 'Sem avanço registrado'

  return {
    showId: season.showId,
    showSlug: season.showSlug,
    showTitle: season.showTitle,
    showOriginalTitle: season.showOriginalTitle,
    showYear: season.showYear,
    showCoverUrl: season.showCoverUrl,
    seasonTitle,
    seasonNumber: season.seasonNumber,
    progress: {
      watchedEpisodes: season.watchedEpisodesCount,
      totalEpisodes: season.episodesCount,
      completionPct,
      statusText,
      lastWatchedLabel,
    },
    heroMeta: [
      season.showYear ? String(season.showYear) : null,
      `${season.watchedEpisodesCount}/${season.episodesCount} episódios`,
      `${completionPct}% visto`,
      lastWatchedLabel,
    ].filter(Boolean) as string[],
    episodes: season.episodes.map((episode) => {
      const duration = formatDuration(episode.durationMs)
      const release = episode.originallyAvailableAt ? formatAbsoluteDate(episode.originallyAvailableAt) : null

      return {
        id: `episode-${episode.episodeId}`,
        episodeId: episode.episodeId,
        title: episode.title,
        episodeNumber: episode.episodeNumber,
        context: formatEpisodeNumber(episode.episodeNumber),
        summary: episode.summary,
        meta: [duration, release].filter(Boolean) as string[],
        watchedLabel: episode.lastWatchedAt ? `Visto ${formatRelativeDate(episode.lastWatchedAt)}` : 'Sem watch',
        watched: episode.watchCount > 0,
      }
    }),
  }
}

export function buildShowCollectionData(payload: {
  summary: ShowsSummaryResponse
  currentShows: CurrentlyWatchingShowDto[]
  recentShows: ShowsRecentResponse
}): ShowCollectionData {
  const inProgress = payload.currentShows.map(currentShowToShelfItem)
  const recentMoments = payload.recentShows.items.map(recentShowToShelfItem)
  const heroCandidates = sortByTimestamp([
    ...inProgress,
    ...recentMoments,
  ])

  return {
    generatedAt: new Date().toISOString(),
    hero: {
      title: 'As séries que seguem puxando você de volta',
      intro:
        'Um recorte só do que continua vivo na cabeça: o que ficou em aberto, o que avançou por último e o que ainda vale retomar sem esforço.',
      lead: heroCandidates[0] ? toHighlight(heroCandidates[0]) : null,
      supporting: heroCandidates.slice(1, 5).map(toHighlight),
    },
    inProgress,
    recentMoments,
    context: {
      eyebrow: 'Recorte do mês',
      title: 'O tamanho dessa rotação recente',
      description: 'Não para resumir o catálogo inteiro, só para situar o volume e lembrar o que ainda está em volta.',
      summary: `${formatShortNumber(payload.summary.uniqueShowsCount)} séries passaram por aqui e ${formatShortNumber(payload.summary.watchesCount)} episódios foram marcados neste recorte.`,
      metrics: buildContextMetrics(payload),
    },
  }
}

export function buildShowLibraryPageData(payload: {
  stats: ShowsStatsResponse
  library: ShowsLibraryResponse
  query: string
  selectedYear: number | null
  searchResults: ShowsSearchResponse | null
  yearResults: ShowsByYearResponse | null
}): ShowLibraryPageData {
  const years = payload.stats.years
    .slice()
    .sort((a, b) => b.year - a.year)
    .slice(0, 12)
    .map((year) => ({
      year: year.year,
      label: String(year.year),
      watches: `${formatShortNumber(year.watchesCount)} registros`,
    }))

  if (payload.selectedYear && payload.yearResults) {
    const watchedItems = payload.yearResults.watched.map(buildWatchedYearCardModel)
    const unwatchedItems = payload.yearResults.unwatched.map(buildUnwatchedYearCardModel)

    return {
      hero: {
        title: `A biblioteca de séries em ${payload.selectedYear}`,
        intro: 'Um corte do arquivo completo para ver o que realmente passou por esse ano e o que ficou apenas no catálogo.',
        backLink: '/shows',
        backLabel: 'Voltar ao recorte',
        accentLink: '/shows/library',
        accentLabel: 'Ver biblioteca inteira',
        spotlight: buildSpotlightFromCard(
          watchedItems[0] ?? unwatchedItems[0],
          `A biblioteca de séries em ${payload.selectedYear}`,
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
        description: 'Uma leitura do ano como memória de consumo, não como relatório.',
        summary: `${formatShortNumber(payload.yearResults.stats.uniqueShowsCount)} séries e ${formatShortNumber(payload.yearResults.stats.watchesCount)} registros apareceram neste recorte.`,
        metrics: [
          {
            id: 'year-shows',
            label: 'Séries vistas no ano',
            value: formatShortNumber(payload.yearResults.stats.uniqueShowsCount),
            note: 'o quanto esse ano realmente teve presença',
          },
          {
            id: 'year-watches',
            label: 'Registros no ano',
            value: formatShortNumber(payload.yearResults.stats.watchesCount),
            note: 'a intensidade do retorno às séries naquele período',
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
          eyebrow: 'Vistas',
          title: 'As que realmente passaram pelo ano',
          description: 'O miolo do recorte anual, em vez de uma lista seca.',
          summary: 'Aqui vale mais a lembrança de presença do que a ideia de completude.',
          items: watchedItems,
          emptyMessage: 'Nenhuma série foi marcada nesse ano.',
        },
        {
          id: 'unwatched',
          eyebrow: 'Fora da rotação',
          title: 'O que ficou de fora nesse período',
          description: 'Ainda parte da biblioteca, mas sem ter entrado no ritmo do ano.',
          summary: 'Esse bloco serve para situar ausência, não para cobrar retomada.',
          items: unwatchedItems,
          emptyMessage: 'Tudo entrou no recorte desse ano.',
        },
      ],
      libraryCursor: null,
      mode: 'year',
    }
  }

  if (payload.query && payload.searchResults) {
    const searchItems = payload.searchResults.shows.map(buildSearchCardModel)

    return {
      hero: {
        title: 'A biblioteca de séries, puxada pela busca',
        intro: 'Quando você já sabe o que está tentando reencontrar, a página vira arquivo de consulta sem perder a mesma superfície editorial.',
        backLink: '/shows',
        backLabel: 'Voltar ao recorte',
        accentLink: '/shows/library',
        accentLabel: 'Limpar busca',
        spotlight: buildSpotlightFromCard(
          searchItems[0],
          'A biblioteca de séries, puxada pela busca',
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
        summary: `${formatShortNumber(payload.searchResults.shows.length)} séries encontradas para "${payload.query}".`,
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
  const activeItems = libraryItems.filter(item => !item.isDormant)
  const dormantItems = libraryItems.filter(item => item.isDormant)

  return {
    hero: {
      title: 'A biblioteca inteira de séries',
      intro: 'O arquivo completo para quando a memória curta já não basta e você quer percorrer o acervo todo com mais calma.',
      backLink: '/shows',
      backLabel: 'Voltar ao recorte',
      accentLink: '/shows/library?year=' + (years[0]?.year ?? new Date().getFullYear()),
      accentLabel: 'Abrir um recorte por ano',
      spotlight: buildSpotlightFromCard(
        activeItems[0] ?? dormantItems[0],
        'A biblioteca inteira de séries',
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
      description: 'Uma visão larga do que já entrou nessa biblioteca, ainda sem perder a leitura pessoal do conjunto.',
      summary: `${formatShortNumber(payload.stats.total.uniqueShowsCount)} séries no arquivo e ${formatShortNumber(payload.stats.total.watchesCount)} registros acumulados até aqui.`,
      metrics: buildStatsMetrics(payload.stats),
    },
    sections: [
      {
        id: 'active-library',
        eyebrow: 'Com rastro',
        title: 'As séries que já deixaram marca',
        description: 'As que já têm algum ponto de contato e por isso funcionam melhor como entrada para o catálogo.',
        summary: 'É o arquivo inteiro, mas começando pelas que já têm memória associada.',
        items: activeItems,
        emptyMessage: 'Nenhuma série com watch consolidado apareceu nesta faixa.',
      },
      {
        id: 'dormant-library',
        eyebrow: 'Mais silenciosas',
        title: 'O que ainda existe mais como estante',
        description: 'Parte do catálogo que ainda não ganhou vida no histórico.',
        summary: 'Isso continua sendo útil como mapa do que está disponível, não como pendência.',
        items: dormantItems,
        emptyMessage: 'Nenhuma série silenciosa nesta página.',
      },
    ],
    libraryCursor: payload.library.nextCursor,
    mode: 'library',
  }
}
