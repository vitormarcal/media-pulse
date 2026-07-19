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
| 5 | Episódios | Moderado/alto | CONCLUÍDO | `tv_episodes.tmdb_id`, `tv_episodes.tvdb_id` e `tv_episodes.imdb_id` | As colunas contêm os dados canônicos; vínculos genéricos são removidos pela migration `V37`. |
| 6 | Artistas | Moderado | CONCLUÍDO | `artists.spotify_id` e `artists.musicbrainz_artist_id` | As colunas contêm os dados canônicos; vínculos genéricos são removidos pela migration `V38`. |
| 7 | Álbuns | Alto | CONCLUÍDO | `albums.musicbrainz_release_group_id`, `album_spotify_ids` e `album_musicbrainz_release_ids` | Release group é a identidade canônica; Spotify e releases são aliases técnicos. Vínculos genéricos são removidos pela migration `V39`. |
| 8 | Faixas | Alto | NÃO INICIADO | `track_spotify_ids` e `track_musicbrainz_recording_ids` | Relações `0..N` confirmadas; uma coluna escalar causaria perda de dados. |
| 9 | Limpeza compartilhada | Alto | NÃO INICIADO | Remoção de `external_identifiers` e do modelo genérico | Somente depois de todas as etapas anteriores estarem concluídas. |

## Mapeamento planejado

| Origem atual | Destino planejado |
|---|---|
| `ARTIST / SPOTIFY` | `artists.spotify_id` |
| `ARTIST / MUSICBRAINZ / ARTIST` | `artists.musicbrainz_artist_id` |
| `ALBUM / SPOTIFY` | `album_spotify_ids` como aliases técnicos `0..N` |
| `ALBUM / MUSICBRAINZ / RELEASE_GROUP` | `albums.musicbrainz_release_group_id` como identidade canônica `0..1` |
| `ALBUM / MUSICBRAINZ / RELEASE` | `album_musicbrainz_release_ids` como aliases técnicos `0..N` |
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

### Identidade canônica e aliases técnicos de álbuns

Para o produto, um álbum representa o trabalho geral, sem distinguir país, mídia, reedição, remaster ou outra edição comercial. Por isso, o identificador MusicBrainz canônico de `albums` é o `RELEASE_GROUP`: ele representa o conceito geral do álbum e pode agrupar vários `RELEASE`.

MusicBrainz `RELEASE` e IDs de álbum do Spotify são aliases técnicos de ingestão, não identidades funcionais exibidas pelo produto. Eles devem ser preservados em relações `0..N` porque Plex ou Spotify podem fornecer IDs diferentes para edições que convergem no mesmo álbum canônico. Esses aliases permitem deduplicar novas importações e localizar o álbum sem repetir resolução remota, mas não significam que o Media Pulse acompanhe qual edição específica pertence ao usuário.

O fluxo esperado é:

1. receber um release MusicBrainz do Plex ou um ID de álbum do Spotify;
2. localizar o álbum por um alias já conhecido quando possível;
3. para um release MusicBrainz novo, resolver seu `RELEASE_GROUP`;
4. usar `albums.musicbrainz_release_group_id` como identidade canônica;
5. manter o ID recebido somente como alias técnico associado ao álbum.

### Evolução futura dos aliases Spotify

A seleção da edição Spotify usada como fonte de tracklist não faz parte desta migração e não bloqueia sua conclusão. O comportamento atual, que pode processar mais de um alias do mesmo álbum no backfill, já existia em `external_identifiers` e foi preservado.

Uma futura evolução do domínio Spotify deve permitir associar aliases manualmente ao mesmo álbum canônico, especialmente para remasters, relançamentos, edições regionais e mudanças de ID ao longo do tempo. Essa feature também deverá definir qual alias fornece a tracklist principal, impedir que tracklists de edições diferentes disputem posições e oferecer revisão antes de unir trabalhos potencialmente distintos. A referência conceitual é o tratamento de títulos alternativos no domínio de livros: uma entidade canônica com múltiplas representações conhecidas.

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

### 2026-07-19 — Início da auditoria de episódios

