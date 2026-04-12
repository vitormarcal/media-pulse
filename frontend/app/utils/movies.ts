import type { MovieDetailsResponse, MoviePageData, MovieWatchEntryModel } from '~/types/movies'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'

function mapWatch(watch: MovieDetailsResponse['watches'][number], index: number): MovieWatchEntryModel {
  return {
    id: `watch-${watch.watchId}`,
    title: index === 0 ? 'Última sessão registrada' : `Sessão ${index + 1}`,
    meta: formatAbsoluteDate(watch.watchedAt),
    relativeWatchedAt: formatRelativeDate(watch.watchedAt),
    source: watch.source,
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
