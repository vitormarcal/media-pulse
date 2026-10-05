# Magazines API

Revistas são um domínio local, separado de livros. A migration `V64__add_magazines.sql` cria publicações, números e jornadas de leitura, com unicidade da identificação de um número e no máximo uma jornada em aberto por número. Nenhum provedor externo é consultado.

## Endpoints

| Método e path | Entrada | Resposta |
| --- | --- | --- |
| `GET /api/magazines` | `q?`, `publicationId?`, `status?`, `page=0`, `limit=24` | `{ items, nextPage }` |
| `GET /api/magazines/overview` | — | `{ inProgress, recent, numbersCount, completedReadsCount }` |
| `GET /api/magazines/publications` | — | publicações ordenadas por nome |
| `PUT /api/magazines/publications/{id}` | JSON `{ name, issn? }` | publicação atualizada |
| `GET /api/magazines/{id}` | — | `{ issue, reads, comments }` |
| `POST /api/magazines` | multipart `issue` JSON, `cover?` arquivo | `201`, detalhe salvo |
| `PUT /api/magazines/{id}` | multipart `issue` JSON, `cover?` arquivo | detalhe atualizado |
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

- Datas são dias (`AAAA-MM-DD`), sem conversão de fuso. A UI sugere o dia local atual e permite alterá-lo.
- Quero ler não aceita início, término ou progresso. Lendo e Abandonada exigem início; não aceitam término.
- Lida exige término. Sem início prévio ou informado, início recebe o mesmo dia do término. Concluir uma jornada iniciada preserva seu início. Término nunca antecede início; conclusão define progresso de 100%.
- Envie `progressPct` entre 0 e 100 ou `currentPage`, nunca ambos. Página exige total conhecido e deve estar entre zero e o total. Porcentagem é calculada por `currentPage / totalPages × 100`. Sem total, use porcentagem direta.
- Omitir início e progresso no `PUT` preserva os valores existentes; porcentagem explícita limpa a página anterior. Para corrigir uma jornada para Quero ler, envie apenas o estado; suas datas e progresso serão limpos.
- Progresso de 100% não conclui automaticamente a jornada. A conclusão é uma ação explícita com data.
- Há no máximo uma jornada Quero ler ou Lendo por número, protegida por bloqueio transacional e índice parcial único. Uma nova jornada pode ser iniciada quando a anterior foi concluída ou abandonada.
- `reads` retorna as jornadas por ID decrescente. `latestRead` prioriza a jornada em aberto; na ausência dela, usa a última criada.

## Biblioteca e operação

Busca textual por nome, número, mês/ano ou ISSN; filtros por publicação e estado da jornada atual. Ordem `activityDate DESC, id DESC`, calculada pelo dia mais recente de término/início entre as jornadas (para Quero ler, usa o dia UTC de cadastro). Página começa em zero, limite entre 1 e 100, `nextPage: null` encerra o histórico. Paginação por offset: mudanças podem deslocar resultados; recarregue após alterações. Cards deduplicam números, independentemente da quantidade de releituras.

O overview retorna até seis números em andamento e seis com última jornada concluída. `completedReadsCount` conta jornadas concluídas, incluindo releituras; `numbersCount` conta números distintos.

Capas JPEG/PNG, até 10 MB e 40 megapixels, validadas pelo conteúdo real. Armazenadas em `magazines/` sob `media-pulse.storage.covers-path`, servidas em `/covers/magazines/{nome}`. Use o volume e backup de covers existentes; não há nova variável de configuração. Falha de persistência/commit remove a nova capa; substituição ou exclusão remove a antiga após o commit. Interrupção abrupta ou falha na remoção pós-commit pode deixar arquivo órfão. Limites globais de multipart também se aplicam.

Campos inválidos retornam `400`, registros inexistentes `404`, identidade repetida/jornada simultânea `409` e multipart excedido `413`. Erros de domínio incluem `detail` legível. O banco valida datas, estados, progresso e a unicidade das jornadas em aberto.

## Validação

Testes de serviço cobrem datas, progresso, regras e compensação de capa; testes HTTP cobrem multipart, estados/datas inválidos e mensagens; integração PostgreSQL/Flyway/JPA cobre persistência, comentários, filtros, paginação, rollback, correção, exclusão e início concorrente de releituras. Execute com `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` se o Docker 29 exigir compatibilidade do cliente Testcontainers.
