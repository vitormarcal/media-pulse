# Movies API

O contrato transversal de enriquecimento automático de filmes e séries está descrito em [`features/automatic-media-enrichment.md`](features/automatic-media-enrichment.md). Esta página registra os detalhes específicos do domínio de filmes.

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
| `GET /api/people/{slug}` | `slug` | `PersonDetailsResponse` com snapshot local do perfil |
| `GET /api/people/{personId}/filmography` | `personId` | `PersonFilmographyResponse` com snapshot local |
| `GET /api/people/{personId}/show-filmography` | `personId` | `PersonShowFilmographyResponse` com snapshot local |
| `GET /api/movies/companies/{slug}` | `slug` | `MovieCompanyDetailsResponse` |
| `GET /api/movies/lists` | - | `MovieListSummaryDto[]` |
| `GET /api/movies/lists/{slug}` | `slug` | `MovieListDetailsResponse` |
| `GET /api/movies/collections` | - | `MovieCollectionSummaryDto[]` |
| `GET /api/movies/collections/{collectionId}` | `collectionId` | `MovieCollectionMembersResponse` com snapshot local |
| `GET /api/movies/companies/{companyId}/members` | `companyId` | `MovieCompanyMembersResponse` com snapshot local |
| `GET /api/people/search` | `q`, `limit=8` | `PersonSuggestionDto[]` |
| `GET /api/movies/terms/{kind}/{slug}` | `kind=genre|tag`, `slug` | `MovieTermDetailsResponse` |
| `GET /api/movies/terms/search` | `q`, `kind=genre|tag`, `limit=8` | `MovieTermSuggestionDto[]` |
| `GET /api/movies/search` | `q`, `limit=10` | `MoviesSearchResponse` |
| `GET /api/movies/summary` | `range=month|year|custom`, `start?`, `end?` | `MoviesSummaryResponse` |
| `GET /api/movies/stats` | - | `MoviesStatsResponse` |
| `GET /api/movies/year/{year}` | `limitWatched=200`, `limitUnwatched=200` | `MoviesByYearResponse` |
| `POST /api/movies/catalog/suggestions` | `q` | `MovieCatalogSuggestionsResponse` |
| `POST /api/admin/movies/collections/{collectionId}/tmdb-members` | `collectionId` | `MovieCollectionMembersResponse` |
| `POST /api/admin/movies/companies/{companyId}/tmdb-members` | `companyId` | `MovieCompanyMembersResponse` |
| `POST /api/movies/catalog` | body com `title`, `year?`, `tmdbId?`, `imdbId?` | `ManualMovieCatalogCreateResponse` |
| `POST /api/admin/movies/collections/backfill` | `limit=50` | `MovieCollectionBackfillResponse` |
| `POST /api/movies/{movieId}/watches` | body com `watchedAt` | `ManualMovieWatchCreateResponse` |
| `DELETE /api/movies/{movieId}/watches/{watchId}` | `movieId`, `watchId` | vazio |
| `POST /api/movies/lists` | body com `name`, `description?` | `MovieListSummaryDto` |
| `POST /api/movies/{movieId}/lists` | body com `listId?`, `name?`, `description?` | `MovieListSummaryDto` |
| `DELETE /api/movies/{movieId}/lists/{listId}` | `movieId`, `listId` | vazio |
| `POST /api/movies/lists/{listId}/order` | `listId`, body com `movieIds[]` | vazio |
| `PATCH /api/movies/lists/{listId}/cover` | `listId`, body com `coverMovieId?` | `MovieListSummaryDto` |
| `POST /api/admin/movies/{movieId}/companies/sync-tmdb` | `movieId` | `MovieCompaniesSyncResponse` |
| `POST /api/admin/movies/companies/sync-tmdb` | `limit=100` | `MovieCompaniesBatchSyncResponse` |
| `POST /api/admin/movies/{movieId}/credits/sync-tmdb` | `movieId` | `MovieCreditsSyncResponse` |
| `POST /api/admin/movies/credits/sync-tmdb` | `limit=100` | `MovieCreditsBatchSyncResponse` |
| `POST /api/movies/{movieId}/credits/tmdb-candidates` | `movieId` | `MovieTmdbCreditCandidatesResponse` |
| `POST /api/movies/{movieId}/credits/from-tmdb` | body com `personTmdbId`, `creditType`, `department?`, `job?`, `characterName?`, `billingOrder?` | `PersonCreditDto` |
| `POST /api/movies/{movieId}/people` | body com `personId`, `group`, `roleLabel?` | `PersonCreditDto` |
| `POST /api/admin/movies/{movieId}/terms/sync-tmdb` | `movieId` | `MovieTermsSyncResponse` |
| `POST /api/admin/movies/terms/sync-tmdb` | `limit=100` | `MovieTermsBatchSyncResponse` |
| `POST /api/movies/{movieId}/terms` | body com `name`, `kind=GENRE|TAG` | `MovieTermDto` |
| `POST /api/movies/{movieId}/terms/{termId}/visibility` | body com `hidden` | `MovieTermDto` |
| `POST /api/movies/terms/{termId}/visibility` | body com `hidden` | `MovieTermDto` |
| `POST /api/movies/{movieId}/enrichment/preview` | body com `tmdbId?` | `MovieEnrichmentPreviewResponse` |
| `POST /api/movies/{movieId}/enrichment/apply` | body com `tmdbId?`, `mode`, `fields[]` | `MovieEnrichmentApplyResponse` |
| `POST /api/admin/people/{personId}/tmdb-filmography` | `personId` | `PersonFilmographyResponse` |
| `POST /api/admin/people/{personId}/tmdb-show-filmography` | `personId` | `PersonShowFilmographyResponse` |

