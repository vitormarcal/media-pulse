# Movies API

A Movies API fornece uma visão read-only dos filmes assistidos e do histórico de watches importados do Plex.

## Escopo e origem dos dados

- O histórico de filmes é preenchido por eventos Plex `media.scrobble` com `Metadata.type=movie`.
- Import de biblioteca de filmes no startup **não** cria histórico em `movie_watches`; ele apenas cadastra metadados dos filmes.
- O watch usa `Metadata.lastViewedAt` como `watchedAt`.

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/movies/recent` | `limit=20` | `MovieCardDto[]` |
| `GET /api/movies/{movieId}` | `movieId` (path) | `MovieDetailsResponse` |
| `GET /api/movies/slug/{slug}` | `slug` (path) | `MovieDetailsResponse` |
| `GET /api/movies/search` | `q` (busca em `originalTitle`, `movie_titles.title` e `slug`), `limit=10` | `MoviesSearchResponse` |
| `GET /api/movies/summary` | `range=month\|year\|custom`, `start?`, `end?` | `MoviesSummaryResponse` |

## Contratos

### MovieCardDto

```json
{
  "movieId": 42,
  "title": "De Olhos Bem Fechados",
  "originalTitle": "Eyes Wide Shut",
  "year": 1999,
  "coverUrl": "/covers/plex/movies/42/poster.jpg",
  "watchedAt": "2026-02-27T19:40:19Z"
}
```

### MovieDetailsResponse

```json
{
  "movieId": 42,
  "title": "De Olhos Bem Fechados",
  "originalTitle": "Eyes Wide Shut",
  "year": 1999,
  "description": "desc",
  "coverUrl": "/covers/plex/movies/42/poster.jpg",
  "images": [
    {
      "id": 101,
      "url": "/covers/plex/movies/42/poster.jpg",
      "isPrimary": true
    }
  ],
  "watches": [
    {
      "watchId": 999,
      "watchedAt": "2026-02-27T19:40:19Z",
      "source": "PLEX"
    }
  ],
  "externalIds": [
    {
      "provider": "IMDB",
      "externalId": "tt0120663"
    },
    {
      "provider": "TMDB",
      "externalId": "345"
    }
  ]
}
```

### MoviesSearchResponse

```json
{
  "movies": [
    {
      "movieId": 42,
      "title": "De Olhos Bem Fechados",
      "originalTitle": "Eyes Wide Shut",
      "year": 1999,
      "coverUrl": "/covers/plex/movies/42/poster.jpg",
      "watchedAt": "2026-02-27T19:40:19Z"
    }
  ]
}
```

### MoviesSummaryResponse

```json
{
  "range": {
    "start": "2026-01-28T21:15:00Z",
    "end": "2026-02-27T21:15:00Z"
  },
  "watchesCount": 12,
  "uniqueMoviesCount": 7
}
```

## Regras de range (`/summary`)

- `range=month`: últimos 30 dias a partir de `now`.
- `range=year`: últimos 365 dias a partir de `now`.
- `range=custom`: exige `start` e `end` em ISO-8601 UTC (ex.: `2026-01-01T00:00:00Z`).

## Semântica de contagem (importante para integração)

- `watchesCount`: conta todas as linhas em `movie_watches` no range.
- `uniqueMoviesCount`: conta `DISTINCT movie_id` no range.
- Se o mesmo filme for assistido 2 vezes em horários diferentes, conta como 2 em `watchesCount` e 1 em `uniqueMoviesCount`.

## Deduplicação de watches

A deduplicação ocorre por `(source, movie_id, watched_at)`.

- Mesmo filme + mesma source + mesmo `watchedAt`: conflito e insert ignorado.
- Mesmo filme em horários diferentes: ambos persistem.

## Erros esperados

- `GET /api/movies/{movieId}` retorna `404` se filme não existir.
- `GET /api/movies/slug/{slug}` retorna `404` se filme não existir.
- `GET /api/movies/summary` com `range=custom` sem `start`/`end`, ou com `range` inválido, lança `IllegalArgumentException` (`"range inválido"`).

## Exemplos de uso

### Recentes

```bash
curl "{{host}}/api/movies/recent?limit=20"
```

### Detalhes

```bash
curl "{{host}}/api/movies/42"
```

### Detalhes por slug

```bash
curl "{{host}}/api/movies/slug/3828"
```

### Busca

```bash
curl "{{host}}/api/movies/search?q=matrix&limit=10"
```

### Summary mensal

```bash
curl "{{host}}/api/movies/summary?range=month"
```

### Summary custom

```bash
curl "{{host}}/api/movies/summary?range=custom&start=2026-01-01T00:00:00Z&end=2026-12-31T23:59:59Z"
```
