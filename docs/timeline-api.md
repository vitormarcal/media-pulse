# Timeline API

Consulta pública do histórico local, como as demais consultas do arquivo. Sem novos dados persistidos, migrations ou configuração. Escopo e fontes em [Linha do tempo pessoal](features/personal-timeline.md).

A página `/timeline` consulta ambos os endpoints com `kind=ACTIVITY` fixo. Inclusões locais não aparecem na UI; o suporte a `ADDITION` permanece na API por compatibilidade.

## Endpoints

### `GET /api/timeline`

Parâmetros opcionais:

| Parâmetro | Regra |
| --- | --- |
| `domain` | `MUSIC`, `BOOKS`, `MAGAZINES`, `MOVIES`, `SHOWS`, `GAMES`, `ARTWORKS`, `WORKOUTS` |
| `kind` | `ACTIVITY` ou `ADDITION` |
| `month` | `YYYY-MM`: inicia no fim desse mês e continua para o passado, não restringe a resposta a um único mês |
| `limit` | Inteiro entre 1 e 100, default 40 |
| `cursor` | Opaco, retornado na página anterior; reenviar os mesmos filtros e mês |

Resposta: `{items, nextCursor, timeZone}`. `nextCursor: null` indica fim. Cada item tem `id` estável por marco, `domain`, `kind`, `date` (data civil ISO), `occurredAt` (instante ISO ou null), `precision` (`DAY` ou `INSTANT`), `action`, `title`, `subtitle`, `href`, `imageUrl` e `relatedItems`. Links relacionados de música contêm `title`, `href`, `imageUrl?` e `subtitle?`; não há limite de álbuns/artistas no resumo, mas a UI prioriza três álbuns com créditos e permite expandir os demais álbuns e links separados de artistas.

`timeZone` é `America/Sao_Paulo`. A UI deve usar `date` diretamente para agrupar, sem converter datas civis pelo fuso do navegador. Mostrar horário apenas quando `occurredAt` não for null. O cursor usa o instante de ordenação interno e o identificador do marco com collation C; marcos de precisão diária podem ter instante interno, que não é exposto como horário real.

Ordenação decrescente por instante e ID. Música usa o início do dia como âncora de ordenação, com precisão diária; livros usam suas âncoras importadas, sem afirmar precisão horária. Registros novos mais recentes não deslocam páginas anteriores. Não é um snapshot: exclusões/correções e novas reproduções no mesmo dia podem atualizar eventos durante a navegação. Recarregue para refletir alterações. Cursores não têm assinatura e não são autorização; são apenas posições de uma consulta pública.

Filtros/enums inválidos, mês inválido, cursor malformado/incompatível ou limite fora do intervalo retornam `400`. Um mês sem dados continua buscando registros anteriores. `ADDITION` em revistas/treinos retorna vazio, pois não existe marco de inclusão separado confiável.

### `GET /api/timeline/periods`

Aceita os mesmos filtros `domain` e `kind`, sem mês ou cursor. Retorna `{months: [{month: "2026-05", count: 12}], timeZone}` em ordem decrescente, somente meses com registros. Contagem de eventos da cronologia (música agrupada), não de obras distintas nem de reproduções. Deve ser carregado independentemente da página e atualizado ao trocar filtros. Falha da régua não impede explorar os cards.
