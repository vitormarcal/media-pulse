# Magazines API

Revistas são um domínio local, separado de livros. A migration `V64__add_magazines.sql` cria publicações, números e jornadas de leitura, com unicidade da identificação de um número e no máximo uma jornada em aberto por número. Não há provedor externo de metadados; imagens podem usar o armazenamento próprio opcional no Visual Library.

## Endpoints

| Método e path | Entrada | Resposta |
| --- | --- | --- |
| `GET /api/magazines` | `q?`, `publicationId?`, `status?`, `unread=false`, `order=PUBLICATION_DATE`, `page=0`, `limit=24` | `{ items, nextPage }` |
| `GET /api/magazines/overview` | — | `{ inProgress, recent, numbersCount, completedReadsCount }` |
| `GET /api/magazines/publications` | — | publicações ordenadas por nome |
| `GET /api/magazines/publications/{id}` | — | `{ publication, latestIssue }`; `404` quando inexistente |
| `PUT /api/magazines/publications/{id}` | JSON `{ name, issn? }` | publicação atualizada |
| `GET /api/magazines/{id}` | — | `{ issue, reads, comments }` |
| `POST /api/magazines` | multipart `issue` JSON, `cover?` arquivo | `201`, detalhe salvo |
| `PUT /api/magazines/{id}` | multipart `issue` JSON, `cover?` arquivo | detalhe atualizado |
| `POST /api/magazines/{id}/cover` | multipart `cover` arquivo | detalhe salvo; retry somente da capa |
| `DELETE /api/magazines/{id}` | — | `204` |
| `POST /api/magazines/{id}/reads` | JSON da jornada | `201`, detalhe atualizado |
| `PUT /api/magazines/{id}/reads/{readId}` | JSON da jornada | detalhe atualizado |
| `DELETE /api/magazines/{id}/reads/{readId}` | — | `204` |

Comentários usam `POST /api/comments/magazines/{issueId}`, `POST /api/comments/{commentId}/edit` e `DELETE /api/comments/{commentId}`. Pertencem ao número e permanecem entre releituras. Excluir uma jornada preserva comentários; excluir o número remove todas as jornadas e comentários dele. Publicações sem números permanecem disponíveis para reutilização.

## Cadastro e correção

Parte `issue` com Content-Type `application/json`:

```json
{
  "publication": { "name": "Ciência Hoje", "issn": "0101-8515" },
  "number": "100",
  "coverDate": "2026-10",
  "totalPages": 80,
  "read": { "status": "WANT_TO_READ" }
}
```

- Use `publicationId` para uma publicação existente ou `publication` para criar/reutilizar uma pelo nome normalizado ou ISSN. Não envie ambos.
- Nome obrigatório, até 200 caracteres; espaços são normalizados. ISSN opcional, com dígito verificador válido, normalizado para `NNNN-NNNX`.
- Exija `number` (texto, até 100 caracteres), `coverDate` (`AAAA-MM`) ou ambos. Numeração normalizada identifica o número quando presente; sem numeração, mês/ano é a identificação. Cadastro repetido retorna `409`; abra o detalhe existente para atualizar ou reler.
- `totalPages` é inteiro positivo opcional. Não pode ser reduzido abaixo de uma página já registrada. Alterar o total recalcula o progresso das jornadas não concluídas informadas por página. Remover o total limpa a página e preserva a porcentagem; jornadas concluídas continuam com 100% e passam a mostrar o novo total como página final.
- `read` é obrigatório na criação. No `PUT` do número, envie todos os metadados; campos opcionais omitidos são removidos. A jornada não é alterada por esse endpoint.
- A capa atual permanece no `PUT`, salvo novo arquivo ou `removeCover: true`. O ISSN e nome da publicação são corrigidos pelo endpoint de publicação, com efeito em todos os seus números.

## Jornadas

Estados: `WANT_TO_READ` (Quero ler), `CURRENTLY_READING` (Lendo), `READ` (Lida), `DID_NOT_FINISH` (Abandonada). Atualizações alteram a mesma jornada; nova leitura ou releitura cria outra, mantendo as anteriores.

```json
{
  "status": "CURRENTLY_READING",
  "startedAt": "2026-10-01",
  "currentPage": 20
}
```

- Datas são dias (`AAAA-MM-DD`), sem conversão de fuso. A UI sugere o dia local atual e permite alterá-lo. Para o mês/ano de publicação, a UI usa um seletor de mês e um campo numérico de ano, gerando `AAAA-MM`; não depende do suporte a `input type="month"`.
- Quero ler não aceita início, término ou progresso. Lendo e Abandonada exigem início; não aceitam término.
- Lida exige término. Sem início prévio ou informado, início recebe o mesmo dia do término. Concluir uma jornada iniciada preserva seu início. Término nunca antecede início; conclusão define progresso de 100%.
- Envie `progressPct` entre 0 e 100 ou `currentPage`, nunca ambos. Página exige total conhecido e deve estar entre zero e o total. Porcentagem é calculada por `currentPage / totalPages × 100`. Sem total, use porcentagem direta.
- Omitir início e progresso no `PUT` preserva os valores existentes; porcentagem explícita limpa a página anterior. Para corrigir uma jornada para Quero ler, envie apenas o estado; suas datas e progresso serão limpos.
- Salvar porcentagem de 100% ou a página final conclui automaticamente a jornada no backend (`READ`). O término recebe o dia atual do servidor quando não informado, e o início existente é preservado. Sem início, ambos recebem a data de término. Editar uma leitura já concluída preserva seu término histórico.
- Há no máximo uma jornada Quero ler ou Lendo por número, protegida por bloqueio transacional e índice parcial único. Uma nova jornada pode ser iniciada quando a anterior foi concluída ou abandonada.
- `reads` retorna as jornadas por ID decrescente. `latestRead` prioriza a jornada em aberto; na ausência dela, usa a última criada.

