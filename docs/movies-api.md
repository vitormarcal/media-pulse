# Movies API

A Movies API fornece endpoints read-only para consulta e um endpoint de ingestão manual idempotente de watches.

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
| `GET /api/movies/year/{year}` | `year` (path), `limitWatched=200` (máx. `1000`), `limitUnwatched=200` (máx. `1000`) | `MoviesByYearResponse` |
| `GET /api/movies/stats` | - | `MoviesStatsResponse` |
| `POST /api/movies/watches` | body com `watchedAt`, `title`, `year?`, `tmdbId?`, `imdbId?` | `ManualMovieWatchCreateResponse` |

## Contratos

### MovieCardDto

```json
{
  "movieId": 42,
  "title": "De Olhos Bem Fechados",
  "originalTitle": "Eyes Wide Shut",
  "slug": "eyes-wide-shut",
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
  "slug": "eyes-wide-shut",
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
      "slug": "eyes-wide-shut",
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

### MoviesByYearResponse

```json
{
  "year": 2026,
  "range": {
    "start": "2026-01-01T00:00:00Z",
    "end": "2026-12-31T23:59:59Z"
  },
  "stats": {
    "watchesCount": 42,
    "uniqueMoviesCount": 30,
    "rewatchesCount": 12
  },
  "watched": [
    {
      "movieId": 1,
      "slug": "eyes-wide-shut",
      "title": "Eyes Wide Shut",
      "originalTitle": "Eyes Wide Shut",
      "year": 1999,
      "coverUrl": "/covers/plex/movies/1/poster.jpg",
      "watchCountInYear": 2,
      "firstWatchedAt": "2026-01-10T21:00:00Z",
      "lastWatchedAt": "2026-02-27T19:40:19Z"
    }
  ],
  "unwatched": [
    {
      "movieId": 9,
      "slug": "movie-x",
      "title": "Movie X",
      "originalTitle": "Movie X",
      "year": 2001,
      "coverUrl": "/covers/plex/movies/9/poster.jpg"
    }
  ]
}
```

### MoviesStatsResponse

```json
{
  "total": {
    "watchesCount": 120,
    "uniqueMoviesCount": 85
  },
  "unwatchedCount": 240,
  "years": [
    {
      "year": 2026,
      "watchesCount": 42,
      "uniqueMoviesCount": 30,
      "rewatchesCount": 12
    },
    {
      "year": 2025,
      "watchesCount": 18,
      "uniqueMoviesCount": 16,
      "rewatchesCount": 2
    }
  ],
  "latestWatchAt": "2026-02-27T19:40:19Z",
  "firstWatchAt": "2024-01-10T11:00:00Z"
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
- Em `GET /api/movies/year/{year}`, `rewatchesCount = watchesCount - uniqueMoviesCount`.
- `watched` inclui filmes com pelo menos 1 watch no range do ano solicitado.
- `unwatched` inclui apenas filmes sem qualquer linha em `movie_watches` (nunca assistidos).

## Deduplicação de watches

A deduplicação ocorre por `(source, movie_id, watched_at)`.

- Mesmo filme + mesma source + mesmo `watchedAt`: conflito e insert ignorado.
- Mesmo filme em horários diferentes: ambos persistem.

## Ingestão manual (`POST /api/movies/watches`)

O endpoint processa um único watch por requisição.

Regras de resolução do filme por item (ordem):

1. `tmdbId`: busca em `external_identifiers` (`provider=TMDB`, `entity_type=MOVIE`).
2. `imdbId`: busca em `external_identifiers` (`provider=IMDB`, `entity_type=MOVIE`).
3. Fingerprint por `title + year`.
4. Se não encontrar, cria `movies` + `movie_titles` (`source=MANUAL`, `is_primary=true`) e vincula ids externos quando informados.

Regras de idempotência:

- `movie_watches` usa `ON CONFLICT DO NOTHING` e UNIQUE `(source, movie_id, watched_at)`.
- `movie_titles`, `movie_images` e `external_identifiers` também usam inserção idempotente com `insertIgnore`/verificação prévia.

Enriquecimento simples de capa:

- Se o item tiver `tmdbId` e o filme ainda não tiver imagem primária, busca `poster_path` em TMDb (`/movie/{id}`).
- Baixa a imagem `${TMDB_IMAGE_BASE_URL}/t/p/w780{poster_path}` e salva em disco (padrão `/covers/tmdb/movies/{movieId}/...`).
- Salva o caminho local em `movie_images` (`is_primary=true`) e preenche `movies.cover_url` apenas se estiver `null`.
- As chamadas TMDb aplicam throttle local (`TMDB_RATE_LIMIT_PER_SECOND`) e retry para `429` respeitando `Retry-After` quando enviado.

## Erros esperados

- `GET /api/movies/{movieId}` retorna `404` se filme não existir.
- `GET /api/movies/slug/{slug}` retorna `404` se filme não existir.
- `GET /api/movies/summary` com `range=custom` sem `start`/`end`, ou com `range` inválido, lança `IllegalArgumentException` (`"range inválido"`).
- `GET /api/movies/year/{year}` retorna `400` se `year < 1900` ou `year > ano atual + 1`.
- `GET /api/movies/year/{year}` retorna `400` se `limitWatched < 1` ou `limitUnwatched < 1`.

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
curl "{{host}}/api/movies/slug/eyes-wide-shut"
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

### Movies por ano

```bash
curl "{{host}}/api/movies/year/2026?limitWatched=200&limitUnwatched=200"
```

### Stats globais

```bash
curl "{{host}}/api/movies/stats"
```
