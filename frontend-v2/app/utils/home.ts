import type {
  BooksListResponse,
  BooksSummaryResponse,
  CurrentlyWatchingShowDto,
  EditorialHighlight,
  EditorialMediaType,
  EditorialShelfItem,
  HomePageData,
  MoviesRecentResponse,
  MoviesSummaryResponse,
  MusicSummaryResponse,
  RecentAlbumsPageResponse,
  ReadCardDto,
  ShowsRecentResponse,
  ShowsSummaryResponse,
} from '~/types/home'
import { formatRelativeDate, formatShortNumber } from '~/utils/formatting'

function mediaLabel(type: EditorialMediaType) {
  switch (type) {
    case 'music':
      return 'Album'
    case 'show':
      return 'Série'
    case 'movie':
      return 'Filme'
    case 'book':
      return 'Livro'
  }
}

function sortByTimestamp<T extends { timestamp?: string | null }>(items: T[]) {
  return [...items].sort((a, b) => {
    const aTime = a.timestamp ? new Date(a.timestamp).getTime() : 0
    const bTime = b.timestamp ? new Date(b.timestamp).getTime() : 0
    return bTime - aTime
  })
}

function createBookSubtitle(read: ReadCardDto) {
  const authors = read.book.authors.map((author) => author.name).join(', ')
  return authors || 'Autoria não informada'
}

function createBookMeta(read: ReadCardDto) {
  if (read.progressPct) {
    return `${Math.round(read.progressPct)}% lido`
  }

  if (read.progressPages) {
    return `${read.progressPages} páginas`
  }

  return read.status === 'READ' ? 'Concluído' : 'Leitura em curso'
}

function bookToShelfItem(read: ReadCardDto): EditorialShelfItem {
  const timestamp = read.finishedAt ?? read.startedAt

  return {
    id: `book-${read.readId}`,
    type: 'book',
    title: read.book.title,
    subtitle: createBookSubtitle(read),
    imageUrl: read.book.coverUrl,
    href: `/books/${read.book.slug}`,
    meta: createBookMeta(read),
    detail: timestamp ? formatRelativeDate(timestamp) : 'Sem data registrada',
    timestamp,
  }
}

