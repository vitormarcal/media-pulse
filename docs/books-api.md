# Books API

A Books API fornece consultas da biblioteca e das jornadas de leitura consolidadas.

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
| `POST /api/books/{bookId}/cover` | multipart `cover` | `{coverUrl}` |
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

- consultas e leituras permanecem read-only; manutenção de capas, séries e universos tem contratos de escrita próprios
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

## Capa manual

`POST /api/books/{bookId}/cover` recebe JPEG/PNG/WebP em multipart `cover`, até 10 MB e 40 megapixels. Retorna 200 com `{coverUrl}` e altera somente a capa do livro canônico. Capas de edições e leituras permanecem independentes. Flag Visual Library define armazenamento remoto ou local; capas existentes não são migradas. Falhas de envio preservam a capa anterior e permitem repetir a mesma operação. Erros: 400 (imagem inválida), 404 (livro ausente), 413 (multipart acima do limite global), 503 (Visual Library indisponível). Consulte [armazenamento e retry de provedores](visual-library-images.md#livros-e-jogos).

## Séries e universos

Escopo: [séries de livros e universos](features/book-series.md). Migration `V72__add_book_series.sql` cria `book_series`, `book_universes`, `book_series_items` e `book_universe_series`, com posições por vínculo, e acrescenta `books.hardcover_id` para sugestões por identidade externa.

Nos contratos abaixo, `{kind}` representa os caminhos concretos `series` ou `universes`. IDs são internos e numéricos. Em séries, um item é um livro; em universos, um item é uma série.

| Método e caminho | Body / retorno |
| --- | --- |
| `GET /api/books/{kind}` | `BookGroupDto[]`: `id`, `name`, `itemCount`, `description`, `authors`, `coverUrls`, `hardcoverId` |
| `POST /api/books/{kind}` | `{name}` → 201 `BookGroupDetailsDto` |
| `GET /api/books/{kind}/{id}` | `BookGroupDetailsDto`: `id`, `name`, `items`, `universes` |
| `PUT /api/books/{kind}/{id}` | `{name, description?}` → detalhe atualizado |
| `DELETE /api/books/{kind}/{id}` | 204; preserva livros e leituras |
| `POST /api/books/{kind}/{id}/items/{childId}` | detalhe atualizado; associação idempotente ao final |
| `DELETE /api/books/{kind}/{id}/items/{childId}` | detalhe atualizado; remoção idempotente |
| `PUT /api/books/{kind}/{id}/order` | `{ids: [...]}` → detalhe atualizado |
| `GET /api/books/{bookId}/series` | séries associadas ao livro |
| `POST /api/books/{bookId}/series/suggestions` | `BookSeriesSuggestionDto[]`: `hardcoverId`, `name`, `books` locais |
| `POST /api/books/{bookId}/series/suggestions/apply` | `{hardcoverId, seriesId?, name, bookIds, metadataFields?}` → detalhe da série |

`items` contém `id`, `name`, `position`, `slug` e `coverUrl`; os dois últimos são nulos para séries dentro de universos. `universes` lista os universos de uma série e é vazio no detalhe de universo. Nomes são obrigatórios, normalizados e limitados a 300 caracteres; nomes iguais não consolidam entidades. O detalhe é acessado por ID, preservando links após renomear.

A ordem inicial é a de cadastro da associação, com novas posições ao final. Reordenar exige exatamente todos os IDs atuais, sem duplicatas; requisições inválidas retornam 400. Escritas de membros e ordem são serializadas por bloqueio da linha do agrupamento em transação. Remover itens pode deixar lacunas nas posições, sem afetar a sequência; reordenar normaliza para 1..N. Um livro pode participar de várias séries e uma série de vários universos, com ordem independente. Exclusões removem somente o agrupamento e seus vínculos.

A UI usa `/books/series`, `/books/series/{id}`, `/books/universes` e `/books/universes/{id}`. O detalhe do livro oferece séries e gerenciamento local. O detalhe da série permite buscar livros do acervo e associar universos; o universo permite adicionar séries. Subir/Descer são ações acessíveis por teclado. Livros novos continuam entrando exclusivamente pelo Hardcover.

### Sugestões Hardcover

Sintoma anterior: a ingestão não preservava o ID do livro no provedor, impossibilitando consultar suas séries sem adivinhar pelo título. Decisão: sincronizações passam a preencher `books.hardcover_id` quando ausente. Livros antigos precisam de uma sincronização antes da primeira busca de sugestões (409 com orientação). O ID salvo não é substituído silenciosamente por outro. A sincronização também preenche o vínculo de eventos já processados e inalterados, usando o mesmo fingerprint canônico da ingestão; não é necessário alterar a leitura no provedor.

A consulta explícita utiliza `book_series → series → book_series`, conforme os schemas oficiais de [Book Series](https://github.com/hardcoverapp/hardcover-docs/blob/main/src/content/docs/api/GraphQL/Schemas/BookSeries.mdx) e o [guia de séries](https://github.com/hardcoverapp/hardcover-docs/blob/main/src/content/docs/api/guides/GettingBooksInSeries.mdx). Retorna somente livros já locais, identificados por `hardcover_id`. Posições do provedor orientam a apresentação da revisão; os vínculos aceitos são acrescentados ao final na sequência selecionada, sem reordenar os existentes. Não há sugestões de universos.

Aplicar consulta novamente a sugestão e valida que os livros selecionados pertencem a ela e existem localmente. `seriesId` reutiliza explicitamente uma série; ausente, cria uma nova com `name`. A UI mantém a série resultante como destino para reaplicações. Buscar não grava; aplicar grava apenas a seleção, e sincronizações não alteram associações ou posições. A origem é registrada em `book_series.hardcover_id` na primeira aplicação. Não há chamadas ao Hardcover na navegação do acervo.

Erros: 400 (nome, ordem ou seleção inválidos), 404 (entidade ausente), 409 (livro sem vínculo Hardcover), 503 (integração desabilitada/sem token), 502 (falha HTTP ou GraphQL, inclusive resposta parcial). Falhas permitem nova tentativa sem perder os controles de curadoria manual.

Validação: `BookSeriesIntegrationTest` aplica todas as migrations em PostgreSQL isolado e cobre multiplicidade, ordem independente, duplicatas, concorrência, seleção local e preservação de leituras. `BookSeriesSuggestionsServiceTest` e `HardcoverSeriesApiClientTest` cobrem filtro local, ausência de vínculo, indisponibilidade e erros GraphQL. Para conferir a UI: sincronize um livro, busque sugestões, aplique uma seleção, reorganize duas séries em dois universos e recarregue; os vínculos e ordens devem permanecer independentes.

No Docker 29, executar os testes com `JAVA_TOOL_OPTIONS=-Dapi.version=1.44`, conforme o padrão de validação dos demais domínios, e conferir zero testes ignorados no relatório.

## Contexto de séries e autores

Evolução descrita em [contexto e apresentação de séries](features/book-series-context.md). A migration `V73__add_book_series_context.sql` acrescenta descrição e metadados opcionais do Hardcover a `book_series`, sem alterar vínculos, leituras ou posições. Séries existentes podem receber contexto pela ação **Editar série → Buscar metadados no Hardcover**, inclusive quando foram criadas antes da V73.

`BookGroupDto` inclui `description`, `authors`, `coverUrls` e `hardcoverId`. Detalhes acrescentam `hardcover`, com `slug`, `primaryAuthor: {id, name}`, `isCompleted`, `booksCount` e `primaryBooksCount`; campos externos podem ser nulos. `isCompleted` descreve a publicação, não o progresso pessoal. Contagens externas são identificadas como Hardcover; `itemCount` e `items` representam exclusivamente o acervo local. Universos também apresentam mosaicos derivados dos livros locais, mantendo os metadados externos vazios.

Autores locais de uma série vêm exclusivamente de `book_authors.role = AUTHOR` nos livros associados, com deduplicação. Tradução, ilustração e outros papéis ficam de fora. O mesmo recorte aparece em `AuthorDetailsResponse.series`; remoções de livros e alterações de créditos se refletem sem persistir uma segunda fonte de autoria. O autor principal externo guarda ID e nome do Hardcover como contexto complementar; não cria um autor local nem faz associação aproximada por nome.

Capas usam a ordem local dos livros, com a capa canônica e fallback para edição. Cards mostram até quatro URLs distintas; universos seguem primeiro a ordem das séries e depois a dos livros. O detalhe usa a primeira capa disponível. Não há importação de uma imagem própria da série.

| Método e caminho | Contrato |
| --- | --- |
| `POST /api/books/series/{id}/metadata/preview` | `{current: BookGroupDetailsDto, suggested: BookSeriesSuggestionDto}`; não grava |
| `POST /api/books/series/{id}/metadata/apply` | `{fields: [...]}` → detalhe atualizado; consulta novamente o provedor e aplica somente a seleção |

Campos aceitos: `NAME`, `DESCRIPTION`, `PRIMARY_AUTHOR`, `PUBLICATION_STATUS`, `COUNTS`, `EXTERNAL_SLUG`. Seleção vazia ou campo desconhecido retorna 400. A ação exige vínculo Hardcover existente (409 quando ausente); erro do provedor retorna 502 e integração indisponível retorna 503. Respostas de erro de negócio incluem `detail` em ProblemDetail para feedback e nova tentativa na UI. Nenhuma dessas ações altera membros ou posições. Selecionar um campo ausente no provedor limpa esse campo explicitamente; valores falso e zero são preservados. `POST /api/books/series` também aceita descrição opcional. `PUT /api/books/series/{id}` aceita `description`: omitida ou nula preserva a descrição existente; string vazia limpa; texto é limitado a 30000 caracteres. Nome continua obrigatório e limitado a 300 caracteres.

Na atribuição de sugestões, `metadataFields` é opcional e vazio por padrão para manter o contrato anterior. Novas séries podem receber os campos selecionados junto dos livros; para séries existentes, a UI começa com metadados desmarcados e distingue valores atuais dos sugeridos. Havendo exatamente uma série local com o mesmo ID Hardcover, ela é sugerida como destino. Livros já associados ficam identificados e não são reenviados como novos vínculos. Reutilizar uma série vinculada a outro ID Hardcover retorna 409, preservando os dados locais.

A interface segue listas de discos e coleções: mosaicos, contagem do acervo e autores na listagem; capa e descrição no cabeçalho, com Continuar leitura para textos longos; manutenção e reordenação durante edição. Atribuição distingue sugestão externa, nova série e destino local. Os mesmos cards são usados na página do autor. Todas as consultas de navegação usam dados locais; enriquecer exige ação explícita e mantém campos e seleção em erro.

Validação adicional: `BookSeriesContextIntegrationTest` migra um arquivo na V72 para a V73 e verifica autoria, edição local, contexto externo, aplicação seletiva e preservação do arquivo. Testes de cliente, serviço e controller cobrem consulta de metadados, valores ausentes/falso/zero, revisão, aplicação e rejeição de campos desconhecidos.

## Tags de séries nas capas

Spec: [tags de séries no Visual Library](features/book-series-visual-library-tags.md). A migration `V74__add_book_series_tag_sync.sql` acrescenta revisão, pendência, mensagem, próxima tentativa e lease/token por livro. Todos os livros existentes entram na fila; livros sem capa remota reconhecida são concluídos sem chamada externa e não mostram pendência na UI.

A fila é invalidada na mesma transação por triggers de persistência em associações, nomes de séries e URLs de capas canônicas/edições. Isso cobre exclusão em cascata, nome aplicado pelo Hardcover, importação e migração de imagens sem duplicar os hooks entre caminhos de escrita. Reordenação, edição de descrição, adição idempotente e gravação do mesmo nome/URL não criam revisões novas. A transformação dos nomes em tags e a comunicação externa permanecem no serviço Kotlin.

Com `VISUAL_LIBRARY_BASE_URL` configurada, um worker dedicado processa até dez livros por lote a cada cinco segundos, depois dos commits locais. Cada livro envia todos os nomes de séries no grupo gerenciado `book-series`, pela referência `book` e por cada referência `book_edition` com capa remota atual. Referências distintas recebem chamadas separadas mesmo que compartilhem o asset. Nomes têm espaços normalizados, são deduplicados sem distinguir caixa e não são truncados. Limites de 48 caracteres UTF-16 por tag e oito tags totais por imagem geram pendência com motivo explícito.

Novos uploads continuam registrando imagem/referência e sugestões genéricas primeiro. A persistência da URL agenda a classificação com a revisão mais recente; conflito de tags não impede salvar a capa. Capas locais/externas não são baixadas por essa rotina. A migração opcional, ao trocar `/covers/...` por referência remota, agenda a mesma fila. O backfill envia somente JSON, sem reenviar bytes. A flag de novos uploads não interrompe a atualização de referências já remotas quando a URL base permanece configurada, como na sincronização de movimentos de obras.

| Método e caminho | Retorno |
| --- | --- |
| `GET /api/books/{bookId}/series-tags/sync` | `{revision, pending, message, hasRemoteCovers}` |
| `POST /api/books/{bookId}/series-tags/sync` | Agenda retry e retorna o mesmo estado, sem esperar pelo Visual Library |

Falhas de rede, limites e referência ausente mantêm `pending=true` e `message` com motivo. Tentativas automáticas de falhas aguardam dez minutos; alterações de séries/capas ou retry explícito ficam elegíveis imediatamente. O retry limpa a mensagem da tentativa anterior enquanto aguarda o novo resultado. Livro ausente retorna 404, e retry de capa remota sem URL base configurada retorna 503 com ProblemDetail. Sem capa remota, o retorno tem `pending=false` e `hasRemoteCovers=false`.

A claim tem lease de cinco minutos e token exclusivo, renovado entre referências enquanto a revisão continua atual. Uma resposta só finaliza a revisão/token que leu; alterações novas não são apagadas por respostas antigas. Expiração do lease permite retomada após interrupção. O Visual Library também exige revisão monotônica e preserva tags pessoais, antigas e de outras referências. Uma tag removida do vínculo pode continuar visível quando ainda pertence a outra referência ou à curadoria pessoal. Referência/asset ausente no Visual Library gera pendência; corrigir o vínculo ou registrar a capa novamente permite nova tentativa.

O detalhe do livro mostra pendência/erro abaixo das séries e oferece “Tentar atualizar tags das capas”. O estado é consultado novamente após mudanças de série ou upload; enquanto pendente, há polling a cada três segundos, ou trinta segundos quando existe mensagem, encerrado ao sair da página. Não altera os controles de atribuição nem bloqueia o histórico. A sincronização cobre as capas atualmente vinculadas; assets antigos retidos após substituir uma capa não recebem alterações por este fluxo.

Validação: `BookSeriesTagSyncIntegrationTest` migra um arquivo na V73 e cobre backfill, referências compartilhadas de livro/edições, invalidação, rollback, leases/revisões concorrentes, falha parcial e retry; testes de serviço, cliente e controller cobrem normalização, limites UTF-16, contrato JSON e erros. Executar integrações com `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` quando necessário para Docker 29.
