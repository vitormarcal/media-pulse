# Migração incremental de identificadores externos

## Intenção

Substituir progressivamente a tabela polimórfica `external_identifiers` por identificadores armazenados nas entidades às quais pertencem.

A migração será incremental, do domínio de menor risco para o de maior risco. Cada etapa deve poder ser implementada, validada e entregue independentemente, inclusive em dias diferentes. A tabela `external_identifiers` continuará existindo enquanto houver algum domínio que dependa dela.

Não é objetivo executar uma migração única nem remover antecipadamente estruturas compartilhadas ainda utilizadas.

## Motivação

`external_identifiers` representa o destino por meio de `entity_type` e `entity_id`, sem uma chave estrangeira real para a entidade correspondente. Isso:

- permite referências órfãs;
- espalha condicionais de tipo e provider pelas consultas;
- dificulta expressar cardinalidades diferentes;
- torna mudanças em um domínio dependentes de uma tabela compartilhada por todos;
- impede que o banco garanta integralmente a relação entre o identificador e sua entidade.

O estado atual já contém referências órfãs de `BOOK_EDITION`. A auditoria também confirmou que uma faixa local pode possuir vários IDs do Spotify ou do MusicBrainz, portanto nem todos os identificadores podem ser convertidos em uma única coluna escalar.

## Estratégia

Para cada domínio:

1. auditar combinações, cardinalidade, duplicidades e referências órfãs;
2. criar as colunas ou tabelas específicas de destino;
3. copiar os dados existentes sem remover a origem;
4. alterar leituras e gravações da aplicação para o novo destino;
5. testar os fluxos do domínio;
6. comparar origem e destino;
7. remover de `external_identifiers` somente os registros daquele domínio;
8. registrar neste documento o resultado e o novo status.

Colunas escalares serão usadas apenas quando a relação for comprovadamente `0..1`. Relações `0..N` usarão tabelas específicas com chave estrangeira real, em vez de arrays ou perda de identificadores.

## Status

Estados possíveis:

- `NÃO INICIADO`: apenas planejado;
- `EM AUDITORIA`: dados e usos no código estão sendo levantados;
- `EM MIGRAÇÃO`: novo destino criado ou aplicação em transição;
- `VALIDANDO`: aplicação já usa o novo destino e a equivalência está sendo conferida;
- `CONCLUÍDO`: domínio não lê nem grava em `external_identifiers` e seus registros antigos foram tratados;
- `BLOQUEADO`: existe uma decisão ou inconsistência documentada que impede continuar.

| Ordem | Etapa | Risco | Status | Destino pretendido | Observações |
|---:|---|---|---|---|---|
| 1 | Livros e edições | Baixo | CONCLUÍDO | `book_editions.isbn_10` e `book_editions.isbn_13` | As colunas contêm os dados canônicos. Registros válidos e órfãos do modelo genérico foram removidos pela migration `V33`. |
| 2 | Jogos | Baixo | CONCLUÍDO | `games.igdb_id` e `games.steamgriddb_id` | As colunas contêm os dados canônicos; vínculos genéricos são removidos pela migration `V34`. |
| 3 | Filmes | Moderado | CONCLUÍDO | `movies.tmdb_id` e `movies.imdb_id` | As colunas contêm os dados canônicos; vínculos genéricos são removidos pela migration `V35`. |
| 4 | Séries | Moderado | CONCLUÍDO | `tv_shows.tmdb_id`, `tv_shows.tvdb_id` e `tv_shows.imdb_id` | As colunas contêm os dados canônicos; vínculos genéricos são removidos pela migration `V36`. |
| 5 | Episódios | Moderado/alto | NÃO INICIADO | `tv_episodes.tmdb_id`, `tv_episodes.tvdb_id` e `tv_episodes.imdb_id` | Volume maior e impacto direto na ingestão e no histórico Plex. |
| 6 | Artistas | Moderado | NÃO INICIADO | `artists.spotify_id` e `artists.musicbrainz_artist_id` | IDs MusicBrainz legados sem tipo devem ser auditados como candidatos a `ARTIST`. |
| 7 | Álbuns | Alto | NÃO INICIADO | Colunas para Spotify e MusicBrainz ou tabela específica, conforme cardinalidade | Separar `RELEASE_GROUP` de `RELEASE`; confirmar se um álbum possui vários releases. |
| 8 | Faixas | Alto | NÃO INICIADO | `track_spotify_ids` e `track_musicbrainz_recording_ids` | Relações `0..N` confirmadas; uma coluna escalar causaria perda de dados. |
| 9 | Limpeza compartilhada | Alto | NÃO INICIADO | Remoção de `external_identifiers` e do modelo genérico | Somente depois de todas as etapas anteriores estarem concluídas. |