- etapa movida para `EM AUDITORIA`;
- o schema atual de `tv_episodes` ainda não possui colunas próprias para TMDb, TVDB ou IMDb;
- a importação da biblioteca Plex procura episódios por vínculos genéricos, persiste GUIDs TMDb, TVDB e IMDb e usa esses vínculos antes do fingerprint e da posição na temporada para deduplicação;
- o processamento de scrobbles Plex repete a procura e a gravação dos três providers antes de persistir o watch;
- a importação manual de temporadas via TMDb grava o identificador TMDb de cada episódio na tabela genérica;
- não foram encontradas leituras de identificadores de episódio nos detalhes de série ou temporada; nesses fluxos, `EPISODE` também é usado como tipo de avaliações e não poderá ser removido do enum `EntityType` nesta etapa;
- os testes existentes de importação e scrobble Plex cobrem parte dos fluxos afetados e deverão ser revisados após a auditoria dos dados;
- as consultas de auditoria da base foram preparadas para validar volume, cobertura, cardinalidade, duplicidades, referências órfãs, formatos e combinações inesperadas;
- nenhuma migration ou alteração de comportamento foi realizada; a cardinalidade e a segurança das três colunas escalares ainda dependem do resultado da auditoria da base.

### 2026-07-19 — Conclusão da auditoria de episódios

- existem 2.134 episódios e 4.963 vínculos `EPISODE` em `external_identifiers`: 1.995 registros TMDb, 1.607 TVDB e 1.361 IMDb;
- 1.998 episódios possuem ao menos um identificador e 136 não possuem nenhum; os episódios sem identificadores devem permanecer com as três futuras colunas nulas;
- 1.359 episódios possuem os três providers, 245 possuem TMDb e TVDB, 389 possuem somente TMDb, três possuem somente TVDB e dois possuem TMDb e IMDb;
- cada episódio possui no máximo um identificador de cada provider e cada identificador externo é distinto dentro de seu provider;
- não foram encontradas referências órfãs, conflitos com entidades de outros domínios, providers inesperados, `external_entity_type` preenchido, valores nulos, vazios, com espaços ou em formato inválido;
- as contagens por provider, por combinação e por cobertura são equivalentes e não indicam vínculos fora das combinações esperadas;
- a cardinalidade `0..1` foi confirmada para os três providers, portanto `tv_episodes.tmdb_id`, `tv_episodes.tvdb_id` e `tv_episodes.imdb_id` são destinos escalares seguros;
- a auditoria de dados foi concluída antes do início da migration; nenhuma alteração de schema ou comportamento foi realizada.

### 2026-07-19 — Conclusão de episódios

- criada a migration `V37__migrate_episode_external_identifiers.sql`;
- adicionadas as colunas anuláveis e únicas `tv_episodes.tmdb_id`, `tv_episodes.tvdb_id` e `tv_episodes.imdb_id`;
- a migration rejeita dados inválidos, cardinalidade inesperada e referências órfãs, copia os vínculos válidos e confere a equivalência antes de remover a origem;
- os 4.963 vínculos `EPISODE` auditados são removidos de `external_identifiers` somente depois da cópia validada;
- `EPISODE` foi removido da constraint de tipos de `external_identifiers`, mas permanece no enum `EntityType` por ser usado em avaliações e outros modelos de domínio;
- `TMDB`, `TVDB` e `IMDB` foram removidos da constraint de providers de `external_identifiers`, mas permanecem no enum `Provider` por serem usados nas integrações e nos modelos específicos;
- importação da biblioteca Plex, processamento de scrobbles e importação manual de temporadas via TMDb passaram a procurar e gravar identificadores diretamente em `tv_episodes`;
- a prioridade de deduplicação por identificador externo, fingerprint e posição na temporada foi preservada, assim como a persistência de watches;
- documentação da ingestão Plex foi alinhada ao novo modelo;
- validação executada com `GRADLE_USER_HOME=/tmp/media-pulse-gradle ./gradlew ktlintFormat test -PskipIntegrationTests` no diretório `server`: build concluído com sucesso;
- etapa marcada como `CONCLUÍDO`: o domínio de episódios não lê nem grava `external_identifiers`.

### 2026-07-19 — Início da auditoria de artistas

- etapa movida para `EM AUDITORIA`;
- o schema atual de `artists` ainda não possui colunas próprias para Spotify ou MusicBrainz;
- `CanonicalizationService` procura artistas por identificador MusicBrainz ou Spotify, cria o artista quando necessário e grava os dois providers em `external_identifiers`;
- a importação de reproduções do Spotify fornece o identificador Spotify ao fluxo de canonicalização; a importação e os scrobbles de música do Plex fornecem o identificador MusicBrainz quando presente;
- `MusicBrainzPageEnrichmentService` procura e grava vínculos MusicBrainz tipados como `ARTIST` nos fluxos de criação manual de artista, aplicação de candidato, enriquecimento de álbum e importação de discografia;
- a página de artista lê o vínculo MusicBrainz tipado para expor o identificador e a importação de discografia exige esse vínculo;
- `CanonicalizationServiceTest` e `MusicBrainzPageEnrichmentServiceTest` já cobrem parte da deduplicação e da gravação dos identificadores; os testes deverão ser adaptados para validar as futuras colunas específicas;
- foram preparadas consultas somente de leitura para auditar volume, cobertura, cardinalidade, duplicidades, referências órfãs, formatos, combinações inesperadas e identificadores MusicBrainz legados sem tipo;
- nenhuma migration ou alteração de comportamento foi realizada; a segurança das duas colunas escalares depende do resultado da auditoria da base.

