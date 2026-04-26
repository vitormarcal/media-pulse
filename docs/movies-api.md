# Movies API

A Movies API expõe consulta read-only da biblioteca e do histórico de watches, além de ações editoriais de catálogo e sessão manual.

## Escopo e origem dos dados

- o histórico de filmes é preenchido por eventos Plex `media.scrobble` com `Metadata.type=movie`
- o import de biblioteca no startup cadastra metadados, mas não cria linhas em `movie_watches`

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/movies/library` | `limit=20`, `cursor?` | `MoviesLibraryResponse` |
| `GET /api/movies/recent` | `limit=20`, `cursor?` | `MoviesRecentResponse` |
| `GET /api/movies/{movieId}` | `movieId` | `MovieDetailsResponse` |
| `GET /api/movies/slug/{slug}` | `slug` | `MovieDetailsResponse` |
| `GET /api/movies/terms/{kind}/{slug}` | `kind=genre|tag`, `slug` | `MovieTermDetailsResponse` |
| `GET /api/movies/search` | `q`, `limit=10` | `MoviesSearchResponse` |
| `GET /api/movies/summary` | `range=month|year|custom`, `start?`, `end?` | `MoviesSummaryResponse` |
| `GET /api/movies/stats` | - | `MoviesStatsResponse` |
| `GET /api/movies/year/{year}` | `limitWatched=200`, `limitUnwatched=200` | `MoviesByYearResponse` |
| `GET /api/movies/catalog/suggestions` | `q` | `MovieCatalogSuggestionsResponse` |
| `GET /api/movies/collections/{collectionId}/tmdb-members` | `collectionId` | `MovieCollectionMembersResponse` |
| `POST /api/movies/catalog` | body com `title`, `year?`, `tmdbId?`, `imdbId?` | `ManualMovieCatalogCreateResponse` |
| `POST /api/movies/collections/backfill` | `limit=50` | `MovieCollectionBackfillResponse` |
| `POST /api/movies/{movieId}/watches` | body com `watchedAt` | `ManualMovieWatchCreateResponse` |
| `POST /api/movies/{movieId}/terms/sync-tmdb` | `movieId` | `MovieTermsSyncResponse` |
| `POST /api/movies/terms/sync-tmdb` | `limit=100` | `MovieTermsBatchSyncResponse` |
| `POST /api/movies/{movieId}/terms` | body com `name`, `kind=GENRE|TAG` | `MovieTermDto` |
| `POST /api/movies/{movieId}/terms/{termId}/visibility` | body com `hidden` | `MovieTermDto` |
| `POST /api/movies/terms/{termId}/visibility` | body com `hidden` | `MovieTermDto` |
| `POST /api/movies/{movieId}/enrichment/preview` | body com `tmdbId?` | `MovieEnrichmentPreviewResponse` |
| `POST /api/movies/{movieId}/enrichment/apply` | body com `tmdbId?`, `mode`, `fields[]` | `MovieEnrichmentApplyResponse` |

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

`GET /api/movies/terms/{kind}/{slug}` abre a página de navegação de um termo.

- `kind` aceita `genre` ou `tag`
- retorna o termo e os filmes ativos ligados a ele
- o resultado exclui termos ocultos globalmente e vínculos ocultos no filme

## Coleções oficiais TMDb

Filmes podem ser vinculados a uma coleção oficial do TMDb, como `The Matrix Collection`.

- o schema guarda `movie_collections.tmdb_id` como chave externa estável da coleção
- `movies.collection_id` aponta para a coleção local
- o vínculo é preenchido durante criação de catálogo e enriquecimento por TMDb
- `MovieDetailsResponse.collection` retorna a coleção do filme e os filmes locais já catalogados na mesma coleção
- coleções oficiais não substituem futuras listas pessoais; elas representam apenas `belongs_to_collection` do TMDb

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
