# Movies API

A Movies API expõe consulta read-only da biblioteca e do histórico de watches, além de ações editoriais de catálogo e sessão manual.

## Escopo e origem dos dados

- o histórico de filmes é preenchido por eventos Plex `media.scrobble` com `Metadata.type=movie`
- o import de biblioteca no startup cadastra metadados, mas não cria linhas em `movie_watches`

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/movies/library` | `limit=20`, `cursor?`, `unwatched=false` | `MoviesLibraryResponse` |
| `GET /api/movies/recent` | `limit=20`, `cursor?` | `MoviesRecentResponse` |
| `GET /api/movies/{movieId}` | `movieId` | `MovieDetailsResponse` |
| `GET /api/movies/slug/{slug}` | `slug` | `MovieDetailsResponse` |
| `GET /api/people/{slug}` | `slug` | `PersonDetailsResponse` |
| `GET /api/movies/companies/{slug}` | `slug` | `MovieCompanyDetailsResponse` |
| `GET /api/movies/lists` | - | `MovieListSummaryDto[]` |
| `GET /api/movies/lists/{slug}` | `slug` | `MovieListDetailsResponse` |
| `GET /api/movies/collections` | - | `MovieCollectionSummaryDto[]` |
| `GET /api/people/search` | `q`, `limit=8` | `PersonSuggestionDto[]` |
| `GET /api/movies/terms/{kind}/{slug}` | `kind=genre|tag`, `slug` | `MovieTermDetailsResponse` |
| `GET /api/movies/terms/search` | `q`, `kind=genre|tag`, `limit=8` | `MovieTermSuggestionDto[]` |
| `GET /api/movies/search` | `q`, `limit=10` | `MoviesSearchResponse` |
| `GET /api/movies/summary` | `range=month|year|custom`, `start?`, `end?` | `MoviesSummaryResponse` |
| `GET /api/movies/stats` | - | `MoviesStatsResponse` |
| `GET /api/movies/year/{year}` | `limitWatched=200`, `limitUnwatched=200` | `MoviesByYearResponse` |
| `GET /api/movies/catalog/suggestions` | `q` | `MovieCatalogSuggestionsResponse` |
| `GET /api/movies/collections/{collectionId}/tmdb-members` | `collectionId` | `MovieCollectionMembersResponse` |
| `GET /api/movies/companies/{companyId}/tmdb-members` | `companyId` | `MovieCompanyMembersResponse` |
| `POST /api/movies/catalog` | body com `title`, `year?`, `tmdbId?`, `imdbId?` | `ManualMovieCatalogCreateResponse` |
| `POST /api/movies/collections/backfill` | `limit=50` | `MovieCollectionBackfillResponse` |
| `POST /api/movies/{movieId}/watches` | body com `watchedAt` | `ManualMovieWatchCreateResponse` |
| `POST /api/movies/lists` | body com `name`, `description?` | `MovieListSummaryDto` |
| `POST /api/movies/{movieId}/lists` | body com `listId?`, `name?`, `description?` | `MovieListSummaryDto` |
| `DELETE /api/movies/{movieId}/lists/{listId}` | `movieId`, `listId` | `204 No Content` |
| `POST /api/movies/lists/{listId}/order` | `listId`, body com `movieIds[]` | `204 No Content` |
| `PATCH /api/movies/lists/{listId}/cover` | `listId`, body com `coverMovieId?` | `MovieListSummaryDto` |
| `POST /api/movies/{movieId}/companies/sync-tmdb` | `movieId` | `MovieCompaniesSyncResponse` |
| `POST /api/movies/companies/sync-tmdb` | `limit=100` | `MovieCompaniesBatchSyncResponse` |
| `POST /api/movies/{movieId}/credits/sync-tmdb` | `movieId` | `MovieCreditsSyncResponse` |
| `POST /api/movies/credits/sync-tmdb` | `limit=100` | `MovieCreditsBatchSyncResponse` |
| `GET /api/movies/{movieId}/credits/tmdb-candidates` | `movieId` | `MovieTmdbCreditCandidatesResponse` |
| `POST /api/movies/{movieId}/credits/from-tmdb` | body com `personTmdbId`, `creditType`, `department?`, `job?`, `characterName?`, `billingOrder?` | `PersonCreditDto` |
| `POST /api/movies/{movieId}/people` | body com `personId`, `group`, `roleLabel?` | `PersonCreditDto` |
| `POST /api/movies/{movieId}/terms/sync-tmdb` | `movieId` | `MovieTermsSyncResponse` |
| `POST /api/movies/terms/sync-tmdb` | `limit=100` | `MovieTermsBatchSyncResponse` |
| `POST /api/movies/{movieId}/terms` | body com `name`, `kind=GENRE|TAG` | `MovieTermDto` |
| `POST /api/movies/{movieId}/terms/{termId}/visibility` | body com `hidden` | `MovieTermDto` |
| `POST /api/movies/terms/{termId}/visibility` | body com `hidden` | `MovieTermDto` |
| `POST /api/movies/{movieId}/enrichment/preview` | body com `tmdbId?` | `MovieEnrichmentPreviewResponse` |
| `POST /api/movies/{movieId}/enrichment/apply` | body com `tmdbId?`, `mode`, `fields[]` | `MovieEnrichmentApplyResponse` |
| `GET /api/people/{personId}/tmdb-filmography` | `personId` | `PersonFilmographyResponse` |

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

`POST /api/movies/{movieId}/watches` registra uma sessão manual em um filme já existente.

- uso esperado: cinema, memória antiga, lacuna de histórico ou correção manual
- a inserção continua idempotente por `(movie_id, source=MANUAL, watched_at)`
- o endpoint não recria nem recatalogra o filme; só acrescenta a sessão

Resolução do filme:

1. `tmdbId`
2. `imdbId`
3. fingerprint por `title + year`
4. criação de `movies` e `movie_titles` com `source=MANUAL`

Regras importantes:

- deduplicação por `(source, movie_id, watched_at)`
- quando `tmdbId` existir, o serviço tenta preencher metadados faltantes, baixar imagens do TMDb e vincular a coleção oficial do filme quando houver `belongs_to_collection`

## Catálogo e enriquecimento

`GET /api/movies/catalog/suggestions` busca correspondências no TMDb para apoiar a criação de catálogo pela UI.

- retorna cards curtos com `tmdbId`, `title`, `originalTitle`, `year`, `overview` e `posterUrl`
- o fluxo esperado é: buscar por nome, escolher uma sugestão, salvar o catálogo já com contexto externo
- se nenhuma sugestão servir, a UI pode cair para criação manual

`POST /api/movies/catalog` cria ou reaproveita um filme sem registrar sessão.

Uso esperado:

- criar uma entrada já ancorada no TMDb quando houver correspondência
- cair para manual apenas quando a busca externa não ajudar
- consolidar ids externos antes do primeiro watch
- abrir um detalhe de filme utilizável mesmo sem histórico de sessão
- vincular automaticamente a coleção/franquia oficial do TMDb quando o filme pertencer a uma

`POST /api/movies/{movieId}/enrichment/preview` compara o estado atual do filme com uma sugestão do TMDb.

- se o filme já tiver vínculo `TMDB`, o body pode omitir `tmdbId`
- se ainda não tiver vínculo, o caller deve informar `tmdbId`
- o preview retorna campos comparáveis e a sugestão de imagens

`POST /api/movies/{movieId}/enrichment/apply` aplica a sugestão do TMDb em dois modos:

- `mode=MISSING`: só preenche lacunas
- `mode=SELECTED`: aplica apenas os campos explicitamente escolhidos em `fields[]`

Campos suportados no MVP:

- `TITLE`
- `YEAR`
- `DESCRIPTION`
- `TMDB_ID`
- `IMDB_ID`
- `IMAGES`

## Termos de filmes

Cada filme agora pode ter termos de classificação editáveis em duas famílias:

- `GENRE`: classificação ampla e relativamente estável
- `TAG`: recorte mais livre, temático ou pessoal

Persistência:

- `movie_terms` guarda o termo global com `kind`, `source` e `hidden`
- `movie_term_assignments` vincula termo ao filme com `source` e `hidden` por filme

Fontes:

- `TMDB`: importado a partir de `genres` e `keywords` do TMDb
- `USER`: criado manualmente pela UI

Visibilidade:

- `hidden` em `movie_terms` oculta o termo globalmente
- `hidden` em `movie_term_assignments` oculta só naquele filme
- termos ocultos continuam persistidos e podem ser reativados depois

`POST /api/movies/{movieId}/terms/sync-tmdb` sincroniza termos do TMDb para o filme.

- exige vínculo `TMDB` já presente em `external_identifiers`
- reaproveita termos existentes por `(kind, normalized_name)`
- reativa termos/vínculos que estavam ocultos
- importa `genres` como `GENRE` e `keywords` como `TAG`

`POST /api/movies/terms/sync-tmdb` sincroniza termos do TMDb em lote.

- processa apenas filmes com vínculo `TMDB`
- considera apenas filmes ainda pendentes de sync em lote (`movies.terms_synced_at IS NULL`)
- `limit` é normalizado entre `1` e `1000`
- cada filme roda isoladamente; falha de um item não interrompe o lote
- a resposta retorna `candidates`, `processed`, `synced` e `failed`

`POST /api/movies/{movieId}/terms` adiciona um termo manualmente ao filme.

- cria o termo se ainda não existir para aquele `kind`
- reaproveita o termo global se ele já existir
- reativa vínculos ocultos em vez de duplicar

`GET /api/movies/terms/search` busca termos já existentes para apoiar a edição.

- filtra por `kind`
- ordena primeiro por nome exato, depois por termos visíveis
- serve para a UI sugerir reaproveitamento antes de criar uma nova marcação

`GET /api/movies/terms/{kind}/{slug}` abre a página de navegação de um termo.

- `kind` aceita `genre` ou `tag`
- retorna o termo e os filmes ativos ligados a ele
- o resultado exclui termos ocultos globalmente e vínculos ocultos no filme

## Pessoas e créditos

## Empresas

Cada filme agora pode carregar empresas locais vindas do TMDb, começando por produtoras.

Escopo do sync:

- `PRODUCTION`: produtoras/estúdios vindos de `production_companies` do TMDb

Persistência:

- `movie_companies` guarda a empresa local com `tmdb_id`, `name`, `slug`, `logo_url` e `origin_country`
- `movie_company_assignments` guarda o vínculo filme-empresa com `company_type`

`POST /api/movies/{movieId}/companies/sync-tmdb` sincroniza empresas de um filme.

- exige vínculo `TMDB` no filme
- substitui o recorte local de empresas pelo snapshot atual do TMDb
- marca `movies.companies_synced_at` ao concluir com sucesso

`POST /api/movies/companies/sync-tmdb` sincroniza empresas em lote.

- processa apenas filmes com vínculo `TMDB`
- considera apenas pendentes (`movies.companies_synced_at IS NULL`)
- `limit` é normalizado entre `1` e `1000`
- falhas individuais não interrompem o lote

`GET /api/movies/companies/{slug}` abre a página local da empresa.

- retorna a empresa e os filmes do catálogo ligados a ela

`GET /api/movies/companies/{companyId}/tmdb-members` expande o catálogo da empresa no TMDb.

- usa `discover/movie` com `with_companies`
- cruza o resultado com o catálogo local
- se um filme já existir localmente, reconcilia o vínculo filme-empresa antes de responder

## Listas manuais

Listas manuais são o primeiro nível de curadoria própria do catálogo.

Persistência:

- `movie_lists` guarda nome, `slug` e descrição opcional
- `movie_list_items` guarda os filmes ligados à lista e a posição explícita

`GET /api/movies/lists` retorna as listas já criadas.

- inclui contagem de filmes por lista
- inclui `coverMovieId`, `coverUrl` e um preview curto de filmes para a UI
- serve para a UI oferecer anexação rápida a partir da página do filme

`GET /api/movies/lists/{slug}` abre a página de um recorte manual.

- retorna a lista, a capa escolhida quando houver e os filmes na ordem salva

`POST /api/movies/lists` cria uma nova lista manual.

- exige `name`
- `description` é opcional

`POST /api/movies/{movieId}/lists` adiciona o filme a uma lista.

- se `listId` vier preenchido, anexa a uma lista existente
- se `listId` vier nulo, cria uma nova lista com `name` e já anexa o filme

`DELETE /api/movies/{movieId}/lists/{listId}` remove o filme da lista.

`POST /api/movies/lists/{listId}/order` atualiza a ordem manual completa da lista.

- exige `movieIds[]` com exatamente os mesmos filmes já ligados à lista
- a ordem persistida passa a valer tanto na página da lista quanto no destaque principal do recorte

`PATCH /api/movies/lists/{listId}/cover` fixa manualmente a imagem principal da lista.

- aceita `coverMovieId` nulo para voltar ao padrão automático
- quando nenhum filme é escolhido, a UI usa a imagem do primeiro item da ordem
- se o filme escolhido sair da lista, a capa fixa é limpa automaticamente

## Pessoas e créditos

Cada filme agora pode carregar um recorte controlado de pessoas vindas do TMDb.

Escopo do sync:

- `CAST`: só top billed, limitado aos primeiros nomes por `order`
- `CREW`: apenas cargos relevantes como `Director`, `Writer`, `Screenplay`, `Story`, `Editor`, `Producer`, `Director of Photography` e `Original Music Composer`

Persistência:

- `people` guarda a pessoa local com `tmdb_id`, `name`, `slug` e `profile_url`
- `movie_credits` guarda os vínculos filme-pessoa com `credit_type`, `job`, `department`, `character_name` e `billing_order`

`POST /api/movies/{movieId}/credits/sync-tmdb` sincroniza créditos de um filme.

- exige vínculo `TMDB` no filme
- substitui o recorte local de créditos pelo snapshot atual do TMDb
- marca `movies.credits_synced_at` ao concluir com sucesso

`POST /api/movies/credits/sync-tmdb` sincroniza créditos em lote.

- processa apenas filmes com vínculo `TMDB`
- considera apenas pendentes (`movies.credits_synced_at IS NULL`)
- `limit` é normalizado entre `1` e `1000`
- falhas individuais não interrompem o lote

`GET /api/movies/{movieId}/credits/tmdb-candidates` expande créditos extras do TMDb para a página do filme.

- olha além do recorte principal já usado no sync automático
- tenta reconciliar automaticamente pessoas que já existem localmente
- retorna apenas os créditos que ainda exigem decisão explícita da UI

`POST /api/movies/{movieId}/credits/from-tmdb` incorpora um crédito específico mostrado nessa expansão.

- reaproveita a pessoa local se ela já existir por `tmdb_id`
- cria a pessoa se ela ainda não estiver persistida
- salva o vínculo filme-pessoa sem precisar rerodar o sync completo

`GET /api/people/{slug}` abre a página local da pessoa.

- retorna a pessoa, os papéis locais agregados, os filmes do catálogo ligados a ela e um bloco `tmdbProfile` com biografia e metadados editoriais quando o TMDb responder

`GET /api/people/search` busca pessoas já persistidas localmente.

- usa `people.normalized_name`
- serve para reaproveitar uma pessoa existente antes de criar ou importar novos créditos

`POST /api/movies/{movieId}/people` vincula uma pessoa já existente ao filme.

- reaproveita `people` local
- aceita grupos editoriais simples: `DIRECTORS`, `WRITERS`, `CAST`, `OTHER`
- `roleLabel` é opcional em `WRITERS` e `CAST`, e obrigatório em `OTHER`

`GET /api/people/{personId}/tmdb-filmography` expande a filmografia externa da pessoa.

- cruza a filmografia do TMDb com os filmes já catalogados localmente
- quando encontra um filme já local, reaproveita essa oportunidade para persistir o vínculo `movie_credits` da pessoa com o filme
- permite à UI mostrar o que já existe e o que ainda pode ser adicionado explicitamente

## Coleções oficiais TMDb

Filmes podem ser vinculados a uma coleção oficial do TMDb, como `The Matrix Collection`.

- o schema guarda `movie_collections.tmdb_id` como chave externa estável da coleção
- `movies.collection_id` aponta para a coleção local
- o vínculo é preenchido durante criação de catálogo e enriquecimento por TMDb
- `MovieDetailsResponse.collection` retorna a coleção do filme e os filmes locais já catalogados na mesma coleção
- coleções oficiais não substituem futuras listas pessoais; elas representam apenas `belongs_to_collection` do TMDb

`GET /api/movies/collections` retorna as coleções já consolidadas no catálogo local.

- inclui contagem de filmes e quantos já têm sessão
- inclui preview curto de posters para páginas editoriais e cards de navegação

`GET /api/movies/collections/{collectionId}/tmdb-members` busca os membros da coleção no TMDb sob demanda.

- não persiste snapshot dos membros externos
- cruza os membros retornados com `external_identifiers` locais por `Provider.TMDB`
- cada membro informa `inCatalog`, `localMovieId`, `localSlug` e `tmdbUrl`
- a UI usa esse payload para mostrar filmes ausentes e permitir adição explícita ao catálogo

`POST /api/movies/collections/backfill` atualiza filmes existentes em lote.

- seleciona filmes com identificador `TMDB` e sem `collection_id`
- marca filmes sem `belongs_to_collection` como verificados para não repetir o mesmo candidato indefinidamente
- `limit` é normalizado entre `1` e `500`
- retorna contadores de candidatos, processados, vinculados, sem coleção e falhas
