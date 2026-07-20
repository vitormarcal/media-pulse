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

Os endpoints paginados retornam `nextCursor`.

- trate `cursor` como opaco
- envie exatamente o `nextCursor` recebido para buscar a próxima página
- não dependa do formato interno do cursor

### `/api/books/library`

Lista a biblioteca em ordem de atividade consolidada, do mais recente para o mais antigo.

### `/api/books/stats`

Retorna agregados do arquivo inteiro para uso de biblioteca e navegação anual.

- `total.booksCount`: quantidade de livros distintos no arquivo
- `total.readsCount`: quantidade total de registros/sessões de leitura
- `total.completedCount`: quantidade total de leituras concluídas
- `unreadCount`: livros que ainda não passaram por leitura efetiva
- `years[]`: série anual agregada por data de leitura/atividade, não por data de cadastro
- `latestActivityAt` e `firstActivityAt`: limites temporais do arquivo consolidado

### `/api/books/list`

Lista sessões de leitura em ordem de atividade.

- o filtro `status` aceita os valores do enum `BookReadStatus`
- valores aceitos: `READ`, `CURRENTLY_READING`, `WANT_TO_READ`, `DID_NOT_FINISH`, `PAUSED`, `UNKNOWN`

## Range temporal

`GET /api/books/summary` aceita:

- `month`: mês UTC atual
- `year`: ano UTC atual
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

## Invariantes

- a API é read-only para livros
- leituras são sessões consolidadas, não eventos crus de provider
- ISBN-10 e ISBN-13 pertencem à edição e são armazenados diretamente em `book_editions.isbn_10` e `book_editions.isbn_13`
- cada edição admite no máximo um valor de cada tipo de ISBN; esses identificadores não pertencem ao livro canônico
- `BookDetailsResponse.comments` pode incluir comentários cross-domain criados pela Comments API

## Non-goals

- este contrato não cobre importação ou sincronização Hardcover
- este contrato não expõe `source_event_id` como identificador público

## Critérios de aceite

- endpoints documentados existem em `BooksController`
- responses usam DTOs em `api/books`
- cursor é tratado como contrato opaco
