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

Resposta body:

```json
{
  "limit": 50,
  "accepted": true
}
```

### `GET /api/spotify/status`

Retorna o estado operacional da importação do Spotify. Os estados possíveis são:

- `UNKNOWN`: nenhuma importação terminou desde a criação do estado;
- `HEALTHY`: a última importação terminou com sucesso;
- `REAUTHORIZATION_REQUIRED`: o Spotify retornou `invalid_grant`; novas tentativas de token ficam suspensas até a aplicação reiniciar com um refresh token novo;
- `ERROR`: ocorreu outra falha na última importação.

O contrato não expõe tokens nem a descrição crua retornada pelo provedor. Quando OAuth está habilitado e a autorização expirou, `reauthorizationUrl` aponta para `/oauth/spotify/login`.

Refresh tokens do Spotify expiram após seis meses. Depois de obter um novo token, atualize `SPOTIFY_REFRESH_TOKEN` e reinicie a aplicação. Um refresh bem-sucedido altera o estado para `HEALTHY`.

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

Resposta: `200 OK` com `sectionKey` e `stats`.

## Event sources

### `POST /event-sources/reprocess`

Reprocessa em lote eventos salvos em `event_sources` conforme os filtros enviados em `ReprocessRequest`.

Resposta: `ApiResult<ReprocessCounter>`.

### `POST /event-sources/{id}/reprocess`

Reprocessa um evento específico pelo `id`.

Resposta: `ApiResult<Unit>`.

## Comments

Comentários são cross-domain e podem aparecer nos detalhes de livros, filmes, séries, álbuns e games.

### `POST /api/comments/{mediaType}/{entityId}`

Cria comentário para uma mídia existente.

Body:

```json
{
  "body": "Comentário pessoal.",
  "commentedAt": "2026-05-19T12:00:00Z"
}
```

- `mediaType`: `movies`, `shows`, `albums`, `books`, `games`
- `body` é obrigatório depois de `trim`
- `body` acima de 10000 caracteres retorna `400`
- mídia inexistente retorna `404`
- retorna `MediaCommentDto`

### `POST /api/comments/{commentId}/edit`

Atualiza comentário existente.

Body:

```json
{
  "body": "Comentário atualizado.",
  "commentedAt": "2026-05-19T12:00:00Z"
}
```

- comentário inexistente retorna `404`
- retorna `MediaCommentDto`

## Ratings

Ratings são cross-domain e podem aparecer nos detalhes de filmes, séries, episódios, álbuns, faixas e games.

### `POST /api/ratings/{mediaType}/{entityId}`

Cria ou atualiza nota para uma mídia existente.

Body:

```json
{
  "rating": 5
}
```

- `mediaType`: `movies`, `shows`, `episodes`, `albums`, `tracks`, `games`
- `rating` deve estar entre `1` e `5`
- mídia inexistente retorna `404`
- retorna `MediaRatingDto`

### `DELETE /api/ratings/{mediaType}/{entityId}`

Remove a nota da mídia.

- mídia inexistente retorna `404`
- resposta: `204 No Content`

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

## Invariantes

- endpoints operacionais podem disparar trabalho assíncrono
- segredos e tokens reais não devem ser documentados em arquivos versionados
- callbacks OAuth retornam texto simples porque são usados para configuração local

## Non-goals

- este documento não descreve APIs read-only de domínio
- este documento não define UI para operações

## Critérios de aceite

- endpoints documentados existem em controllers operacionais ou cross-domain
- status HTTP documentado bate com o controller quando confirmado
- payloads documentados usam DTOs reais
