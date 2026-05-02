# Books API

A Books API fornece visão read-only da biblioteca e das jornadas de leitura consolidadas.

## Modelo de leitura

As leituras são armazenadas como sessões consolidadas, não como eventos brutos de sincronização.

- cada sessão possui `readId` interno
- o payload HTTP não depende de `source_event_id`
- transições de status da mesma jornada são refletidas na própria sessão

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/books/library` | `limit=20`, `cursor?` | `BooksLibraryResponse` |
| `GET /api/books/stats` | - | `BooksStatsResponse` |
| `GET /api/books/year/{year}` | `year` | `YearReadsResponse` |
| `GET /api/books/{bookId}` | `bookId` | `BookDetailsResponse` |
| `GET /api/books/slug/{slug}` | `slug` | `BookDetailsResponse` |
| `GET /api/books/authors/{authorId}` | `authorId` | `AuthorDetailsResponse` |
| `GET /api/books/list` | `status?`, `limit=20`, `cursor?` | `BooksListResponse` |
| `GET /api/books/search` | `q`, `limit=10` | `BooksSearchResponse` |
| `GET /api/books/summary` | `range=month|year|custom`, `start?`, `end?` | `BooksSummaryResponse` |

## Paginação

### `/api/books/library`

Lista a biblioteca de livros em ordem paginada, com `nextCursor` retornado pela API.

- este endpoint alimenta o arquivo principal em `/books?view=archive`
- busca em `/books?q=...` e recorte anual em `/books?year=...` continuam combinando `library`, `search`, `stats` e `year`

### `/api/books/stats`

Retorna agregados do arquivo inteiro para uso de biblioteca e navegação anual.

- `total.booksCount`: quantidade de livros distintos no arquivo
- `total.readsCount`: quantidade total de registros/sessões de leitura
- `total.completedCount`: quantidade total de leituras concluídas
- `unreadCount`: livros que ainda não passaram por leitura efetiva
- `years[]`: série anual agregada por data de leitura/atividade, não por data de cadastro
- `latestActivityAt` e `firstActivityAt`: limites temporais do arquivo consolidado

### `/api/books/list`

O cursor é baseado no `readId` da sessão de leitura, em formato `id:123`.

- use `cursor=id:123` para buscar itens com `id` menor que o último retornado
- o filtro `status` aceita os valores do enum `BookReadStatus`

## Range temporal

`GET /api/books/summary` aceita:

- `month`
- `year`
- `custom` com `start` e `end` em ISO-8601 UTC

`GET /api/books/year/{year}` usa um range UTC fixo:

- início: `01/01/{year} 00:00:00Z`
- fim exclusivo lógico do relatório: início do ano seguinte

## Observações de contrato

- detalhes de livro podem ser buscados por `bookId` ou `slug`
- detalhes de autor retornam catálogo agregado e leituras recentes
- `summary` é agregado por período, não por evento bruto do provedor
- `stats` representa o arquivo inteiro e serve melhor para biblioteca, métricas acumuladas e chips anuais
- `list` e `stats` priorizam a data real de leitura/atividade; data de cadastro não deve empurrar itens antigos para o topo do arquivo