## Mapeamento planejado

| Origem atual | Destino planejado |
|---|---|
| `ARTIST / SPOTIFY` | `artists.spotify_id` |
| `ARTIST / MUSICBRAINZ / ARTIST` | `artists.musicbrainz_artist_id` |
| `ALBUM / SPOTIFY` | `albums.spotify_id`, se a cardinalidade permitir |
| `ALBUM / MUSICBRAINZ / RELEASE_GROUP` | `albums.musicbrainz_release_group_id`, se a cardinalidade permitir |
| `ALBUM / MUSICBRAINZ / RELEASE` | `albums.musicbrainz_release_id` ou tabela específica |
| `TRACK / SPOTIFY` | `track_spotify_ids` |
| `TRACK / MUSICBRAINZ / RECORDING` | `track_musicbrainz_recording_ids` |
| `BOOK_EDITION / ISBN_10` | `book_editions.isbn_10` |
| `BOOK_EDITION / ISBN_13` | `book_editions.isbn_13` |
| `MOVIE / TMDB` | `movies.tmdb_id` |
| `MOVIE / IMDB` | `movies.imdb_id` |
| `SHOW / TMDB` | `tv_shows.tmdb_id` |
| `SHOW / TVDB` | `tv_shows.tvdb_id` |
| `SHOW / IMDB` | `tv_shows.imdb_id` |
| `EPISODE / TMDB` | `tv_episodes.tmdb_id` |
| `EPISODE / TVDB` | `tv_episodes.tvdb_id` |
| `EPISODE / IMDB` | `tv_episodes.imdb_id` |
| `GAME / IGDB` | `games.igdb_id` |
| `GAME / STEAMGRIDDB` | `games.steamgriddb_id` |

MusicBrainz legado com `external_entity_type IS NULL` será tratado por contexto, após validação de cada etapa:

- `ARTIST` como candidato a `ARTIST`;
- `ALBUM` como candidato a `RELEASE`;
- `TRACK` como candidato a `RECORDING`.

## Critério global de conclusão

A migração estará concluída quando:

- nenhum código ler ou gravar `external_identifiers`;
- todos os identificadores válidos estiverem representados no destino correto;
- referências órfãs e conflitos tiverem uma decisão registrada;
- testes dos fluxos afetados estiverem passando;
- `external_identifiers` puder ser removida por uma migration Flyway própria;
- `ExternalIdentifier`, seu repository e enums exclusivos do modelo genérico puderem ser removidos sem afetar outros usos.

## Registro de progresso

Ao avançar uma etapa, atualizar sua linha na tabela de status e adicionar uma entrada abaixo. Registrar migrations, principais arquivos alterados, validações executadas, divergências encontradas e decisões tomadas.

### 2026-07-19 — Planejamento inicial

- intenção de migração incremental registrada;
- ordem definida do menor para o maior risco;
- combinações existentes em `external_identifiers` auditadas;
- referências órfãs de `BOOK_EDITION` identificadas;
- multiplicidade de IDs Spotify e MusicBrainz por faixa confirmada;
- nenhuma migração de dados ou alteração de comportamento iniciada.

### 2026-07-19 — Auditoria de livros e edições