## Paginação e limites

- `library` e `recent` são paginados por cursor retornado no payload
- trate `cursor` como opaco
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
- se `movieId` não existir, retorna `404`

`DELETE /api/movies/{movieId}/watches/{watchId}` remove uma sessão de watch existente.

- retorna `404` se a sessão não existir ou não pertencer ao filme informado
- não remove o filme do catálogo

## Catálogo e enriquecimento

`POST /api/movies/catalog/suggestions` busca correspondências no TMDb para apoiar a criação de catálogo pela UI.

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

Resolução do catálogo:

1. `tmdbId`
2. `imdbId`
3. fingerprint por `title + year`
4. criação local de catálogo quando necessário

`POST /api/movies/{movieId}/enrichment/preview` compara o estado atual do filme com uma sugestão do TMDb.

- se o filme já tiver vínculo `TMDB`, o body pode omitir `tmdbId`
- se ainda não tiver vínculo, o caller deve informar `tmdbId`
- o preview retorna campos comparáveis e a sugestão de imagens

`POST /api/movies/{movieId}/enrichment/apply` aplica a sugestão do TMDb em dois modos:

- `mode=MISSING`: só preenche lacunas
- `mode=SELECTED`: aplica apenas os campos explicitamente escolhidos em `fields[]`
- `imageSelection.selectedKeys` seleciona imagens candidatas
- `imageSelection.primaryKey` define qual imagem selecionada vira primária

Campos suportados no MVP:

- `TITLE`
- `YEAR`
- `DESCRIPTION`
- `TMDB_ID`
- `IMDB_ID`
- `IMAGES`

O detalhe do filme também retorna `enrichment`, com o estado automático agregado e os estados individuais de termos, créditos e empresas:

- `PENDING`: há trabalho aguardando processamento ou nova tentativa
- `COMPLETE`: a etapa foi concluída, inclusive quando o TMDb retornou uma lista vazia
- `BLOCKED`: o filme ainda não possui um vínculo que permita consultar o TMDb

Filmes com apenas IMDb têm o vínculo TMDb resolvido automaticamente. A importação e o registro de sessões não são revertidos quando o provedor está indisponível.

Cada etapa automática registra sua última tentativa e eventual erro em `movies`. Uma falha preserva os dados locais, não impede as outras etapas e só volta a ser elegível para execução automática após um dia. Uma sincronização bem-sucedida limpa o erro da etapa.

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

