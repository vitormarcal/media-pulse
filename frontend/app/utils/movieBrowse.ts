import type {
  MovieLibraryCardDto,
  MovieLibraryCardModel,
  MoviesByYearResponse,
  MoviesSearchResponse,
} from '~/types/movies'
import { formatRelativeDate } from '~/utils/formatting'

type Identity = Pick<MovieLibraryCardDto, 'movieId' | 'title' | 'slug' | 'year' | 'coverUrl'>
function card(movie: Identity, activityLabel: string, sessionsLabel = ''): MovieLibraryCardModel {
  return {
    id: `browse-${movie.movieId}`,
    movieId: movie.movieId,
    title: movie.title,
    subtitle: movie.year == null ? '' : String(movie.year),
    href: movie.slug ? `/movies/${movie.slug}` : null,
    imageUrl: movie.coverUrl,
    activityLabel,
    sessionsLabel,
    aside: '',
  }
}
export function movieBrowseCards(movies: MovieLibraryCardDto[]) {
  return movies.map((movie) =>
    card(
      movie,
      movie.lastWatchedAt ? `Última sessão ${formatRelativeDate(movie.lastWatchedAt)}` : 'Sem sessão registrada',
      movie.watchCount > 1 ? `${movie.watchCount} sessões` : '',
    ),
  )
}
export function movieBrowseSearchCards(movies: MoviesSearchResponse['movies']) {
  return movies.map((movie) =>
    card(movie, movie.watchedAt ? `Última sessão ${formatRelativeDate(movie.watchedAt)}` : 'Sem sessão registrada'),
  )
}
export function movieBrowseYearCards(movies: MoviesByYearResponse['watched']) {
  return movies.map((movie) =>
    card(
      movie,
      `Última no ano ${formatRelativeDate(movie.lastWatchedAt)}`,
      movie.watchCountInYear > 1 ? `${movie.watchCountInYear} sessões no ano` : '',
    ),
  )
}
