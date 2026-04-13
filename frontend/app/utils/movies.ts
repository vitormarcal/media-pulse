import type {
  MovieCollectionContextMetric,
  MovieCollectionData,
  MovieDetailsResponse,
  MoviePageData,
  MoviesStatsResponse,
  MovieWatchEntryModel,
} from '~/types/movies'
import type { EditorialHighlight, EditorialShelfItem, MoviesRecentResponse, MoviesSummaryResponse } from '~/types/home'
import { formatAbsoluteDate, formatRelativeDate, formatShortNumber } from '~/utils/formatting'

function mapWatch(watch: MovieDetailsResponse['watches'][number], index: number): MovieWatchEntryModel {
  return {
    id: `watch-${watch.watchId}`,
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
    subtitle: movie.year ? String(movie.year) : (movie.originalTitle || 'Filme'),
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
      description: 'Um contexto curto para entender quanto a filmoteca girou e quanto dela ainda está só esperando a primeira sessão.',
      summary: `${formatShortNumber(payload.summary.uniqueMoviesCount)} filmes circularam no recorte recente e ${formatShortNumber(payload.stats.total.watchesCount)} sessões já ficaram registradas no histórico total`,
      metrics: buildContextMetrics(payload),
    },
  }
}

export function buildMoviePageData(movie: MovieDetailsResponse): MoviePageData {
  const latestWatch = movie.watches[0]?.watchedAt ?? null
  const firstWatch = movie.watches[movie.watches.length - 1]?.watchedAt ?? null

  return {
    slug: movie.slug ?? String(movie.movieId),
    title: movie.title,
    originalTitle: movie.originalTitle,
    year: movie.year,
    description: movie.description,
    coverUrl: movie.coverUrl,
    gallery: [
      ...(movie.coverUrl ? [movie.coverUrl] : []),
      ...movie.images.map((image) => image.url),
    ].filter((value, index, array) => array.indexOf(value) === index).slice(0, 4),
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
