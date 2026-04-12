import type { BooksSearchResponse, MoviesSearchResponse, ShowsSearchResponse } from '~/types/home'
import type { GlobalSearchData, MusicSearchResponse, SearchResultItem } from '~/types/search'

function group(id: string, title: string, items: SearchResultItem[]) {
  return {
    id,
    title,
    items,
  }
}

export async function fetchGlobalSearch(query: string): Promise<GlobalSearchData> {
  const config = useRuntimeConfig()
  const q = query.trim()

  if (!q) {
    return { groups: [], total: 0 }
  }

  const [shows, movies, books, music] = await Promise.all([
    $fetch<ShowsSearchResponse>('/api/shows/search', { baseURL: config.public.apiBase, query: { q, limit: 5 } }),
    $fetch<MoviesSearchResponse>('/api/movies/search', { baseURL: config.public.apiBase, query: { q, limit: 5 } }),
    $fetch<BooksSearchResponse>('/api/books/search', { baseURL: config.public.apiBase, query: { q, limit: 5 } }),
    $fetch<MusicSearchResponse>('/api/music/search', { baseURL: config.public.apiBase, query: { q, limit: 5 } }),
  ])

  const groups = [
    group(
      'shows',
      'Séries',
      shows.shows.map((item) => ({
        id: `show-${item.showId}`,
        kind: 'show',
        title: item.title,
        subtitle: item.year ? String(item.year) : 'Série',
        href: item.slug ? `/shows/${item.slug}` : null,
      })),
    ),
    group(
      'movies',
      'Filmes',
      movies.movies.map((item) => ({
        id: `movie-${item.movieId}`,
        kind: 'movie',
        title: item.title,
        subtitle: item.year ? String(item.year) : 'Filme',
        href: item.slug ? `/movies/${item.slug}` : null,
      })),
    ),
    group(
      'books',
      'Livros',
      books.books.map((item) => ({
        id: `book-${item.bookId}`,
        kind: 'book',
        title: item.title,
        subtitle: item.authors.map((author) => author.name).join(', ') || 'Livro',
        href: `/books/${item.slug}`,
      })),
    ),
    group(
      'albums',
      'Álbuns',
      music.albums.map((item) => ({
        id: `album-${item.id}`,
        kind: 'album',
        title: item.title,
        subtitle: item.year ? `${item.artistName} · ${item.year}` : item.artistName,
        href: `/music/albums/${item.id}`,
      })),
    ),
    group(
      'artists',
      'Artistas',
      music.artists.map((item) => ({
        id: `artist-${item.id}`,
        kind: 'artist',
        title: item.name,
        subtitle: 'Artista',
        href: null,
      })),
    ),
    group(
      'tracks',
      'Faixas',
      music.tracks.map((item) => ({
        id: `track-${item.id}`,
        kind: 'track',
        title: item.title,
        subtitle: `${item.artistName} · ${item.albumTitle}`,
        href: null,
      })),
    ),
    group(
      'authors',
      'Autores',
      books.authors.map((item) => ({
        id: `author-${item.id}`,
        kind: 'author',
        title: item.name,
        subtitle: 'Autor',
        href: null,
      })),
    ),
  ].filter((group) => group.items.length > 0)

  return {
    groups,
    total: groups.reduce((acc, group) => acc + group.items.length, 0),
  }
}