function currentShowToShelfItem(show: CurrentlyWatchingShowDto): EditorialShelfItem {
  const watched = show.progress.watchedEpisodesCount
  const total = show.progress.episodesCount

  return {
    id: `show-${show.showId}`,
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

function recentMovieToShelfItem(movie: MoviesRecentResponse['items'][number]): EditorialShelfItem {
  return {
    id: `movie-${movie.movieId}`,
    type: 'movie',
    title: movie.title,
    subtitle: movie.year ? String(movie.year) : 'Filme',
    imageUrl: movie.coverUrl,
    href: movie.slug ? `/movies/${movie.slug}` : null,
    meta: mediaLabel('movie'),
    detail: formatRelativeDate(movie.watchedAt),
    timestamp: movie.watchedAt,
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
    meta: mediaLabel('show'),
    detail: formatRelativeDate(show.watchedAt),
    timestamp: show.watchedAt,
  }
}

function recentAlbumToShelfItem(album: RecentAlbumsPageResponse['items'][number]): EditorialShelfItem {
  return {
    id: `album-${album.albumId}`,
    type: 'music',
    title: album.albumTitle,
    subtitle: album.artistName,
    imageUrl: album.coverUrl,
    href: null,
    meta: `${formatShortNumber(album.playCount)} plays`,
    detail: formatRelativeDate(album.lastPlayed),
    timestamp: album.lastPlayed,
  }
}

function toHighlight(item: EditorialShelfItem): EditorialHighlight {
  return {
    id: item.id,
    type: item.type,
    title: item.title,
    subtitle: item.subtitle,
    eyebrow: mediaLabel(item.type),
    imageUrl: item.imageUrl,
    href: item.href,
    timestamp: item.timestamp ?? new Date().toISOString(),
    meta: `${item.meta} · ${item.detail}`,
  }
}

export function buildHomePageData(payload: {
  musicSummary: MusicSummaryResponse
  recentAlbums: RecentAlbumsPageResponse
  movieSummary: MoviesSummaryResponse
  recentMovies: MoviesRecentResponse
  showSummary: ShowsSummaryResponse
  currentShows: CurrentlyWatchingShowDto[]
  recentShows: ShowsRecentResponse
  booksSummary: BooksSummaryResponse
  readingBooks: BooksListResponse
  finishedBooks: BooksListResponse
}): HomePageData {
  const inProgress = [
    ...payload.currentShows.map(currentShowToShelfItem),
    ...payload.readingBooks.items.map(bookToShelfItem),
  ].slice(0, 8)

  const recentMoments = sortByTimestamp([
    ...payload.recentAlbums.items.map(recentAlbumToShelfItem),
    ...payload.recentMovies.items.map(recentMovieToShelfItem),
    ...payload.recentShows.items.map(recentShowToShelfItem),
    ...payload.finishedBooks.items.map(bookToShelfItem),
  ]).slice(0, 12)

  const heroCandidates = sortByTimestamp([
    ...inProgress,
    ...recentMoments,
  ])

  const heroLead = heroCandidates[0] ? toHighlight(heroCandidates[0]) : null
  const heroSupporting = heroCandidates.slice(1, 5).map(toHighlight)

  return {
    generatedAt: new Date().toISOString(),
    hero: {
      title: 'Uma capa viva do que está passando por você agora',
      intro:
        'Mais revista do que painel: uma leitura contínua do que entrou em rotação, foi concluído ou ainda está te acompanhando.',
      lead: heroLead,
      supporting: heroSupporting,
    },
    inProgress,
    recentMoments,
    sections: [
      {
        id: 'music',
        eyebrow: 'Som da semana',
        title: 'Discos que mantiveram o ritmo aceso',
        description: 'Os álbuns mais recentes puxam a temperatura da página e dão o tom do momento.',
        summary: `${formatShortNumber(payload.musicSummary.albumsCount)} álbuns e ${formatShortNumber(payload.musicSummary.tracksCount)} faixas tocadas no recorte atual`,
        items: payload.recentAlbums.items.slice(0, 6).map(recentAlbumToShelfItem),
      },
      {
        id: 'shows',
        eyebrow: 'Sessão em curso',
        title: 'Séries que seguem abertas na mesa',
        description: 'Quando a relação continua, o progresso vira parte da narrativa.',
        summary: `${formatShortNumber(payload.showSummary.uniqueShowsCount)} séries vistas e ${formatShortNumber(payload.showSummary.watchesCount)} episódios/sessões registrados no período`,
        items: payload.currentShows.slice(0, 6).map(currentShowToShelfItem),
      },
      {
        id: 'movies',
        eyebrow: 'Tela recente',
        title: 'Filmes que acabaram de deixar rastro',
        description: 'Entradas recentes aparecem como uma faixa curta e visual, sem cara de dashboard.',
        summary: `${formatShortNumber(payload.movieSummary.uniqueMoviesCount)} filmes únicos e ${formatShortNumber(payload.movieSummary.watchesCount)} plays no período`,
        items: payload.recentMovies.items.slice(0, 6).map(recentMovieToShelfItem),
      },
      {
        id: 'books',
        eyebrow: 'Mesa de leitura',
        title: 'Livros entre o agora e o recém-terminado',
        description: 'Leituras em andamento e conclusões recentes dividem a mesma atmosfera editorial.',
        summary: `${formatShortNumber(payload.booksSummary.counts.reading)} em leitura e ${formatShortNumber(payload.booksSummary.counts.finished)} concluídos no período`,
        items: [
          ...payload.readingBooks.items.slice(0, 3).map(bookToShelfItem),
          ...payload.finishedBooks.items.slice(0, 3).map(bookToShelfItem),
        ],
      },
    ],
  }
}
