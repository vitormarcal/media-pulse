# Games API

Games usam entrada manual: a UI chama sugestões do IGDB e, ao criar, o backend tenta enriquecer metadados pelo IGDB e imagens pelo SteamGridDB.

## Configuração

- `IGDB_CLIENT_ID` e `IGDB_CLIENT_SECRET`: credenciais Twitch/IGDB para obter token OAuth client credentials.
- `STEAMGRIDDB_API_KEY`: chave da API SteamGridDB para buscar grids.
- `IGDB_ENABLED` e `STEAMGRIDDB_ENABLED`: permitem desligar cada integração sem remover a UI.

Sem credenciais, a entrada mínima continua funcionando, mas sugestões/metadados/imagens externas ficam vazios.

## Endpoints

- `GET /api/games/library?limit=24&cursor=...`
- `GET /api/games/stats`
- `GET /api/games/search?q=hollow&limit=40`
- `GET /api/games/{gameId}`
- `GET /api/games/slug/{slug}`
- `GET /api/games/catalog/suggestions?q=hollow`
- `POST /api/games/catalog`
- `POST /api/games/{gameId}/sessions`
- `DELETE /api/games/{gameId}/sessions/{sessionId}`

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