### 2026-07-19 — Conclusão de artistas

- existem 5.807 artistas e 550 vínculos `ARTIST` em `external_identifiers`: 505 registros Spotify e 45 registros MusicBrainz;
- 515 artistas possuem ao menos um identificador e 5.292 não possuem nenhum; os artistas sem identificadores permanecem com as duas novas colunas nulas;
- os vínculos MusicBrainz incluem 24 registros tipados como `ARTIST` e 21 registros legados sem tipo;
- os 21 MusicBrainz legados são UUIDs válidos, apontam para artistas existentes, não competem com vínculos tipados e foram tratados como identificadores de artista;
- cada artista possui no máximo um identificador de cada provider e cada identificador externo é distinto dentro de seu provider;
- não foram encontradas referências órfãs, duplicidades globais, providers ou tipos inesperados, valores nulos, vazios, com espaços ou em formato inválido;
- criada a migration `V38__migrate_artist_external_identifiers.sql`;
- adicionadas as colunas anuláveis e únicas `artists.spotify_id` e `artists.musicbrainz_artist_id`;
- a migration rejeita dados inválidos, cardinalidade inesperada e referências órfãs, copia os vínculos válidos e confere a equivalência antes de remover a origem;
- os 550 vínculos `ARTIST` são removidos de `external_identifiers` somente depois da cópia validada;
- `ARTIST` foi removido da constraint de tipos de `external_identifiers`, mas permanece no enum `EntityType` por ser usado em avaliações, comentários e outros modelos de domínio;
- canonicalização, importações Spotify e Plex, criação e vinculação MusicBrainz, importação de discografia e página do artista passaram a usar as colunas de `artists`;
- os contratos HTTP existentes foram preservados;
- validação executada com `GRADLE_USER_HOME=/tmp/media-pulse-gradle ./gradlew ktlintFormat test -PskipIntegrationTests` no diretório `server`: build concluído com sucesso;
- etapa marcada como `CONCLUÍDO`: o domínio de artistas não lê nem grava `external_identifiers`.

### 2026-07-19 — Auditoria de dados de álbuns