- etapa movida para `EM AUDITORIA`;
- encontrados 148 identificadores `ISBN_10` e 148 identificadores `ISBN_13` em `external_identifiers`;
- somente 7 identificadores de cada provider apontam para edições existentes; todos coincidem, após normalização, com `book_editions.isbn_10` ou `book_editions.isbn_13`;
- os outros 141 identificadores de cada provider são órfãos;
- 140 órfãos de cada provider correspondem pelo ISBN a 140 edições atuais, indicando resíduos de edições recriadas;
- um par órfão, da antiga edição `277` (`ISBN_10` `4199802185` e `ISBN_13` `9784199802188`), não corresponde a nenhuma edição atual e não deve ser copiado;
- não foram encontrados mais de um identificador do mesmo provider por edição, conflitos entre vínculos válidos e colunas, nem ISBNs repetidos entre edições atuais;
- 140 edições atuais possuem cada ISBN na coluna específica sem vínculo genérico equivalente, pois a restrição única `(provider, external_id)` ainda está ocupada pelos registros órfãos;
- no código, consultas do domínio de livros já leem os ISBNs diretamente de `book_editions`;
- a ingestão Hardcover grava os ISBNs nas colunas e também tenta gravá-los em `external_identifiers` com `ON CONFLICT DO NOTHING`, mascarando o conflito com registros órfãos;
- nenhuma migration ou alteração de comportamento foi realizada nesta auditoria.

### 2026-07-19 — Conclusão de livros e edições

- criada a migration `V33__remove_book_external_identifiers.sql`;
- a migration remove todos os registros com `entity_type = 'BOOK_EDITION'`, incluindo 14 vínculos válidos e 282 referências órfãs encontrados na auditoria;
- o par órfão da antiga edição `277` não foi copiado porque não possui edição atual correspondente;
- `ISBN_10` e `ISBN_13` foram removidos da constraint de providers de `external_identifiers` e do enum `Provider`;
- `BOOK_EDITION` foi removido da constraint de tipos de `external_identifiers` e do enum `EntityType`;
- a ingestão Hardcover deixou de gravar na tabela genérica e continua persistindo ISBNs diretamente em `book_editions.isbn_10` e `book_editions.isbn_13`;
- removido de `HardcoverNativeRepository` o método de gravação de identificadores genéricos, que era exclusivo desse fluxo;
- testes de persistência de edição passaram a verificar explicitamente a inserção e atualização das duas colunas de ISBN;
- validação executada com `GRADLE_USER_HOME=/tmp/media-pulse-gradle ./gradlew ktlintFormat test -PskipIntegrationTests` no diretório `server`: build concluído com sucesso;
- etapa marcada como `CONCLUÍDO`: o domínio de livros não lê nem grava `external_identifiers`.

### 2026-07-19 — Início da auditoria de jogos

- etapa movida para `EM AUDITORIA`;
- o schema atual de `games` ainda não possui colunas próprias para IGDB ou SteamGridDB;
- `ManualGameCatalogService` procura jogos existentes pelo ID do IGDB e grava vínculos de IGDB e SteamGridDB em `external_identifiers`;
- `ManualGameCatalogCreateFlowService` lê os vínculos genéricos para compor a resposta da criação manual;
- `GameQueryRepository` lê os vínculos genéricos para compor os detalhes do jogo;
- não foram encontrados outros fluxos de jogos que leiam ou gravem identificadores externos;
- não foram encontrados testes específicos do domínio de jogos; a futura migração deverá cobrir deduplicação por IGDB, persistência dos dois providers e exposição dos IDs nas respostas;
- existem 21 jogos no catálogo; todos possuem exatamente um identificador IGDB e um identificador SteamGridDB;
- os 21 IDs de cada provider são distintos e referenciam jogos existentes;
- não foram encontradas duplicidades por jogo e provider, IDs repetidos, referências órfãs, valores vazios, valores não numéricos, providers inesperados ou `external_entity_type` preenchido;
- a cardinalidade `0..1` foi confirmada para ambos os providers, portanto `games.igdb_id` e `games.steamgriddb_id` são destinos escalares seguros;
- a auditoria de dados foi concluída antes do início da migration.

### 2026-07-19 — Conclusão de jogos