`POST /api/admin/movies/{movieId}/terms/sync-tmdb` sincroniza termos do TMDb para o filme.

- exige `movies.tmdb_id` já preenchido
- reaproveita termos existentes por `(kind, normalized_name)`
- reativa termos/vínculos que estavam ocultos
- importa `genres` como `GENRE` e `keywords` como `TAG`

`POST /api/admin/movies/terms/sync-tmdb` sincroniza termos do TMDb em lote.

- processa apenas filmes com vínculo `TMDB`
- considera filmes ainda pendentes e elegíveis para tentativa (`movies.terms_synced_at IS NULL`)
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

## Empresas

Cada filme agora pode carregar empresas locais vindas do TMDb, começando por produtoras.

Escopo do sync:

- `PRODUCTION`: produtoras/estúdios vindos de `production_companies` do TMDb

Persistência:

- `movie_companies` guarda a empresa local com `tmdb_id`, `name`, `slug`, `logo_url` e `origin_country`
- `movie_company_assignments` guarda o vínculo filme-empresa com `company_type`

`POST /api/admin/movies/{movieId}/companies/sync-tmdb` sincroniza empresas de um filme.

- exige vínculo `TMDB` no filme
- substitui o recorte local de empresas pelo snapshot atual do TMDb
- marca `movies.companies_synced_at` ao concluir com sucesso

`POST /api/admin/movies/companies/sync-tmdb` sincroniza empresas em lote.

- processa apenas filmes com vínculo `TMDB`
- considera apenas pendentes elegíveis para tentativa (`movies.companies_synced_at IS NULL`)
- `limit` é normalizado entre `1` e `1000`
- falhas individuais não interrompem o lote

`GET /api/movies/companies/{slug}` abre a página local da empresa.

- retorna a empresa e os filmes do catálogo ligados a ela

Os filmes externos de uma empresa são persistidos em `movie_company_members` por um worker automático.

- o worker processa empresas ainda sem snapshot e repete falhas após um dia
- abrir a página não consulta o TMDb e não escreve no banco
- o vínculo com o catálogo é resolvido dinamicamente pelo `tmdb_id`; nenhum filme é catalogado automaticamente

`GET /api/movies/companies/{companyId}/members` retorna somente o snapshot local.

`POST /api/admin/movies/companies/{companyId}/tmdb-members` força a atualização do snapshot como mecanismo de reparo explícito.

- usa `discover/movie` com `with_companies`
- substitui atomicamente o snapshot persistido

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

`POST /api/admin/movies/{movieId}/credits/sync-tmdb` sincroniza créditos de um filme.

- exige vínculo `TMDB` no filme
- substitui o recorte local de créditos pelo snapshot atual do TMDb
- marca `movies.credits_synced_at` ao concluir com sucesso

`POST /api/admin/movies/credits/sync-tmdb` sincroniza créditos em lote.

- processa apenas filmes com vínculo `TMDB`
- considera apenas pendentes elegíveis para tentativa (`movies.credits_synced_at IS NULL`)
- `limit` é normalizado entre `1` e `1000`
- falhas individuais não interrompem o lote

`POST /api/movies/{movieId}/credits/tmdb-candidates` expande créditos extras do TMDb para a página do filme.

- olha além do recorte principal já usado no sync automático
- tenta reconciliar automaticamente pessoas que já existem localmente
- retorna apenas os créditos que ainda exigem decisão explícita da UI

`POST /api/movies/{movieId}/credits/from-tmdb` incorpora um crédito específico mostrado nessa expansão.

- reaproveita a pessoa local se ela já existir por `tmdb_id`
- cria a pessoa se ela ainda não estiver persistida
- salva o vínculo filme-pessoa sem precisar rerodar o sync completo

`GET /api/people/{slug}` abre a página local da pessoa sem consultar provedores externos.