- etapa movida para `EM AUDITORIA`;
- existem 12.218 álbuns e 790 vínculos `ALBUM` em `external_identifiers`, distribuídos entre 754 álbuns;
- os vínculos incluem 670 identificadores Spotify em 666 álbuns, 35 MusicBrainz `RELEASE_GROUP`, três MusicBrainz `RELEASE` e 82 MusicBrainz legados sem tipo;
- quatro álbuns possuem dois identificadores Spotify válidos cada; todos possuem faixas e histórico de reprodução Spotify, confirmando que uma coluna escalar causaria perda de dados;
- não foram encontradas referências órfãs, duplicidades globais, providers ou tipos inesperados, valores nulos, vazios, com espaços ou em formato inválido;
- os 82 MusicBrainz legados são tratados como candidatos a `RELEASE`: 79 aparecem isoladamente e três coexistem com um `RELEASE_GROUP`, sem conflito com identificadores tipados;
- 85 álbuns possuem um MusicBrainz release legado ou tipado, 35 possuem release group, 79 possuem release sem release group e 29 possuem release group sem release;
- o índice único tipado atual limita artificialmente cada álbum a um identificador por tipo MusicBrainz e não comprova a cardinalidade natural de releases;
- o destino indicado pela auditoria é `albums.musicbrainz_release_group_id` para a relação `0..1`, `album_spotify_ids` para Spotify `0..N` e `album_musicbrainz_release_ids` para releases `0..N`;
- a identidade canônica do produto foi definida como MusicBrainz `RELEASE_GROUP`; releases MusicBrainz e IDs Spotify são aliases técnicos de ingestão e não representam edições acompanhadas pelo produto;
- `PlexMusicImportService` extrai o primeiro GUID `mbid` do álbum e o entrega sem tipo para `CanonicalizationService`; o fluxo de scrobble Plex não fornece atualmente MBID de álbum;
- `SpotifyPlaybackService` entrega o ID do álbum Spotify para canonicalização; `SpotifyExtendedPlaybackService` não possui ID de álbum e deduplica pelo artista e título normalizado;
- `CanonicalizationService.ensureAlbum` procura primeiro pelo MusicBrainz ou Spotify ID recebido, depois por artista, título e ano, e por fim pelo fingerprint; ao gravar, ambos os providers ainda são persistidos sem tipo pela função genérica `safeLink`;
- `MusicBrainzPageEnrichmentService` pesquisa, aplica e importa discografia por `RELEASE_GROUP`, usa esse vínculo para reconhecer itens já associados e reconcilia um MusicBrainz legado consultando-o como `RELEASE` para obter o release group;
- `MusicBrainzAlbumGenreEnrichmentService` lê o primeiro vínculo MusicBrainz do álbum sem distinguir tipo, enquanto `MusicBrainzApiClient.getAlbumGenreNamesByMbid` pressupõe que o ID recebido seja um `RELEASE`; esse comportamento é ambíguo e pode falhar para álbuns que possuem somente `RELEASE_GROUP`;
- `MusicQueryRepository` expõe na página do álbum apenas o vínculo MusicBrainz `RELEASE_GROUP`, comportamento alinhado à identidade canônica escolhida;
- `SpotifyBackfillQueryRepository` lê todos os IDs Spotify de álbum para completar posições de faixas; álbuns com múltiplos aliases podem aparecer mais de uma vez e o comportamento deve continuar seguro e idempotente;
- `MusicDuplicateReviewRepository` usa `external_identifiers` apenas para vínculos de faixas e não depende dos identificadores de álbum nesta etapa;
- os testes diretamente afetados estão em `CanonicalizationServiceTest`, `MusicBrainzPageEnrichmentServiceTest`, `MusicBrainzAlbumGenreEnrichmentServiceTest` e nos fluxos de importação/reprodução Plex e Spotify; a consulta da página do álbum também deve validar a exposição do release group canônico;
- a auditoria de dados e usos no código foi concluída; nenhuma alteração de schema ou comportamento foi realizada.

### 2026-07-19 — Conclusão de álbuns

- criada a migration `V39__migrate_album_external_identifiers.sql`;
- adicionada a coluna anulável e única `albums.musicbrainz_release_group_id`, usada como identidade canônica do trabalho geral;
- criadas `album_spotify_ids` e `album_musicbrainz_release_ids` com chaves estrangeiras reais para `albums`, unicidade global do ID externo e suporte a aliases `0..N`;
- a migration valida combinações, formatos, referências órfãs e cardinalidade de release group antes da cópia e confere as contagens antes de remover a origem;
- 670 IDs Spotify são copiados para `album_spotify_ids`, 85 releases MusicBrainz tipados ou legados para `album_musicbrainz_release_ids` e 35 release groups para a coluna canônica;
- os registros `ALBUM` são removidos de `external_identifiers` somente depois da cópia validada; a tabela genérica permanece para a futura etapa de faixas;
- a canonicalização passou a deduplicar álbuns pelos aliases específicos e continua usando artista, título, ano e fingerprint como fallbacks;
- a importação da biblioteca Plex resolve o release group antes da canonicalização e somente associa um release novo como alias técnico quando essa validação tem sucesso; releases já associados reutilizam localmente o álbum e seu release group, sem repetir a consulta remota a cada inicialização; divergências com um release group já associado são rejeitadas antes da gravação do alias, e o scrobble Plex continua sem MBID de álbum;
- aplicação e importação de discografia MusicBrainz passaram a ler e gravar diretamente `albums.musicbrainz_release_group_id`;
- o enriquecimento de gêneros passou a consultar diretamente o release group canônico quando disponível e a usar um release técnico somente como fallback;
- a página do álbum continua expondo somente o release group, e o backfill Spotify passou a ler `album_spotify_ids` preservando o suporte a múltiplos aliases;
- testes de canonicalização, resolução de release group, enriquecimento de gêneros, importação Plex e consultas foram adaptados ao novo modelo; a cobertura anterior de página, idempotência, artista, discografia, estados vazios e falhas temporárias MusicBrainz foi preservada;
- validação executada com `GRADLE_USER_HOME=/tmp/media-pulse-gradle ./gradlew ktlintFormat test -PskipIntegrationTests` no diretório `server`: 268 testes concluídos e build bem-sucedido;
- etapa marcada como `CONCLUÍDO`: o domínio de álbuns não lê nem grava `external_identifiers`.
