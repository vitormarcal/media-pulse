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
| 1 | Livros e edições | Baixo | NÃO INICIADO | `book_editions.isbn_10` e `book_editions.isbn_13` | As colunas já existem. Há referências órfãs em `external_identifiers`, que não devem ser copiadas. |
| 2 | Jogos | Baixo | NÃO INICIADO | `games.igdb_id` e `games.steamgriddb_id` | Auditoria inicial encontrou 21 IDs de cada provider. |
| 3 | Filmes | Moderado | NÃO INICIADO | `movies.tmdb_id` e `movies.imdb_id` | Atualizar catálogo manual, importação Plex, enriquecimentos e consultas. |
| 4 | Séries | Moderado | NÃO INICIADO | `tv_shows.tmdb_id`, `tv_shows.tvdb_id` e `tv_shows.imdb_id` | Deve ser concluída antes da etapa de episódios. |
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