- retorna a pessoa, os papéis locais agregados e os filmes e séries ligados a ela
- quando o enriquecimento automático terminou, inclui em `tmdbProfile` o snapshot local de biografia, datas, origem, aliases e links
- pessoas com `tmdb_id` e sem snapshot são processadas em segundo plano; falhas são registradas e repetidas com intervalo mínimo de um dia
- a leitura da página nunca consulta o TMDb

`GET /api/people/search` busca pessoas já persistidas localmente.

- usa `people.normalized_name`
- serve para reaproveitar uma pessoa existente antes de criar ou importar novos créditos

`POST /api/movies/{movieId}/people` vincula uma pessoa já existente ao filme.

- reaproveita `people` local
- aceita grupos editoriais simples: `DIRECTORS`, `WRITERS`, `CAST`, `OTHER`
- `roleLabel` é opcional em `WRITERS` e `CAST`, e obrigatório em `OTHER`

As filmografias de filmes e séries são persistidas localmente por um worker automático.

- cada tipo possui snapshot e estado de sincronização independentes
- falhas preservam o último snapshot e são repetidas após um dia
- abrir ou explorar a página nunca consulta o TMDb nem grava no banco
- a presença no catálogo é resolvida dinamicamente pelo `tmdb_id`
- nenhum filme, série ou crédito canônico é criado pela leitura da filmografia

`GET /api/people/{personId}/filmography` retorna o snapshot local de filmes.

`POST /api/admin/people/{personId}/tmdb-filmography` força a atualização desse snapshot como reparo explícito.

- permite à UI mostrar o que já existe e o que ainda pode ser adicionado explicitamente

`GET /api/people/{personId}/show-filmography` retorna o snapshot local de séries.

`POST /api/admin/people/{personId}/tmdb-show-filmography` força a atualização desse snapshot como reparo explícito.

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

`GET /api/movies/collections/{collectionId}` retorna o snapshot local dos membros oficiais da coleção e resolve quais filmes já estão no catálogo.

- não consulta o TMDb durante a leitura
- o painel embutido na página de filme também usa este GET ao expandir a coleção
- coleções ainda não sincronizadas são processadas automaticamente em segundo plano
- falhas aguardam pelo menos um dia antes de uma nova tentativa automática

`POST /api/admin/movies/collections/{collectionId}/tmdb-members` força a atualização dos membros no TMDb como mecanismo de reparo explícito.

- substitui atomicamente o snapshot local dos membros externos
- a leitura cruza o snapshot com `movies.tmdb_id`
- cada membro informa `inCatalog`, `localMovieId`, `localSlug` e `tmdbUrl`
- a UI usa esse payload para mostrar filmes ausentes e permitir adição explícita ao catálogo

`POST /api/admin/movies/collections/backfill` atualiza filmes existentes em lote.

- seleciona filmes com identificador `TMDB` e sem `collection_id`
- marca filmes sem `belongs_to_collection` como verificados para não repetir o mesmo candidato indefinidamente
- `limit` é normalizado entre `1` e `500`
- retorna contadores de candidatos, processados, vinculados, sem coleção e falhas

## Invariantes

- cada filme admite no máximo um `tmdb_id` e um `imdb_id`, armazenados diretamente em `movies`
- cada ID externo é único dentro de seu provider e não pode identificar dois filmes locais
- TMDb e IMDb identificam o mesmo filme canônico local e podem ser usados para deduplicação; o catálogo não depende de consultar esses provedores durante a leitura
- `MovieDetailsResponse.rating` e `MovieDetailsResponse.comments` podem incluir dados cross-domain de Ratings e Comments
- watches manuais usam `source=MANUAL`
- endpoints `sync-tmdb` são ações explícitas de enriquecimento provider-specific
- endpoints de leitura retornam catálogo local, não uma proxy direta do TMDb

## Non-goals

- listas manuais não substituem coleções oficiais TMDb
- `POST /api/movies/{movieId}/watches` não cria catálogo

## Critérios de aceite

- endpoints documentados existem em `MoviesController`, `MovieCatalogController` ou `PeopleController`
- DTOs citados existem em `api/movies`
- comportamento de provider externo é descrito como enriquecimento, não como fonte canônica
