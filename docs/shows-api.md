# Shows API

O comportamento transversal de enriquecimento automático de filmes e séries está descrito em [`features/automatic-media-enrichment.md`](features/automatic-media-enrichment.md). Esta página registra os detalhes específicos do domínio de séries.

A Shows API expõe consulta read-only da biblioteca e do histórico agregado de séries, além de ingestão manual idempotente de episode watches.

## Escopo e origem dos dados

- o histórico de séries é preenchido por eventos Plex `media.scrobble` com `Metadata.type=episode`
- o import de biblioteca no startup cadastra `tv_shows` e `tv_episodes`, mas não cria linhas em `tv_episode_watches`
- contagens de série são agregadas a partir de watches de episódios

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/shows/library` | `limit=20`, `cursor?`, `unwatched=false` | `ShowsLibraryResponse` |
| `GET /api/shows/recent` | `limit=20`, `cursor?` | `ShowsRecentResponse` |
| `GET /api/shows/currently-watching` | `limit=20`, `activeWithinDays=90` | lista de `CurrentlyWatchingShowDto` |
| `GET /api/shows/{showId}` | `showId` | `ShowDetailsResponse` |
| `GET /api/shows/slug/{slug}` | `slug` | `ShowDetailsResponse` |
| `GET /api/shows/lists` | - | `ShowListSummaryDto[]` |
| `GET /api/shows/lists/{slug}` | `slug` | `ShowListDetailsResponse` |
| `GET /api/shows/slug/{slug}/seasons/{seasonNumber}` | `slug`, `seasonNumber` | `ShowSeasonDetailsResponse` |
| `GET /api/shows/search` | `q`, `limit=10` | `ShowsSearchResponse` |
| `GET /api/shows/summary` | `range=month|year|custom`, `start?`, `end?` | `ShowsSummaryResponse` |
| `GET /api/shows/stats` | - | `ShowsStatsResponse` |
| `GET /api/shows/year/{year}` | `limitWatched=200`, `limitUnwatched=200` | `ShowsByYearResponse` |
| `POST /api/shows/catalog/suggestions` | `q` | `ShowCatalogSuggestionsResponse` |
| `POST /api/shows/lists` | body com `name`, `description?` | `ShowListSummaryDto` |
| `POST /api/shows/{showId}/lists` | body com `listId?`, `name?`, `description?` | `ShowListSummaryDto` |
| `DELETE /api/shows/{showId}/lists/{listId}` | `showId`, `listId` | vazio |
| `POST /api/shows/lists/{listId}/order` | body com todos os `showIds[]` | vazio |
| `PATCH /api/shows/lists/{listId}/cover` | body com `coverShowId?` | `ShowListSummaryDto` |
| `DELETE /api/shows/lists/{slug}` | `slug` | vazio |
| `POST /api/admin/shows/credits/sync-tmdb` | `limit=100` | `ShowCreditsBatchSyncResponse` |
| `POST /api/admin/shows/{showId}/credits/sync-tmdb` | `showId` | `ShowCreditsSyncResponse` |
| `POST /api/admin/shows/{showId}/terms/sync-tmdb` | `showId` | `ShowTermsSyncResponse` |
| `POST /api/admin/shows/terms/sync-tmdb` | `limit=100` | `ShowTermsBatchSyncResponse` |
| `GET /api/shows/terms/search` | `q`, `kind=GENRE|TAG`, `limit=8` | lista de `ShowTermSuggestionDto` |
| `GET /api/shows/terms/{kind}/{termId}/{slug}` | `kind=GENRE|TAG`, `termId`, `slug` | `ShowTermDetailsResponse` |
| `POST /api/shows/{showId}/terms` | body com `name`, `kind=GENRE|TAG` | `ShowTermDto` |
| `POST /api/shows/{showId}/terms/{termId}/visibility` | body com `hidden` | `ShowTermDto` |
| `POST /api/shows/terms/{termId}/visibility` | body com `hidden` | `ShowTermDto` |
| `POST /api/shows/catalog` | body com `title`, `year?`, `tmdbId?`, `tvdbId?`, `importEpisodes=true` | `ManualShowCatalogCreateResponse` |
| `POST /api/shows/{showId}/seasons/{seasonNumber}/enrichment/preview` | body com `tmdbId?` | `ShowSeasonEnrichmentPreviewResponse` |
| `POST /api/shows/{showId}/enrichment/preview` | body com `tmdbId?` | prévia dos metadados sugeridos pelo TMDb |
| `POST /api/shows/{showId}/enrichment/apply` | body com `tmdbId?`, `mode`, `fields[]` e seleção de imagens | aplica metadados selecionados |
| `POST /api/shows/{showId}/seasons/{seasonNumber}/enrichment/apply` | body com `tmdbId?`, `mode`, `seasonFields`, `episodeFields` | `ShowSeasonEnrichmentApplyResponse` |
| `POST /api/shows/{showId}/watches` | body com `watchedAt`, `episodeTitle`, `seasonNumber?`, `episodeNumber?`, `originallyAvailableAt?` | `ManualShowWatchCreateResponse` |
| `POST /api/shows/watches` | body com `watchedAt`, `showTitle`, `episodeTitle`, `seasonNumber?`, `episodeNumber?`, `year?`, `tmdbId?`, `tvdbId?` | `ManualShowWatchCreateResponse` |

## Paginação e limites

- `library` e `recent` são paginados por cursor retornado no payload
- trate `cursor` como opaco
- `library?unwatched=true` retorna apenas séries sem nenhum episódio assistido
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

`POST /api/shows/{showId}/enrichment/preview` compara título, ano, descrição, vínculo e imagens da série com o TMDb. A aplicação aceita `MISSING` para preencher lacunas ou `SELECTED` para substituir somente os campos escolhidos. Depois de aplicar, gêneros, tags e créditos são sincronizados a partir do vínculo TMDb.

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

## Listas manuais

Listas de séries são recortes exclusivos deste domínio e não misturam filmes ou outras mídias.

- `show_lists` guarda nome, nome normalizado único, slug, descrição e capa manual opcional
- `show_list_items` associa cada série uma única vez e persiste sua posição explícita
- criar uma lista com o mesmo nome normalizado reutiliza a lista existente
- adicionar novamente a mesma série é idempotente e não altera sua posição
- a atualização de ordem exige exatamente todos os IDs atualmente pertencentes à lista
- `coverShowId` deve pertencer à lista; `null` remove a escolha manual
- ao remover a série usada como capa, a escolha manual também é removida
- sem capa manual, a UI usa a primeira imagem disponível do recorte
- remover uma associação ou excluir a lista nunca exclui a série

A UI está disponível em `/shows/lists`, `/shows/lists/{slug}` e no bloco `Organizar em listas` da página da série.

## Pessoas e créditos

`POST /api/admin/shows/{showId}/credits/sync-tmdb` sincroniza as pessoas principais da série a partir do TMDb.

- exige vínculo `TMDB` salvo na série
- traz o recorte principal de elenco e equipe relevante
- persiste os vínculos em `show_credits`, reutilizando `people` por `tmdb_id`
- a página da série passa a navegar para `/people/{slug}`
- a página da pessoa agrega esses créditos de série ao lado dos créditos de filme
- funciona como reparo explícito; a página da série não dispara esta operação

Um worker executa esse sync automaticamente para séries pendentes. `POST /api/admin/shows/credits/sync-tmdb?limit=100` mantém o mesmo processamento disponível para reparo em lote.

- considera apenas séries com vínculo `TMDB`
- considera apenas pendentes (`tv_shows.credits_synced_at IS NULL`)
- falhas registram `credits_sync_attempted_at` e `credits_sync_error`, preservam os créditos locais e são repetidas após um dia
- processa no máximo `limit`, truncado em `1000`
- executa cada série em transação isolada, contabilizando `synced` e `failed`
- marca `tv_shows.credits_synced_at` ao concluir com sucesso

## Gêneros e tags

Séries possuem termos locais editáveis em duas famílias: gêneros (`GENRE`) e tags (`TAG`). O worker importa `genres` e `keywords` do TMDb para séries vinculadas e repete falhas após um dia.

- `show_terms` mantém nome, tipo, origem (`TMDB` ou `USER`) e ocultação global
- `show_term_assignments` mantém o vínculo e a ocultação específica da série
- termos manuais são preservados durante novas sincronizações
- termos ocultos continuam armazenados e podem ser restaurados
- a sincronização manual é um mecanismo de curadoria e reparo; o fluxo normal é automático

Termos visíveis são pontos de exploração do catálogo. `GET /api/shows/terms/{kind}/{termId}/{slug}` valida conjuntamente a identidade, o tipo e o slug do termo, então retorna suas séries priorizando atividade pessoal recente e usando título como desempate estável.

- termos ocultos globalmente, tipos inválidos, identidade divergente e termos inexistentes retornam `404`
- associações ocultas são omitidas; quando nenhuma associação visível resta, o recorte retorna `404`
- a página `/shows/terms/{kind}/{termId}/{slug}` reutiliza o grid visual da biblioteca de séries
- os chips visíveis da página da série navegam para esse recorte; o modo de edição preserva suas ações de curadoria

## Catálogo manual

`POST /api/shows/catalog/suggestions` busca sugestões no TMDb usando o título informado em `q`.

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

## Invariantes

- cada série admite no máximo um `tmdb_id`, um `tvdb_id` e um `imdb_id`, armazenados diretamente em `tv_shows`
- cada episódio admite no máximo um identificador de cada um desses provedores, armazenados diretamente em `tv_episodes`
- dentro de cada domínio, um ID externo é único por provider e não pode identificar duas entidades locais
- identificadores externos têm prioridade na deduplicação de episódios; fingerprint e posição na temporada permanecem como fallbacks
- séries e episódios sem identificadores externos continuam válidos no catálogo local
- `ShowDetailsResponse.rating` e `ShowDetailsResponse.comments` podem incluir dados cross-domain de Ratings e Comments
- `ShowSeasonEpisodeDto.rating` pode incluir rating por episódio
- watches manuais usam `source=MANUAL`
- endpoints `sync-tmdb` são ações explícitas de enriquecimento provider-specific
- endpoints de leitura retornam catálogo local, não uma proxy direta do TMDb

## Non-goals

- importação de biblioteca não cria linhas em `tv_episode_watches`
- enriquecimento de temporada não cria episódios ou temporadas faltantes
- não há endpoint documentado para remover watch de episódio

## Critérios de aceite

- endpoints documentados existem em `ShowsController`, `ShowCatalogController` ou `ManualShowWatchController`
- DTOs citados existem em `api/shows`
- cursor é tratado como contrato opaco
