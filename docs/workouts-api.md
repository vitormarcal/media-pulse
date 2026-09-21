# Workouts API

Entrada manual de treinos, sem provedores externos. Migration: `V63__add_workouts.sql` cria `workouts`, constraints de métricas por categoria e índices do histórico.

## Endpoints

| Método e path | Entrada | Resposta |
| --- | --- | --- |
| `GET /api/workouts` | `category?`, `page=0`, `limit=24` | `{ items, nextPage }` |
| `POST /api/workouts` | multipart: `workout` JSON e `photo?` arquivo | `201` com treino salvo |

Categorias: `RUNNING`, `JUMP_ROPE`, `GYM`. Histórico em `startedAt DESC, id DESC`, páginas a partir de zero, limite entre 1 e 100. `nextPage: null` indica fim. Paginação por offset: novos registros podem deslocar páginas; recarregue a primeira página após salvar.

Parte `workout`, com Content-Type `application/json`:

```json
{
  "category": "RUNNING",
  "startedAt": "2026-09-21T07:30:00-03:00",
  "durationMinutes": 32,
  "distanceKm": 5.125,
  "location": "Parque"
}
```

Para corda, omita `distanceKm` e opcionalmente envie `jumps`. Para academia, omita ambos. `startedAt` exige horário com fuso/offset; o servidor preserva o instante e a UI exibe no fuso do navegador. `durationMinutes` é inteiro positivo. Distância deve ser positiva, menor que 10.000.000 km, com no máximo três casas decimais. `jumps` deve ser inteiro positivo. Local é aparado e vazio vira `null`; máximo 200 caracteres.

A resposta inclui `id`, `category`, `startedAt`, `durationMinutes`, `distanceKm`, `jumps`, `location` e `photoUrl`. Opcionais ausentes aparecem como `null`. A imagem padrão é responsabilidade do frontend; `photoUrl` representa apenas uma foto própria.

## Fotos e operação

- JPEG e PNG, no máximo 10 MB e 40 megapixels. O servidor valida o conteúdo real, não a extensão informada.
- O arquivo original é preservado, inclusive orientação e metadados. Nome local é gerado pelo servidor.
- Arquivos são salvos em `workouts/` sob `media-pulse.storage.covers-path`, servidos em `/covers/workouts/{nome}`. Use o mesmo volume persistente e backup dos covers existentes, além do PostgreSQL.
- O handler de covers normaliza o diretório como caminho absoluto com barra final, inclusive quando a configuração não inclui essa barra.
- Nenhuma variável nova é necessária. Limites globais de multipart existentes também se aplicam (`SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE`, `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE`, defaults de 15 MB).
- Foto inválida ou campos inconsistentes retornam `400`; limite global de multipart excedido retorna `413`.
- Falha de persistência/commit provoca remoção compensatória da foto. Uma interrupção abrupta do processo entre escrita e commit ainda pode deixar um arquivo órfão.
- Não existe upload avulso: foto e dados são enviados na mesma criação. O MVP não inclui edição ou exclusão de treinos.

## Validação

Testes cobrem regras das categorias, datas com offset, contrato multipart, conteúdo de imagem, remoção compensatória e migration/JPA/paginação em PostgreSQL via Testcontainers. No navegador, registrar um treino por categoria, filtrar, recarregar e conferir foto/local, inclusive em tela pequena.

No ambiente de validação com Docker 29, Testcontainers inicialmente pulou o teste por não reconhecer o daemon. A execução com `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` permitiu rodar a integração (sem skips); use essa opção se encontrar o mesmo sintoma.
