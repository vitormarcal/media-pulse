# People API

A People API sustenta a exploração audiovisual transversal entre filmes e séries. Pessoas são entidades locais únicas ligadas a elenco, direção ou roteiro.

## Escopo e origem dos dados

- `people` guarda a identidade canônica local e o vínculo estável com o TMDb
- `movie_credits` e `show_credits` ligam a mesma pessoa aos dois catálogos
- `people.profile_url` referencia a imagem remota do TMDb; retratos de pessoas não são armazenados em disco
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
| `POST /api/people/{personId}/filmography/movies/{movieId}/link` | body com `category`          | vincula a pessoa a um filme local existente                         |
| `POST /api/people/{personId}/filmography/shows/{showId}/link`   | body com `category`          | vincula a pessoa a uma série local existente                        |
| `POST /api/admin/people/{personId}/tmdb-filmography`      | `personId`                        | força atualização da filmografia de filmes                          |
| `POST /api/admin/people/{personId}/tmdb-show-filmography` | `personId`                        | força atualização da filmografia de séries                          |
| `POST /api/admin/people/filmography/compact`              | `limit=100`                       | compacta snapshots antigos e retorna as quantidades removidas       |

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
- adicionar ou vincular uma obra mantém o owner na página da pessoa, sem abrir automaticamente o filme ou a série; a atualização após adicionar preserva a filmografia aberta e o recorte selecionado
- cada obra local informa as categorias já vinculadas e as categorias de elenco, direção ou roteiro ainda disponíveis no snapshot
- quando uma categoria está ausente, a página permite vinculá-la sem sair da filmografia; uma opção é aplicada diretamente e várias opções usam um seletor compacto
- o vínculo usa somente o snapshot local, marca a obra como curada manualmente e não consulta o TMDb durante a leitura ou a ação
- `compacted=true` indica que o recorte contém somente obras locais com vínculos ainda incompletos; obras já completamente vinculadas continuam disponíveis nos créditos canônicos da página
- atualizar manualmente substitui o recorte pelo snapshot completo do TMDb, limpa o estado compactado e inicia um novo prazo de retenção

### Retenção e compactação

- filmografias de pessoas não favoritas tornam-se elegíveis sete dias após a última sincronização bem-sucedida
- filmes e séries são avaliados independentemente
- a compactação exclui membros externos e membros locais cujas categorias de elenco, direção e roteiro já estejam completamente vinculadas
- membros locais permanecem no snapshot compacto somente quando permitem reparar ao menos uma categoria de crédito ausente
- ao corrigir a última categoria pendente, o membro é removido imediatamente do snapshot compacto e continua visível nos créditos canônicos da página
- filmes, séries, créditos e pessoas nunca são removidos pela compactação
- pessoas favoritas são sempre excluídas da seleção
- acesso e leitura da página não renovam o prazo nem consultam o TMDb
- o scheduler executa diariamente às 04:30 por padrão e processa até 100 pessoas
- `TMDB_FILMOGRAPHY_COMPACTION_CRON` altera o cron da rotina
- a ação administrativa aceita `limit` entre 1 e 1.000 e usa as mesmas regras do scheduler
- os registros excluídos deixam espaço reutilizável pelo PostgreSQL e reduzem o crescimento futuro, mas `pg_total_relation_size` não necessariamente diminui imediatamente
- redução física do arquivo exige manutenção específica do PostgreSQL, como `VACUUM FULL`, fora da rotina da aplicação e com planejamento para o bloqueio da tabela

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
- música, produção e demais funções técnicas não fazem parte do recorte local de pessoas
- créditos fora do recorte são descartados; pessoas sem créditos são removidas quando não estão marcadas como favoritas
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

### Evolução da retenção

- acompanhar registros ativos, espaço reutilizável e crescimento de `person_filmography_members` após a primeira compactação
- revisar o prazo de sete dias somente com base no uso e no crescimento medidos

## Non-goals

- diretório irrestrito de todas as pessoas persistidas
- listas genéricas ou múltiplas coleções de pessoas
- busca externa ou criação de pessoas pela página de favoritos
- atualização de provedor durante leitura
- ordenação manual de favoritos
- ficha técnica irrestrita com produção, música ou outras equipes

## Critérios de aceite

- favoritos persistem e são retornados pela marcação mais recente
- busca e perfil expõem o mesmo estado de favorito
- rankings consideram filmes e séries como obras distintas, sem inflar séries por episódios ou reassistidas
- cada ranking contabiliza somente sua função, exclui favoritos e preserva a ordenação ao carregar mais
- uma pessoa pode aparecer em rankings diferentes, mas apenas uma vez dentro de cada seção
- acesso direto e reload de `/people` são encaminhados para a SPA
- filmes e séries refletem os watches locais nos estados documentados
- endpoints de leitura funcionam apenas com dados locais
- compactação preserva membros locais com vínculos incompletos e nunca seleciona favoritos
- atualização manual restaura separadamente o snapshot completo de filmes ou séries