- criada a migration `V34__migrate_game_external_identifiers.sql`;
- adicionadas as colunas anuláveis e únicas `games.igdb_id` e `games.steamgriddb_id`;
- a migration valida dados inválidos, cardinalidade e referências órfãs, copia os identificadores e confere a equivalência antes de remover a origem;
- os registros `GAME` são removidos de `external_identifiers` somente depois da cópia validada;
- `GAME` e o `BOOK` residual foram removidos da constraint de tipos de `external_identifiers`; ambos permanecem no enum `EntityType` por serem usados fora do modelo genérico;
- `IGDB` e `STEAMGRIDDB` foram removidos da constraint de providers e do enum `Provider`, pois não possuem outros usos no modelo genérico;
- `ManualGameCatalogService` passou a buscar e gravar os identificadores diretamente em `games`;
- `ManualGameCatalogCreateFlowService` e `GameQueryRepository` passaram a expor os identificadores a partir das novas colunas, preservando os contratos HTTP;
- adicionados testes para deduplicação por IGDB, persistência dos dois providers e exposição dos IDs na resposta do catálogo;
- validação executada com `GRADLE_USER_HOME=/tmp/media-pulse-gradle ./gradlew ktlintFormat test -PskipIntegrationTests` no diretório `server`: build concluído com sucesso;
- etapa marcada como `CONCLUÍDO`: o domínio de jogos não lê nem grava `external_identifiers`.

### 2026-07-19 — Auditoria de filmes

- etapa movida para `EM AUDITORIA`;
- existem 335 filmes e 671 vínculos `MOVIE` em `external_identifiers`: 337 registros TMDb e 334 registros IMDb;
- 333 filmes possuem um vínculo TMDb e um IMDb, um filme possui somente TMDb e um caso possui cardinalidade TMDb inesperada;
- não foram encontrados IDs externos repetidos entre filmes, valores nulos, vazios ou com espaços, IDs IMDb em formato inválido, providers inesperados ou `external_entity_type` preenchido;
- o filme `147` possuía `TMDB / 1096648` e `IMDB / tt27121876` corretos, além de uma duplicata mal classificada `TMDB / tt27121876`; a duplicata inválida foi removida manualmente, sem perda de informação;
- existia uma referência órfã `TMDB / 1401184` para o antigo filme `323`; como não havia entidade local ou outro vínculo correspondente, ela foi removida manualmente;
- depois de desconsiderar a duplicata mal classificada e a referência órfã, cada filme possui no máximo um ID de cada provider; `movies.tmdb_id` e `movies.imdb_id` são destinos escalares seguros;
- o catálogo manual deduplica por TMDb ou IMDb e grava os dois providers na tabela genérica;
- a importação da biblioteca Plex e o processamento de scrobbles Plex gravam GUIDs TMDb e IMDb na tabela genérica;
- o enriquecimento de filmes lê e grava os vínculos, enquanto detalhes, termos, créditos, empresas e coleções usam especialmente o vínculo TMDb;
- os contratos HTTP expõem os identificadores como uma lista e devem ser preservados durante a migração;
- nenhuma migration ou alteração de comportamento foi realizada nesta auditoria.

### 2026-07-19 — Conclusão de filmes

- criada a migration `V35__migrate_movie_external_identifiers.sql`;
- adicionadas as colunas anuláveis e únicas `movies.tmdb_id` e `movies.imdb_id`;
- a duplicata mal classificada `TMDB / tt27121876` do filme `147` e a referência órfã `TMDB / 1401184` para o antigo filme `323` foram removidas manualmente antes da migration;
- a migration rejeita novos dados inválidos, cardinalidade inesperada e referências órfãs, copia os vínculos válidos e confere a equivalência antes de remover a origem;
- os registros `MOVIE` são removidos de `external_identifiers` somente depois da cópia validada;
- `MOVIE` foi removido da constraint de tipos de `external_identifiers`, mas permanece no enum `EntityType` por ser usado em avaliações, comentários e outros modelos de domínio;
- `TMDB` e `IMDB` permanecem na constraint de providers e no enum `Provider`, pois séries e episódios ainda dependem deles;
- catálogo manual, importação e scrobbles Plex, enriquecimento, detalhes, termos, créditos, empresas e coleções passaram a usar as colunas de `movies`;
- os contratos HTTP que expõem identificadores como lista foram preservados;
- documentação da API de filmes e da ingestão Plex foi alinhada ao novo modelo;
- validação executada com `GRADLE_USER_HOME=/tmp/media-pulse-gradle ./gradlew ktlintFormat test -PskipIntegrationTests` no diretório `server`: build concluído com sucesso;
- etapa marcada como `CONCLUÍDO`: o domínio de filmes não lê nem grava `external_identifiers`.

