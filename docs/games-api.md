# Games API

Games usam entrada manual: a UI chama sugestões do IGDB e, ao criar, o backend tenta enriquecer metadados pelo IGDB e imagens pelo SteamGridDB.

## Configuração

- `IGDB_CLIENT_ID` e `IGDB_CLIENT_SECRET`: credenciais Twitch/IGDB para obter token OAuth client credentials.
- `STEAMGRIDDB_API_KEY`: chave da API SteamGridDB para buscar grids.
- `IGDB_ENABLED` e `STEAMGRIDDB_ENABLED`: permitem desligar cada integração sem remover a UI.

Sem credenciais, a entrada mínima continua funcionando, mas sugestões/metadados/imagens externas ficam vazios.

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/games/library` | `limit=24`, `cursor?` | `GamesLibraryResponse` |
| `GET /api/games/stats` | - | `GamesStatsResponse` |
| `GET /api/games/search` | `q`, `limit=40` | `GamesSearchResponse` |
| `GET /api/games/{gameId}` | `gameId` | `GameDetailsResponse` |
| `GET /api/games/slug/{slug}` | `slug` | `GameDetailsResponse` |
| `GET /api/games/catalog/suggestions` | `q` | `GameCatalogSuggestionsResponse` |
| `POST /api/games/catalog` | body com `title`, `year?`, `igdbId?` | `ManualGameCatalogCreateResponse` |
| `POST /api/games/{gameId}/sessions` | body com sessão | `GameSessionCreateResponse` |
| `PATCH /api/games/{gameId}/sessions/{sessionId}` | body com sessão | `GameSessionDto` |
| `DELETE /api/games/{gameId}/sessions/{sessionId}` | `gameId`, `sessionId` | vazio |

## Paginação e limites

- `library` retorna `nextCursor`; trate o cursor como opaco
- `limit` de `library` e `search` é normalizado para `1..100`
- detalhes retornam no máximo 100 sessões, ordenadas por `startedAt DESC, sessionId DESC`

## Criação manual

`POST /api/games/catalog`

```json
{
  "title": "Hollow Knight",
  "year": 2017,
  "igdbId": "11156"
}
```

`igdbId` e `year` são opcionais. Quando `igdbId` é informado, o backend tenta preencher ano, descrição e IDs externos a partir do IGDB.

## Sessões

`POST /api/games/{gameId}/sessions`

```json
{
  "status": "PLAYING",
  "startedAt": "2026-05-15T20:00:00Z",
  "endedAt": null,
  "notes": "Comecei uma nova campanha."
}
```

Status aceitos:

- `PLAYING`
- `BACKLOG`
- `COMPLETED`
- `ABANDONED`

Cada sessão tem início obrigatório e fim opcional. O histórico aceita múltiplas sessões para o mesmo game.

- se `endedAt` vier antes de `startedAt`, a API retorna `400`
- se `gameId` ou `sessionId` não existir ou não pertencer ao game, a API retorna `404`

Para finalizar ou abandonar um ciclo em andamento, atualize a mesma sessão com `PATCH /api/games/{gameId}/sessions/{sessionId}` em vez de criar outra sessão:

```json
{
  "status": "COMPLETED",
  "startedAt": "2026-05-16T20:00:00Z",
  "endedAt": "2026-05-21T23:00:00Z",
  "notes": "Campanha finalizada."
}
```

## Invariantes

- games são entradas de catálogo local com sessões manuais
- `GameDetailsResponse.rating` e `GameDetailsResponse.comments` podem incluir dados cross-domain de Ratings e Comments
- provedores externos enriquecem metadados e imagens; o catálogo local continua sendo o contrato canônico

## Non-goals

- não há endpoint de importação automática de sessões de jogo neste contrato
- não há deduplicação de sessões por período documentada no código

## Critérios de aceite

- endpoints documentados existem em `GamesController` ou `GameCatalogController`
- status aceitos batem com `GameSessionStatusDto`
- cursor é tratado como contrato opaco
