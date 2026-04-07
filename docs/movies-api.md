# Movies API

A Movies API expõe consulta read-only da biblioteca e do histórico de watches, além de ingestão manual idempotente.

## Escopo e origem dos dados

- o histórico de filmes é preenchido por eventos Plex `media.scrobble` com `Metadata.type=movie`
- o import de biblioteca no startup cadastra metadados, mas não cria linhas em `movie_watches`
- o endpoint manual `POST /api/movies/watches` cria ou reutiliza o filme e registra um watch idempotente

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/movies/library` | `limit=20`, `cursor?` | `MoviesLibraryResponse` |
| `GET /api/movies/recent` | `limit=20`, `cursor?` | `MoviesRecentResponse` |
| `GET /api/movies/{movieId}` | `movieId` | `MovieDetailsResponse` |
| `GET /api/movies/slug/{slug}` | `slug` | `MovieDetailsResponse` |
| `GET /api/movies/search` | `q`, `limit=10` | `MoviesSearchResponse` |
| `GET /api/movies/summary` | `range=month|year|custom`, `start?`, `end?` | `MoviesSummaryResponse` |
| `GET /api/movies/stats` | - | `MoviesStatsResponse` |
| `GET /api/movies/year/{year}` | `limitWatched=200`, `limitUnwatched=200` | `MoviesByYearResponse` |
| `POST /api/movies/watches` | body com `watchedAt`, `title`, `year?`, `tmdbId?`, `imdbId?` | `ManualMovieWatchCreateResponse` |

## Paginação e limites

- `library` e `recent` são paginados por cursor retornado no payload
- `limitWatched` e `limitUnwatched` são normalizados para no máximo `1000`
- valores menores que `1` geram erro `400`

## Range temporal

`GET /api/movies/summary` aceita:

- `month`: últimos 30 dias
- `year`: últimos 365 dias
- `custom`: exige `start` e `end`

`GET /api/movies/year/{year}` aceita anos entre `1900` e `ano UTC atual + 1`.

O range do relatório anual é:

- início: `01/01/{year} 00:00:00Z`
- fim: `31/12/{year} 23:59:59Z`

## Semântica de contagem

- `watchesCount`: conta todas as linhas em `movie_watches`
- `uniqueMoviesCount`: conta `DISTINCT movie_id`
- `rewatchesCount = watchesCount - uniqueMoviesCount`
- `watched` inclui filmes com ao menos um watch no ano
- `unwatched` inclui apenas filmes nunca assistidos

## Ingestão manual

Resolução do filme:

1. `tmdbId`
2. `imdbId`
3. fingerprint por `title + year`
4. criação de `movies` e `movie_titles` com `source=MANUAL`

Regras importantes:

- deduplicação por `(source, movie_id, watched_at)`
- quando `tmdbId` existir, o serviço tenta preencher metadados faltantes e baixar imagens do TMDb
