# People API

A People API sustenta a exploração audiovisual transversal entre filmes e séries. Pessoas são entidades locais únicas, independentemente de aparecerem como elenco, direção, roteiro ou outra função.

## Escopo e origem dos dados

- `people` guarda a identidade canônica local e o vínculo estável com o TMDb
- `movie_credits` e `show_credits` ligam a mesma pessoa aos dois catálogos
- perfil e filmografias do TMDb são snapshots locais; endpoints de leitura não consultam provedores externos
- watches de filmes e episódios determinam a relação da filmografia com o histórico do owner

## Endpoints

| Path                                                      | Params                            | Retorna                                                             |
| --------------------------------------------------------- | --------------------------------- | ------------------------------------------------------------------- |
| `GET /api/people/{slug}`                                  | `slug`                            | `PersonDetailsResponse` com perfil e vínculos audiovisuais locais   |
| `GET /api/people/search`                                  | `q`, `limit=8`                    | pessoas locais ligadas a ao menos um filme ou série                 |
| `GET /api/people/favorites`                               | -                                 | favoritos, pela marcação mais recente                               |
| `GET /api/people/history/most-present`                    | `category`, `limit=4`, `offset=0` | ranking paginado de elenco, direção ou roteiro nas obras assistidas |
| `POST /api/people/{personId}/favorite`                    | `personId`                        | marca uma pessoa como favorita                                      |
| `DELETE /api/people/{personId}/favorite`                  | `personId`                        | remove a marcação de favorito                                       |
| `GET /api/people/{personId}/filmography`                  | `personId`                        | snapshot local da filmografia de filmes                             |
| `GET /api/people/{personId}/show-filmography`             | `personId`                        | snapshot local da filmografia de séries                             |
| `POST /api/admin/people/{personId}/tmdb-filmography`      | `personId`                        | força atualização da filmografia de filmes                          |
| `POST /api/admin/people/{personId}/tmdb-show-filmography` | `personId`                        | força atualização da filmografia de séries                          |

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

## Mais presentes no seu histórico

- três seções de descoberta em `/people`, depois dos favoritos e antes dos resultados de busca: Elenco, Direção e Roteiro
- cada seção começa com 4 pessoas e permite carregar mais 4 independentemente
- uma pessoa pode aparecer em mais de uma seção quando possuir funções diferentes
- cada ranking considera somente a quantidade de obras distintas efetivamente assistidas na função correspondente
- filme com watch conta uma obra; série com ao menos um episódio assistido conta uma obra
- múltiplos créditos, episódios ou watches da mesma obra não aumentam a contagem
- são elegíveis apenas pessoas ligadas a pelo menos duas obras assistidas na função da seção
- `CAST` considera créditos de elenco; `DIRECTING`, o job `Director`; `WRITING`, os jobs `Writer`, `Screenplay`, `Story` e `Story Editor`
- música, produção e demais funções técnicas permanecem preservadas no arquivo, mas não disputam esses rankings
- popularidade do TMDb e quantidade de créditos sem watch não influenciam o ranking
- favoritos são excluídos para evitar repetição
- empates usam a obra assistida mais recentemente, nome e ID, nessa ordem
- cards em grid alinhado priorizam retrato, nome e uma única contagem curta, preservando transições previsíveis ao carregar ou favoritar
- favoritar pelo ranking move a pessoa para Favoritos sem recarregar a página

## Roadmap da feature

### Atualização prioritária de favoritos

- pessoas favoritas poderão receber prioridade nos workers periódicos de perfil e filmografia
- a leitura continuará usando snapshots locais e nunca disparará atualização externa
- frequência, retentativa e observabilidade devem ser definidas antes da implementação

### Créditos ampliados nas páginas das obras

- páginas de filmes e séries poderão expor música, produção e outras equipes além de direção, roteiro e elenco
- a apresentação deve preservar a baixa densidade textual e evitar transformar a página em uma ficha técnica irrestrita
- os créditos continuam disponíveis na página da pessoa mesmo antes dessa ampliação

## Non-goals

- diretório irrestrito de todas as pessoas persistidas
- listas genéricas ou múltiplas coleções de pessoas
- busca externa ou criação de pessoas pela página de favoritos
- atualização de provedor durante leitura
- ordenação manual de favoritos

## Critérios de aceite

- favoritos persistem e são retornados pela marcação mais recente
- busca e perfil expõem o mesmo estado de favorito
- rankings consideram filmes e séries como obras distintas, sem inflar séries por episódios ou reassistidas
- cada ranking contabiliza somente sua função, exclui favoritos e preserva a ordenação ao carregar mais
- uma pessoa pode aparecer em rankings diferentes, mas apenas uma vez dentro de cada seção
- acesso direto e reload de `/people` são encaminhados para a SPA
- filmes e séries refletem os watches locais nos estados documentados
- endpoints de leitura funcionam apenas com dados locais
