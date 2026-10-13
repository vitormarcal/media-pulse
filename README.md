# Media Pulse

Media Pulse centraliza o arquivo pessoal de consumo, descobertas culturais e atividades físicas:

- música
- livros
- revistas
- artes visuais
- filmes
- séries
- games
- treinos (corrida, corda e academia)

O backend agrega dados de provedores externos, persiste uma visão canônica local e expõe APIs HTTP principalmente read-only para exploração, resumos e páginas de detalhe. Revistas têm registro manual, progresso, releituras e comentários em `/magazines`. Artes visuais têm obras, artistas, experiências, galeria local e listas pessoais em `/artworks`. Treinos têm registro manual com foto opcional e histórico visual em `/workouts`.

## Estrutura do repositório

- `server/`: backend Kotlin + Spring Boot
- `frontend/`: frontend Nuxt 4
- `docs/`: contratos HTTP e notas operacionais
- `http-client-env/`: exemplos de ambiente para clientes HTTP locais

## Backend

- Stack: Kotlin 1.9 + Spring Boot 3.5
- Java: 21
- Banco principal: PostgreSQL
- Migrations: Flyway em `server/src/main/resources/db/migration`
- Start local: `./server/gradlew bootRun`

O backend não builda mais o frontend durante o ciclo do Gradle. O empacotamento conjunto agora acontece no `Dockerfile` raiz, que monta uma imagem única com:

- backend Spring Boot
- frontend estático gerado pelo Nuxt
- entrega no mesmo domínio, com APIs em `/api/*` e UI em `/`

## Configuração

Para iniciar o backend, configure o acesso ao PostgreSQL:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

Integrações, pipeline, storage, CORS e limites HTTP possuem defaults e opções documentados em `server/src/main/resources/application.yml`. Sobrescreva somente o necessário por variáveis de ambiente e nunca versione credenciais reais.

Imagens novas de revistas, artes visuais, livros e jogos podem usar o Visual Library: `VISUAL_LIBRARY_ENABLED=true` e `VISUAL_LIBRARY_BASE_URL=http://visual-library:3000`. A integração usa o contrato 0.6.0 e envia sugestões de tags e referência da entidade. A flag vem desligada; referências locais existentes permanecem. Consulte [configuração, falhas e backup](docs/visual-library-images.md).

## Documentação

- APIs de domínio: `docs/books-api.md`, `docs/music-api.md`, `docs/movies-api.md`, `docs/shows-api.md`, `docs/games-api.md`, `docs/people-api.md`, `docs/magazines-api.md`, `docs/artworks-api.md` e `docs/workouts-api.md`
- Operações e integrações: `docs/operations-api.md`, `docs/plex-movie-ingestion.md` e `docs/plex-show-ingestion.md`
- Contrato HTTP publicado: `docs/openapi.yaml`
- Descoberta de novas features: `docs/feature-discovery.md`
- Decisões de enriquecimento MusicBrainz: `docs/features/musicbrainz-album-enrichment.md`

## Frontend

O frontend em `frontend/` pode ser servido separadamente para desenvolvimento ou via imagem combinada.

Para desenvolvimento local do Nuxt, use `NUXT_PUBLIC_API_BASE` se o backend estiver em outra origem. Em produção no mesmo domínio, o valor esperado é relativo, normalmente `/api`.

Se o frontend rodar em outra origem, ajuste `media-pulse.allowed-origin` para incluir essa origem no CORS.

## Docker

O repositório possui três Dockerfiles com responsabilidades distintas:

- `frontend/Dockerfile`: build e entrega standalone do frontend estático via `nginx`
- `server/Dockerfile`: build e entrega standalone do backend
- `Dockerfile`: imagem combinada para produção, servindo frontend e backend no mesmo domínio

Fluxo padrão de publicação:

```bash
make build VERSION=<versão> EXTRA_TAGS="latest"
make push VERSION=<versão> EXTRA_TAGS="latest"
```

Por padrão, o `Makefile` usa o `Dockerfile` raiz.

Artes visuais aceita múltiplos movimentos e estilos com sugestões e entrada livre. Correções atualizam as imagens remotas automaticamente e permitem retry em falhas; operação e migration V67 estão em [artworks-api.md](docs/artworks-api.md).
