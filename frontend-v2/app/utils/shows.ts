import type {
  ShowDetailsResponse,
  ShowPageData,
  ShowSeasonCardModel,
  ShowWatchDto,
  ShowWatchEntryModel,
} from '~/types/shows'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'

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
