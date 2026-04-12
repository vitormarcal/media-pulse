import type {
  ShowCollectionData,
  ShowCollectionContextMetric,
  ShowDetailsResponse,
  ShowPageData,
  ShowSeasonCardModel,
  ShowWatchDto,
  ShowWatchEntryModel,
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

function mapSeason(season: ShowDetailsResponse['seasons'][number]): ShowSeasonCardModel {
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
    seasons: show.seasons.map(mapSeason),
    recentWatches: show.watches.slice(0, 24).map(mapWatch),
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
