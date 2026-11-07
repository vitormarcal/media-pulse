# Games API

Games usam entrada manual: a UI chama sugestões do IGDB e, ao criar, o backend tenta enriquecer metadados pelo IGDB e imagens pelo SteamGridDB.

## Configuração

- `IGDB_CLIENT_ID` e `IGDB_CLIENT_SECRET`: credenciais Twitch/IGDB para obter token OAuth client credentials.
- `STEAMGRIDDB_API_KEY`: chave da API SteamGridDB para buscar grids.
- `IGDB_ENABLED` e `STEAMGRIDDB_ENABLED`: permitem desligar cada integração sem remover a UI.

Sem credenciais, a entrada mínima continua funcionando, mas sugestões/metadados/imagens externas ficam vazios.

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/games/library` | `limit=24`, `cursor?` | `GamesLibraryResponse` |
| `GET /api/games/stats` | - | `GamesStatsResponse` |
| `GET /api/games/search` | `q`, `limit=40` | `GamesSearchResponse` |
| `POST /api/games/{gameId}/cover` | multipart `cover` | `{coverUrl}` |
| `GET /api/games/{gameId}` | `gameId` | `GameDetailsResponse` |
| `GET /api/games/slug/{slug}` | `slug` | `GameDetailsResponse` |
| `POST /api/games/catalog/suggestions` | `q` | `GameCatalogSuggestionsResponse` |
| `POST /api/games/catalog` | body com `title`, `year?`, `igdbId?` | `ManualGameCatalogCreateResponse` |
| `POST /api/games/{gameId}/sessions` | body com sessão | `GameSessionCreateResponse` |
| `PATCH /api/games/{gameId}/sessions/{sessionId}` | body com sessão | `GameSessionDto` |
| `DELETE /api/games/{gameId}/sessions/{sessionId}` | `gameId`, `sessionId` | vazio |

## Paginação e limites

- `library` retorna `nextCursor`; trate o cursor como opaco
- `limit` de `library` e `search` é normalizado para `1..100`
- detalhes retornam no máximo 100 sessões, ordenadas por `startedAt DESC, sessionId DESC`

## Criação manual

`POST /api/games/catalog`

```json
{
  "title": "Hollow Knight",
  "year": 2017,
  "igdbId": "11156"
}
```

`igdbId` e `year` são opcionais. Quando `igdbId` é informado, o backend tenta preencher ano, descrição e IDs externos a partir do IGDB.

## Sessões

`POST /api/games/{gameId}/sessions`

```json
{
  "status": "PLAYING",
  "startedAt": "2026-05-15T20:00:00Z",
  "endedAt": null,
  "notes": "Comecei uma nova campanha."
}
```

Status aceitos:

- `PLAYING`
- `BACKLOG`
- `COMPLETED`
- `ABANDONED`

Cada sessão tem início obrigatório e fim opcional. O histórico aceita múltiplas sessões para o mesmo game.

- se `endedAt` vier antes de `startedAt`, a API retorna `400`
- se `gameId` ou `sessionId` não existir ou não pertencer ao game, a API retorna `404`

Para finalizar ou abandonar um ciclo em andamento, atualize a mesma sessão com `PATCH /api/games/{gameId}/sessions/{sessionId}` em vez de criar outra sessão:

```json
{
  "status": "COMPLETED",
  "startedAt": "2026-05-16T20:00:00Z",
  "endedAt": "2026-05-21T23:00:00Z",
  "notes": "Campanha finalizada."
}
```

## Invariantes

- games são entradas de catálogo local com sessões manuais
- cada game admite no máximo um `igdb_id` e um `steamgriddb_id`, armazenados diretamente em `games`
- cada ID externo é único dentro de seu provider e não pode identificar dois games locais
- `igdb_id` ancora deduplicação e enriquecimento de metadados; `steamgriddb_id` referencia a fonte de imagens associada ao mesmo game local
- `GameDetailsResponse.rating` e `GameDetailsResponse.comments` podem incluir dados cross-domain de Ratings e Comments
- provedores externos enriquecem metadados e imagens; o catálogo local continua sendo o contrato canônico

## Non-goals

- não há endpoint de importação automática de sessões de jogo neste contrato
- não há deduplicação de sessões por período documentada no código

## Critérios de aceite

- endpoints documentados existem em `GamesController` ou `GameCatalogController`
- status aceitos batem com `GameSessionStatusDto`
- cursor é tratado como contrato opaco

## Imagens e capa manual

Imagens novas do SteamGridDB e capa de fallback do IGDB são baixadas, validadas e armazenadas localmente ou no Visual Library conforme a flag existente. Imagens já salvas não são migradas. Falha de imagem não impede cadastro; repetir a criação deduplicada permite tentar novamente quando não houver capa/principal.

`POST /api/games/{gameId}/cover` recebe JPEG/PNG/WebP em multipart `cover`, até 10 MB e 40 megapixels. Retorna 200 com `{coverUrl}`, torna a imagem principal e preserva imagens anteriores, sessões e metadados. Falha de envio mantém a capa e a principal anteriores; repetir tenta somente a capa. Erros: 400 (imagem inválida), 404 (jogo ausente), 413 (limite multipart global), 503 (Visual Library indisponível). [Operação, referências e retenção](visual-library-images.md#livros-e-jogos).

## Buscar imagens no detalhe

`POST /api/games/{gameId}/images/import` busca até três grids usando o SteamGridDB ID salvo, ou procura pelo título se ainda não houver vínculo. Sem grid salvo, tenta a capa do IGDB vinculado. Usa o armazenamento e limites existentes e acrescenta imagens sem alterar metadados ou sessões. Preserva a capa atual; atribui uma principal somente quando não houver capa nem principal. Retorna `{savedImagesCount, coverAssigned, failedImagesCount}`; imagens já vinculadas não contam como novas. Entidade ausente retorna 404; falha de todas as imagens tentadas retorna 503 e permite retry na página. Sucesso parcial mantém imagens salvas e informa falhas. Provedores sem configuração ou sem resultados podem retornar contagens zero.

## Escolher imagens no detalhe

A interface usa agora seleção explícita, conforme [escopo aprovado](features/game-image-selection.md). O import automático acima permanece compatível e o cadastro mantém seu comportamento anterior.

- `GET /api/games/{gameId}/images/candidates`: retorna `{images, warnings}`. Cada opção contém `id`, `provider`, `kind`, `url`, `previewUrl`, `width` e `height`. Não baixa nem armazena arquivos. SteamGridDB fornece um lote de grids (incluindo horizontais) e heroes; IGDB fornece capa, screenshots e artworks do ID local. A ausência de credenciais/resultados produz lista vazia; falhas isoladas preservam opções dos outros tipos/provedores e geram avisos.
- `POST /api/games/{gameId}/images/selection`: exige owner e CSRF; recebe `{ids: [...]}`, entre uma e 50 seleções. Retorna `{savedIds, failedIds}`, inclusive quando todas falham. IDs desconhecidos ou opções que desapareceram do provedor entram em `failedIds`. Formato inválido retorna 400 e jogo ausente retorna 404. O backend revalida os IDs nos resultados atuais, sem aceitar URLs do navegador.

Salvar usa o armazenamento, validações e limites existentes e não atribui nem troca a capa/principal, mesmo quando não há capa. A UI mantém falhas selecionadas para retry, desmarca os sucessos e atualiza a galeria completa. As prévias são carregadas diretamente do provedor, sem token; os arquivos selecionados são baixados pelo backend e guardados no Visual Library quando habilitado.

O Media Pulse guarda somente a referência retornada pelo armazenamento na galeria existente (`game_images`). Não persiste URLs de origem nem histórico de seleção. Selecionar novamente envia a imagem ao armazenamento; deduplicação e revisão de duplicadas pertencem ao Visual Library. Se o armazenamento retornar uma referência já vinculada ao jogo, a inserção existente preserva um único vínculo. Sem migration, nova variável ou dependência.

As imagens salvas aparecem em galeria masonry, com proporção original e ampliação em tela cheia na própria página. O visualizador tem miniaturas, contador, setas e Escape; ao fechar, devolve o foco ao controle de origem. A galeria fica disponível sem login e as ações de adição continuam exclusivas do owner.

Na seleção, clicar na prévia abre a URL original transitoriamente no visualizador, com setas, miniaturas e checkbox para marcar/desmarcar. Abrir, navegar e fechar não guardam imagens nem alteram as escolhas; o envio ocorre somente em “Salvar selecionadas”.

## Existência de imagens na seleção

`POST /api/games/{gameId}/images/lookup` exige owner e CSRF. Recebe `{ids: [...]}` com 1–8 IDs de candidatos atuais e retorna `{available, results: [{id, state}]}`. Estados: `IN_GALLERY`, `IN_LIBRARY`, `NOT_FOUND` e `UNAVAILABLE`. IDs inválidos/lote excedido retornam 400; jogo ausente retorna 404. A URL de download é resolvida novamente nos provedores pelo backend, nunca aceita do navegador.

Com Visual Library habilitado, o backend baixa os originais solicitados (até 10 MiB cada), calcula SHA-256 dos bytes completos e consulta `POST /api/assets/lookup` com o Bearer existente. Compara IDs encontrados com as referências remotas atuais da capa/galeria. Nenhum arquivo, hash, URL de origem ou resultado é persistido. O contrato exige Visual Library 0.27.0; serviço desabilitado, versão anterior, falhas e respostas inválidas são tratados como indisponíveis, preservando seleção e upload.

A UI observa as opções que entram na tela e envia um lote de até quatro por vez, sem baixar todas antecipadamente. Abrir/navegar no visualizador também prioriza a opção atual. “Verificando…”, “Já na galeria”, “Já no Visual Library”, “Não encontrada no Visual Library” e “Não verificado” são avisos informativos: nenhuma opção é oculta ou bloqueada. Respostas de buscas anteriores são ignoradas. A ausência indica somente falta dos bytes exatos no índice, sem comparação por similaridade nem garantia sobre legados sem hash. Falha isolada de download não marca a imagem como ausente.

Salvar mantém o upload existente para registrar a referência e sugestões; o lookup não cria vínculos. Após sucesso, o aviso pode refletir a referência já na galeria durante a sessão atual. A consulta pode exigir novo download do original no envio; não há cache persistente nem migration. Veja [escopo aprovado](features/game-image-existence.md).
