# People API

A People API sustenta a exploração audiovisual transversal entre filmes e séries. Pessoas são entidades locais únicas, independentemente de aparecerem como elenco, direção, roteiro ou outra função.

## Escopo e origem dos dados

- `people` guarda a identidade canônica local e o vínculo estável com o TMDb
- `movie_credits` e `show_credits` ligam a mesma pessoa aos dois catálogos
- perfil e filmografias do TMDb são snapshots locais; endpoints de leitura não consultam provedores externos
- watches de filmes e episódios determinam a relação da filmografia com o histórico do owner

## Endpoints

| Path | Params | Retorna |
| --- | --- | --- |
| `GET /api/people/{slug}` | `slug` | `PersonDetailsResponse` com perfil e vínculos audiovisuais locais |
| `GET /api/people/search` | `q`, `limit=8` | pessoas locais ligadas a ao menos um filme ou série |
| `GET /api/people/favorites` | - | favoritos, pela marcação mais recente |
| `POST /api/people/{personId}/favorite` | `personId` | marca uma pessoa como favorita |
| `DELETE /api/people/{personId}/favorite` | `personId` | remove a marcação de favorito |
| `GET /api/people/{personId}/filmography` | `personId` | snapshot local da filmografia de filmes |
| `GET /api/people/{personId}/show-filmography` | `personId` | snapshot local da filmografia de séries |
| `POST /api/admin/people/{personId}/tmdb-filmography` | `personId` | força atualização da filmografia de filmes |
| `POST /api/admin/people/{personId}/tmdb-show-filmography` | `personId` | força atualização da filmografia de séries |

## Página e favoritos

`/people` é uma entrada própria da navegação e prioriza curadoria pessoal.

- mostra favoritos como conteúdo principal e não lista indiscriminadamente todas as pessoas
- a busca considera somente pessoas locais com ao menos um crédito em filme ou série
- cards priorizam retrato e nome; contagens do histórico aparecem em uma única linha curta
- `people.favorited_at` preserva estado e ordem de marcação
- marcar e desmarcar são operações idempotentes
- desfavoritar não remove pessoa, créditos, perfil ou filmografias

`/people/{slug}` mantém o contexto completo da pessoa e permite alterar o favorito pelo perfil.

## Filmografia e histórico

As filmografias de filmes e séries possuem snapshots e estados de sincronização independentes.

- abrir ou explorar a página nunca consulta o TMDb nem grava no catálogo
- presença local é resolvida dinamicamente pelo `tmdb_id`
- leitura da filmografia não cria filmes, séries ou créditos
- títulos externos continuam disponíveis para adição explícita pelo owner

Filmes usam:

- `UNWATCHED`: está no catálogo e não possui watch
- `WATCHED`: possui ao menos um watch
- `OUTSIDE_CATALOG`: está no snapshot da filmografia, mas não no catálogo

Séries usam os episódios locais conhecidos:

- `NOT_STARTED`: nenhum episódio assistido
- `IN_PROGRESS`: possui watches e episódios locais pendentes
- `WATCHED`: todos os episódios locais conhecidos possuem watch
- `OUTSIDE_CATALOG`: está no snapshot da filmografia, mas não no catálogo

## Roadmap da feature

### Mais presentes no seu histórico

- seção curta de descoberta em `/people`, separada dos favoritos
- ranking baseado somente na quantidade de obras distintas efetivamente assistidas
- popularidade do TMDb e quantidade de créditos sem watch não influenciam o ranking
- favoritos são excluídos para evitar repetição
- quantidade de cards permanece pequena; a heurística deve ser validada com dados reais antes de ser consolidada

### Atualização prioritária de favoritos

- pessoas favoritas poderão receber prioridade nos workers periódicos de perfil e filmografia
- a leitura continuará usando snapshots locais e nunca disparará atualização externa
- frequência, retentativa e observabilidade devem ser definidas antes da implementação

## Non-goals

- diretório irrestrito de todas as pessoas persistidas
- listas genéricas ou múltiplas coleções de pessoas
- busca externa ou criação de pessoas pela página de favoritos
- atualização de provedor durante leitura
- ordenação manual de favoritos

## Critérios de aceite

- favoritos persistem e são retornados pela marcação mais recente
- busca e perfil expõem o mesmo estado de favorito
- acesso direto e reload de `/people` são encaminhados para a SPA
- filmes e séries refletem os watches locais nos estados documentados
- endpoints de leitura funcionam apenas com dados locais
