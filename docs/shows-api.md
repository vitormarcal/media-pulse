# Shows API

A Shows API fornece endpoints read-only para consulta de séries e episódios assistidos via Plex.

## Escopo e origem dos dados

- O histórico de séries é preenchido por eventos Plex `media.scrobble` com `Metadata.type=episode`.
- Import de biblioteca de séries no startup não cria histórico em `tv_episode_watches`; ele apenas cadastra metadados de `tv_shows` e `tv_episodes`.
- O watch usa `Metadata.lastViewedAt` como `watchedAt`.
- As contagens de séries são derivadas de watches de episódios agregados por `show_id`.

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/shows/recent` | `limit=20` | `ShowCardDto[]` |
| `GET /api/shows/{showId}` | `showId` (path) | `ShowDetailsResponse` |
| `GET /api/shows/slug/{slug}` | `slug` (path) | `ShowDetailsResponse` |
| `GET /api/shows/search` | `q` (busca em `originalTitle`, `tv_show_titles.title` e `slug`), `limit=10` | `ShowsSearchResponse` |
| `GET /api/shows/summary` | `range=month\|year\|custom`, `start?`, `end?` | `ShowsSummaryResponse` |
| `GET /api/shows/year/{year}` | `year` (path), `limitWatched=200` (máx. `1000`), `limitUnwatched=200` (máx. `1000`) | `ShowsByYearResponse` |
| `GET /api/shows/stats` | - | `ShowsStatsResponse` |

## Contratos

### ShowCardDto

```json
{
  "showId": 42,
  "title": "Ruptura",
  "originalTitle": "Severance",
  "slug": "severance",
  "year": 2022,
  "coverUrl": "/covers/plex/tv-shows/42/poster.jpg",
  "watchedAt": "2026-02-27T19:40:19Z"
}
```

### ShowDetailsResponse

```json
{
  "showId": 42,
  "title": "Ruptura",
  "originalTitle": "Severance",
  "slug": "severance",
  "year": 2022,
  "description": "desc",
  "coverUrl": "/covers/plex/tv-shows/42/poster.jpg",
  "images": [
    {
      "id": 101,
      "url": "/covers/plex/tv-shows/42/poster.jpg",
      "isPrimary": true
    }
  ],
  "watches": [
    {
      "watchId": 999,
      "episodeId": 5001,
      "episodeTitle": "Good News About Hell",
      "seasonNumber": 1,
      "episodeNumber": 1,
      "watchedAt": "2026-02-27T19:40:19Z",
      "source": "PLEX"
    }
  ],
  "externalIds": [
    {
      "provider": "TMDB",
      "externalId": "95396"
    },
    {
      "provider": "TVDB",
      "externalId": "371980"
    }
  ]
}
```

### ShowsSearchResponse

```json
{
  "shows": [
    {
      "showId": 42,
      "title": "Ruptura",
      "originalTitle": "Severance",
      "slug": "severance",
      "year": 2022,
      "coverUrl": "/covers/plex/tv-shows/42/poster.jpg",
      "watchedAt": "2026-02-27T19:40:19Z"
    }
  ]
}
```

### ShowsSummaryResponse

```json
{
  "range": {
    "start": "2026-01-28T21:15:00Z",
    "end": "2026-02-27T21:15:00Z"
  },
  "watchesCount": 12,
  "uniqueShowsCount": 7
}
```

### ShowsByYearResponse

```json
{
  "year": 2026,
  "range": {
    "start": "2026-01-01T00:00:00Z",
    "end": "2026-12-31T23:59:59Z"
  },
  "stats": {
    "watchesCount": 42,
    "uniqueShowsCount": 30,
    "rewatchesCount": 12
  },
  "watched": [
    {
      "showId": 1,
      "slug": "severance",
      "title": "Ruptura",
      "originalTitle": "Severance",
      "year": 2022,
      "coverUrl": "/covers/plex/tv-shows/1/poster.jpg",
      "watchCountInYear": 2,
      "firstWatchedAt": "2026-01-10T21:00:00Z",
      "lastWatchedAt": "2026-02-27T19:40:19Z"
    }
  ],
  "unwatched": [
    {
      "showId": 9,
      "slug": "dark",
      "title": "Dark",
      "originalTitle": "Dark",
      "year": 2017,
      "coverUrl": "/covers/plex/tv-shows/9/poster.jpg"
    }
  ]
}
```

### ShowsStatsResponse

```json
{
  "total": {
    "watchesCount": 120,
    "uniqueShowsCount": 85
  },
  "unwatchedCount": 240,
  "years": [
    {
      "year": 2026,
      "watchesCount": 42,
      "uniqueShowsCount": 30,
      "rewatchesCount": 12
    },
    {
      "year": 2025,
      "watchesCount": 18,
      "uniqueShowsCount": 16,
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
- `range=custom`: exige `start` e `end` em ISO-8601 UTC.

## Semântica de contagem

- `watchesCount`: conta todas as linhas em `tv_episode_watches` no range.
- `uniqueShowsCount`: conta `DISTINCT tv_episodes.show_id` no range.
- Se episódios da mesma série forem assistidos múltiplas vezes em horários diferentes, cada watch conta em `watchesCount`, mas a série conta uma vez em `uniqueShowsCount`.
- Em `GET /api/shows/year/{year}`, `rewatchesCount = watchesCount - uniqueShowsCount`.
- `watched` inclui séries com pelo menos 1 watch de episódio no range do ano solicitado.
- `unwatched` inclui apenas séries sem qualquer linha em `tv_episode_watches`.

## Deduplicação de watches

A deduplicação ocorre por `(source, episode_id, watched_at)`.

- Mesmo episódio + mesma source + mesmo `watchedAt`: conflito e insert ignorado.
- Mesmo episódio em horários diferentes: ambos persistem.

## Erros esperados

- `GET /api/shows/{showId}` retorna `404` se série não existir.
- `GET /api/shows/slug/{slug}` retorna `404` se série não existir.
- `GET /api/shows/summary` com `range=custom` sem `start`/`end`, ou com `range` inválido, lança `IllegalArgumentException` (`"range inválido"`).
- `GET /api/shows/year/{year}` retorna `400` se `year < 1900` ou `year > ano atual + 1`.
- `GET /api/shows/year/{year}` retorna `400` se `limitWatched < 1` ou `limitUnwatched < 1`.

## Exemplos de uso

### Recentes

```bash
curl "{{host}}/api/shows/recent?limit=20"
```

### Detalhes

```bash
curl "{{host}}/api/shows/42"
```

### Detalhes por slug

```bash
curl "{{host}}/api/shows/slug/severance"
```

### Busca

```bash
curl "{{host}}/api/shows/search?q=dark&limit=10"
```

### Summary mensal

```bash
curl "{{host}}/api/shows/summary?range=month"
```

### Summary custom

```bash
curl "{{host}}/api/shows/summary?range=custom&start=2026-01-01T00:00:00Z&end=2026-12-31T23:59:59Z"
```

### Shows por ano

```bash
curl "{{host}}/api/shows/year/2026?limitWatched=200&limitUnwatched=200"
```

### Stats globais

```bash
curl "{{host}}/api/shows/stats"
```