## Biblioteca e operação

Busca textual por nome, número, mês/ano ou ISSN; filtros por publicação e estado da jornada atual. Ordem padrão `PUBLICATION_DATE`: mês de publicação decrescente (sem data ao final), número numérico decrescente, numeração textual e ID para desempate. Ex.: 71 de fevereiro, 70 de janeiro, 69 de janeiro. Numerações textuais permanecem válidas e usam ordem textual decrescente após as numéricas. `READING_ACTIVITY` usa a atividade mais recente e é usada no dashboard. Ler ou reler não muda a ordem cronológica do acervo.

Página começa em zero, limite entre 1 e 100, `nextPage: null` encerra o arquivo. Paginação por offset: mudanças podem deslocar resultados; recarregue após alterações. Cards deduplicam números, independentemente da quantidade de releituras. O overview retorna até seis números em andamento por atividade de leitura e as seis jornadas concluídas mais recentes, incluindo releituras. `completedReadsCount` conta jornadas concluídas; `numbersCount` conta números distintos.

Com `VISUAL_LIBRARY_ENABLED=true`, novas capas vão para o Visual Library. Referência relativa `/api/image-assets/{uuid}/content`, leitura via backend. Falha de upload remoto preserva dados válidos do número e retorna `coverUploadError` no detalhe (201/200); uma capa anterior permanece. A UI permite tentar apenas a capa novamente em `POST /api/magazines/{id}/cover`, sem repetir cadastro ou leituras. Remoções remotas preservam o asset compartilhado. Validações locais de imagens inválidas continuam retornando 400. Veja [configuração e operação](visual-library-images.md).

No modo local (default): capas JPEG/PNG/WebP, até 10 MB e 40 megapixels, validadas pelo conteúdo real. Armazenadas em `magazines/` sob `media-pulse.storage.covers-path`, servidas em `/covers/magazines/{nome}`. Use o volume e backup de covers existentes; não há nova variável de configuração. Falha de persistência/commit remove a nova capa; substituição ou exclusão remove a antiga após o commit. Interrupção abrupta ou falha na remoção pós-commit pode deixar arquivo órfão. Limites globais de multipart também se aplicam.

Campos inválidos retornam `400`, registros inexistentes `404`, identidade repetida/jornada simultânea `409` e multipart excedido `413`. Erros de domínio incluem `detail` legível. O banco valida datas, estados, progresso e a unicidade das jornadas em aberto.

## Validação

Testes de serviço cobrem datas, progresso, regras e compensação de capa; testes HTTP cobrem multipart, estados/datas inválidos e mensagens; integração PostgreSQL/Flyway/JPA cobre persistência, comentários, filtros, paginação, rollback, correção, exclusão e início concorrente de releituras. Execute com `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` se o Docker 29 exigir compatibilidade do cliente Testcontainers.

### Navegação do acervo

“Adicionar número” cadastra uma edição, reutilizando uma publicação ou criando-a quando necessário. O filtro “Leitura” mantém os estados de jornada e acrescenta “Não lida” (nenhuma jornada concluída). Publicações mostra contagens e capas do último número publicado. `/magazines/publications` é o catálogo; `/magazines/publications/{id}` é o arquivo próprio, agrupado pelo ano de edição, com busca local e filtro de leitura. Limpar filtros mantém a publicação. Números sem data ficam ao final. A paginação é mantida.

`GET /api/magazines` aceita `unread=true`; ordena por publicação e número decrescentes por padrão, em qualquer recorte. `GET /api/magazines/publications` inclui `numbersCount` e `coverUrl` do último número publicado. `overview.recent` retorna pares `{issue, read}` das seis jornadas concluídas mais recentes, inclusive releituras e números com nova leitura ativa. Cards do acervo não mostram a data de atividade; cards de últimas leituras mostram o término da jornada.

Links legados `/magazines?view=publications` e `/magazines?publicationId={id}` são redirecionados pela UI para as páginas próprias, preservando busca e leitura. O detalhe do número continua em `/magazines/{id}`. A grade regular preserva a leitura cronológica em linhas, reutilizando os cards existentes. O destaque do arquivo sempre usa o último número publicado, sem ser alterado pelos filtros.

## Metadados de imagem no Visual Library 0.6.0

Uploads remotos incluem sugestões de tags derivadas dos metadados existentes e referência à entidade com tipo e ID real em string. A referência é enviada também em substituições e novas tentativas, evitando reaplicar tags da mesma associação. Em revistas o tipo é `magazine_issue`; obras usam `artwork` e retratos `artwork_artist`. Omitir sugestões longas ou vazias não remove dados do Media Pulse. Ver [mapeamento, limites e tratamento de recusas](visual-library-images.md). Não há backfill automático nem sincronização contínua de tags.
