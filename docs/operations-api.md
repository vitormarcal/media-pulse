# Operations API

Este documento cobre endpoints operacionais de ingestão, webhook, OAuth e manutenção que não pertencem às APIs read-only por domínio.

## Spotify

### `POST /api/spotify/import`

Dispara importação assíncrona de plays recentes do Spotify.

Body opcional:

```json
{
  "resetCursor": false,
  "maxPages": 5
}
```

Resposta: `202 Accepted` com `resetCursor`.

### `POST /api/spotify/extended/import`

Recebe `multipart/form-data` com a parte `file` e importa histórico estendido exportado.

Resposta: `200 OK`.

### `POST /api/spotify/backfill-album-tracks`

Dispara backfill assíncrono de tracklists de álbuns já conhecidos.

Query params:

- `limit=50`

Resposta: `202 Accepted`.

## Spotify OAuth

### `GET /oauth/spotify/login`

Se `media-pulse.spotify.oauth.enabled=false`, retorna `404`.

Quando habilitado:

- gera `state`
- redireciona para `https://accounts.spotify.com/authorize`

### `GET /oauth/spotify/callback`

Troca o `code` do OAuth por tokens e retorna um texto simples com o `SPOTIFY_REFRESH_TOKEN` obtido.

Casos de erro documentados no código:

- OAuth desabilitado -> `404`
- `error` vindo do provedor -> `400`
- callback sem `code` -> `400`
- `state` inválido -> `401`
- ausência de `refresh_token` -> `500`

## Plex

### `POST /webhook/plex`

Recebe `multipart/form-data` com:

- `payload`: obrigatório
- `thumb`: opcional

O payload é salvo em `event_sources` e processado em seguida.

### `POST /api/plex/music/import`

Executa importação de biblioteca musical do Plex.

Body:

```json
{
  "sectionKey": "9",
  "pageSize": 200
}
```

## Event sources

### `POST /event-sources/reprocess`

Reprocessa em lote eventos salvos em `event_sources` conforme os filtros enviados em `ReprocessRequest`.

### `POST /event-sources/{id}/reprocess`

Reprocessa um evento específico pelo `id`.

## MusicBrainz

### `POST /api/musicbrainz/enrich-album-genres`

Dispara enriquecimento assíncrono de gêneros em lote.

Query params:

- `limit=200`

### `POST /api/musicbrainz/enrich-album-genres/drain`

Dispara processamento assíncrono contínuo até esgotar o backlog ou atingir o teto informado.

Query params:

- `batchSize=200`
- `maxTotal=50000`
