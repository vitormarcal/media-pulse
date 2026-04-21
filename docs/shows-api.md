# Shows API

A Shows API expõe consulta read-only da biblioteca e do histórico agregado de séries, além de ingestão manual idempotente de episode watches.

## Escopo e origem dos dados

- o histórico de séries é preenchido por eventos Plex `media.scrobble` com `Metadata.type=episode`
- o import de biblioteca no startup cadastra `tv_shows` e `tv_episodes`, mas não cria linhas em `tv_episode_watches`
- contagens de série são agregadas a partir de watches de episódios

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/shows/library` | `limit=20`, `cursor?` | `ShowsLibraryResponse` |
| `GET /api/shows/recent` | `limit=20`, `cursor?` | `ShowsRecentResponse` |
| `GET /api/shows/currently-watching` | `limit=20`, `activeWithinDays=90` | lista de `CurrentlyWatchingShowDto` |
| `GET /api/shows/{showId}` | `showId` | `ShowDetailsResponse` |
| `GET /api/shows/slug/{slug}` | `slug` | `ShowDetailsResponse` |
| `GET /api/shows/slug/{slug}/seasons/{seasonNumber}` | `slug`, `seasonNumber` | `ShowSeasonDetailsResponse` |
| `GET /api/shows/search` | `q`, `limit=10` | `ShowsSearchResponse` |
| `GET /api/shows/summary` | `range=month|year|custom`, `start?`, `end?` | `ShowsSummaryResponse` |
| `GET /api/shows/stats` | - | `ShowsStatsResponse` |
| `GET /api/shows/year/{year}` | `limitWatched=200`, `limitUnwatched=200` | `ShowsByYearResponse` |
| `GET /api/shows/catalog/suggestions` | `q` | `ShowCatalogSuggestionsResponse` |
| `POST /api/shows/catalog` | body com `title`, `year?`, `tmdbId?`, `tvdbId?`, `importEpisodes=true` | `ManualShowCatalogCreateResponse` |
| `POST /api/shows/{showId}/seasons/{seasonNumber}/enrichment/preview` | body com `tmdbId?` | `ShowSeasonEnrichmentPreviewResponse` |
| `POST /api/shows/{showId}/seasons/{seasonNumber}/enrichment/apply` | body com `tmdbId?`, `mode`, `seasonFields`, `episodeFields` | `ShowSeasonEnrichmentApplyResponse` |
| `POST /api/shows/{showId}/watches` | body com `watchedAt`, `episodeTitle`, `seasonNumber?`, `episodeNumber?`, `originallyAvailableAt?` | `ManualShowWatchCreateResponse` |
| `POST /api/shows/watches` | body com `watchedAt`, `showTitle`, `episodeTitle`, `seasonNumber?`, `episodeNumber?`, `year?`, `tmdbId?`, `tvdbId?` | `ManualShowWatchCreateResponse` |

## Paginação e limites

- `library` e `recent` são paginados por cursor retornado no payload
- `currently-watching` exige `limit >= 1` e `activeWithinDays >= 1`
- `limitWatched` e `limitUnwatched` são truncados para no máximo `1000`

## Range temporal

`GET /api/shows/summary` aceita:

- `month`: últimos 30 dias
- `year`: últimos 365 dias
- `custom`: exige `start` e `end`

`GET /api/shows/year/{year}` aceita anos entre `1900` e `ano UTC atual + 1`.

O range anual é:

- início: `01/01/{year} 00:00:00Z`
- fim: `31/12/{year} 23:59:59Z`

## Semântica de contagem

- `watchesCount`: conta linhas em `tv_episode_watches`
- `uniqueShowsCount`: conta séries distintas com watch no período
- `rewatchesCount = watchesCount - uniqueShowsCount`
- `currently-watching` considera séries com atividade recente e retorna progresso agregado

## Enriquecimento de temporada

`POST /api/shows/{showId}/seasons/{seasonNumber}/enrichment/preview` compara os episódios existentes da temporada com o TMDb.

- usa o vínculo `TMDB` salvo na série ou o `tmdbId` enviado no body
- não cria episódios ou temporadas faltantes
- marca como lacuna títulos genéricos como `Episode 5`, `Episódio 5` e `Ep. 5`
- compara nome da temporada, título do episódio, descrição, duração e data original

`POST /api/shows/{showId}/seasons/{seasonNumber}/enrichment/apply` aplica o preview.

- `mode=MISSING`: preenche apenas lacunas seguras
- `mode=SELECTED`: aplica os campos selecionados no payload
- atualiza somente linhas existentes de `tv_episodes`
- vincula o `tmdbId` à série quando ele foi informado e ainda não existe vínculo

Campos aceitos:

- `SEASON_TITLE`
- `EPISODE_TITLE`
- `EPISODE_SUMMARY`
- `EPISODE_DURATION`
- `EPISODE_AIR_DATE`

## Catálogo manual

`GET /api/shows/catalog/suggestions` busca sugestões no TMDb usando o título informado em `q`.

`POST /api/shows/catalog` cria ou consolida uma série sem registrar watch.

- uso esperado: trazer uma série nova para a biblioteca antes de marcar episódios assistidos
- resolução de série: `tmdbId`, `tvdbId`, fingerprint por `title + year`
- quando `tmdbId` existir, o serviço tenta preencher título, ano, descrição, poster/backdrop e vínculo externo
- com `importEpisodes=true`, importa temporadas numeradas a partir de `1` e episódios disponíveis no TMDb
- a importação de episódios não cria linhas em `tv_episode_watches`
- episódios existentes são reaproveitados por `(show_id, season_number, episode_number)` ou fingerprint

## Ingestão manual

### Série existente

`POST /api/shows/{showId}/watches` registra um episódio manual dentro de uma série já existente.

- uso esperado: página de detalhe da série, lacuna de histórico antigo, correção manual
- o endpoint resolve a série apenas por `showId`
- o endpoint nunca cria `tv_shows`; se `showId` não existir, retorna `404`
- o episódio é reaproveitado por fingerprint ou por `(show_id, season_number, episode_number)`
- watches manuais são persistidos com `source=MANUAL`

### Resolução ou criação de catálogo

`POST /api/shows/watches` é o fluxo solto para resolver ou criar a série a partir dos dados enviados.

Ordem de resolução de série:

1. `tmdbId`
2. `tvdbId`
3. fingerprint por `showTitle + year`
4. resolução ou criação do episódio via fingerprint ou `(show_id, season_number, episode_number)`

Regras importantes:

- watches manuais são persistidos com `source=MANUAL`
- quando `tmdbId` existir, o serviço tenta preencher metadados do show e baixar poster/backdrop