### 2026-07-19 — Início da auditoria de séries

- etapa movida para `EM AUDITORIA`;
- o schema atual de `tv_shows` ainda não possui colunas próprias para TMDb, TVDB ou IMDb;
- existem 77 séries e 206 vínculos `SHOW` em `external_identifiers`: 76 registros TMDb, 65 TVDB e 65 IMDb;
- 65 séries possuem exatamente um identificador de cada provider, 11 séries de origem manual possuem somente TMDb e uma série importada do Plex não possui identificadores externos;
- a série sem identificadores possui episódios e histórico válidos e deve permanecer com as três futuras colunas nulas;
- não foram encontrados mais de um identificador do mesmo provider por série, IDs repetidos, referências órfãs, valores nulos, vazios, com espaços ou em formato inválido, providers inesperados ou `external_entity_type` preenchido;
- a cardinalidade `0..1` foi confirmada para os três providers, portanto `tv_shows.tmdb_id`, `tv_shows.tvdb_id` e `tv_shows.imdb_id` são destinos escalares seguros;
- a importação Plex deduplica séries por TMDb e depois TVDB e grava GUIDs TMDb, TVDB e IMDb;
- o catálogo manual deduplica por TMDb ou TVDB, grava esses providers quando fornecidos e expõe a lista de identificadores na resposta;
- detalhes de séries, criação de watches em séries existentes e detalhes de temporada leem os vínculos genéricos para preservar contratos e resolver o ID TMDb;
- enriquecimento de temporadas e sincronização de créditos leem o vínculo TMDb, e o enriquecimento também pode criá-lo quando ainda não existe;
- a auditoria de dados foi concluída antes do início da migration;
- nenhuma migration ou alteração de comportamento foi realizada.

### 2026-07-19 — Conclusão de séries

- criada a migration `V36__migrate_show_external_identifiers.sql`;
- adicionadas as colunas anuláveis e únicas `tv_shows.tmdb_id`, `tv_shows.tvdb_id` e `tv_shows.imdb_id`;
- a migration rejeita dados inválidos, cardinalidade inesperada e referências órfãs, copia os vínculos válidos e confere a equivalência antes de remover a origem;
- os 206 vínculos `SHOW` auditados são removidos de `external_identifiers` somente depois da cópia validada;
- `SHOW` foi removido da constraint de tipos de `external_identifiers`, mas permanece no enum `EntityType` por ser usado em avaliações, comentários e outros modelos de domínio;
- `TMDB`, `TVDB` e `IMDB` permanecem na constraint de providers e no enum `Provider`, pois episódios ainda dependem deles;
- importação Plex, catálogo manual, enriquecimento de temporadas, sincronização de créditos, detalhes e respostas de criação passaram a usar as colunas de `tv_shows`;
- os identificadores de episódios continuam em `external_identifiers` e não foram alterados nesta etapa;
- os contratos HTTP que expõem identificadores como lista foram preservados;
- documentação da ingestão Plex foi alinhada ao novo modelo;
- validação executada com `GRADLE_USER_HOME=/tmp/media-pulse-gradle ./gradlew ktlintFormat test -PskipIntegrationTests` no diretório `server`: build concluído com sucesso;
- etapa marcada como `CONCLUÍDO`: o domínio de séries não lê nem grava `external_identifiers`.
