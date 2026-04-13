# Media Pulse

Media Pulse centraliza dados pessoais de consumo de mídia em quatro domínios:

- música
- livros
- filmes
- séries

O backend agrega dados de provedores externos, persiste uma visão canônica local e expõe APIs HTTP principalmente read-only para exploração, resumos e páginas de detalhe.

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

## Migrations atuais

- `V1__create_event_sources_table.sql`
- `V2__create_music_schema.sql`
- `V3__create_books_schema.sql`
- `V4__add_slug_to_books.sql`
- `V5__rebuild_book_reads_as_sessions.sql`
- `V6__add_edition_information_to_book_editions.sql`
- `V7__create_movies_schema.sql`
- `V8__add_movie_images.sql`
- `V9__add_slug_to_movies.sql`
- `V10__allow_manual_movie_sources.sql`
- `V11__create_tv_schema.sql`
- `V12__add_tv_show_images.sql`
- `V13__add_tv_episode_season_title.sql`

## Configuração

`application.yml` depende principalmente destas variáveis de ambiente:

### Infra

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO` (`validate` por padrão)
- `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` (`15MB`)
- `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` (`15MB`)

### Pipeline e storage

- `PIPELINE_IMPORT_ENABLED`
- `PIPELINE_IMPORT_CRON`
- `PIPELINE_RUN_ON_STARTUP`
- `MEDIA_PULSE_FRONTEND_STATIC_PATH`
- `media-pulse.allowed-origin` via config YAML para CORS
- `media-pulse.storage.covers-path`
- `media-pulse.storage.imports-path`

### Plex

- `PLEX_URL`
- `PLEX_TOKEN`
- `PLEX_IMPORT_ENABLED`
- `PLEX_IMPORT_MOVIES_ENABLED`
- `PLEX_IMPORT_SHOWS_ENABLED`
- `PLEX_IMPORT_PAGE_SIZE`

### Spotify

- `SPOTIFY_ENABLED`
- `SPOTIFY_API_BASE_URL`
- `SPOTIFY_ACCOUNTS_BASE_URL`
- `SPOTIFY_CLIENT_ID`
- `SPOTIFY_CLIENT_SECRET`
- `SPOTIFY_REFRESH_TOKEN`
- `SPOTIFY_OAUTH_ENABLED`
- `SPOTIFY_REDIRECT_URI`
- `SPOTIFY_SCOPES`
- `SPOTIFY_IMPORT_ENABLED`
- `SPOTIFY_IMPORT_PAGE_SIZE`
- `SPOTIFY_POLL_ENABLED`
- `SPOTIFY_POLL_CRON`

### Hardcover

- `HARDCOVER_ENABLED`
- `HARDCOVER_API_BASE_URL`
- `HARDCOVER_TOKEN`
- `HARDCOVER_USER_ID`
- `HARDCOVER_POLL_ENABLED`
- `HARDCOVER_POLL_CRON`
- `HARDCOVER_POLL_PAGE_SIZE`

### TMDb

- `TMDB_ENABLED`
- `TMDB_API_BASE_URL`
- `TMDB_IMAGE_BASE_URL`
- `TMDB_TOKEN`
- `TMDB_API_KEY`
- `TMDB_RATE_LIMIT_PER_SECOND`
- `TMDB_MAX_429_RETRIES`
- `TMDB_RETRY_BACKOFF_MS`

### MusicBrainz e HTTP clients

- `MB_IMPORT_ENABLED`
- `MB_ENRICH_BATCH_SIZE`
- `MB_ENRICH_MAX_TAGS`
- `MB_ENRICH_MIN_REQUEST_INTERVAL_MS`
- parâmetros `media-pulse.http.*` controlam pools e timeouts para clientes remotos, locais e de imagens

## Endpoints por domínio

### Books

- `GET /api/books/library`
- `GET /api/books/year/{year}`
- `GET /api/books/{bookId}`
- `GET /api/books/slug/{slug}`
- `GET /api/books/authors/{authorId}`
- `GET /api/books/list`
- `GET /api/books/search`
- `GET /api/books/summary`

### Music

- `GET /api/music/summary`
- `GET /api/music/recent-albums`
- `GET /api/music/library/artists`
- `GET /api/music/library/albums`
- `GET /api/music/library/tracks`
- `GET /api/music/search`
- `GET /api/music/albums/{albumId}`
- `GET /api/music/artists/{artistId}`
- `GET /api/music/tracks/{trackId}`
- `GET /api/music/tops/artists`
- `GET /api/music/tops/albums`
- `GET /api/music/tops/tracks`
- `GET /api/music/tops/genres`
- `GET /api/music/coverage/artists`
- `GET /api/music/coverage/albums`
- `GET /api/music/albums/never-played`
- `GET /api/music/genres/trending`
- `GET /api/music/genres/recent`
- `GET /api/music/genres/underplayed`
- `GET /api/music/genres/top-by-source`

### Movies

- `GET /api/movies/library`
- `GET /api/movies/recent`
- `GET /api/movies/{movieId}`
- `GET /api/movies/slug/{slug}`
- `GET /api/movies/search`
- `GET /api/movies/summary`
- `GET /api/movies/stats`
- `GET /api/movies/year/{year}`
- `POST /api/movies/watches`

### Shows

- `GET /api/shows/library`
- `GET /api/shows/recent`
- `GET /api/shows/currently-watching`
- `GET /api/shows/{showId}`
- `GET /api/shows/slug/{slug}`
- `GET /api/shows/search`
- `GET /api/shows/summary`
- `GET /api/shows/stats`
- `GET /api/shows/year/{year}`
- `POST /api/shows/watches`

## Endpoints operacionais

- `POST /webhook/plex`
- `POST /event-sources/reprocess`
- `POST /event-sources/{id}/reprocess`
- `POST /api/spotify/import`
- `POST /api/spotify/extended/import`
- `POST /api/spotify/backfill-album-tracks`
- `GET /oauth/spotify/login`
- `GET /oauth/spotify/callback`
- `POST /api/plex/music/import`
- `POST /api/musicbrainz/enrich-album-genres`
- `POST /api/musicbrainz/enrich-album-genres/drain`

## Frontend

O frontend em `frontend/` pode ser servido separadamente para desenvolvimento ou via imagem combinada.

Para desenvolvimento local do Nuxt, use `NUXT_PUBLIC_API_BASE` se o backend estiver em outra origem. Em produção no mesmo domínio, o valor esperado é relativo, normalmente `/api`.

Se o frontend rodar em outra origem, ajuste `media-pulse.allowed-origin` para incluir essa origem no CORS.

## Docker

O repositório agora tem três Dockerfiles com responsabilidades distintas:

- `frontend/Dockerfile`: build e entrega standalone do frontend estático via `nginx`
- `server/Dockerfile`: build e entrega standalone do backend
- `Dockerfile`: imagem combinada para produção, servindo frontend e backend no mesmo domínio

Fluxo padrão de publicação:

```bash
make build VERSION=1.0.0-beta.35 EXTRA_TAGS="latest"
make push VERSION=1.0.0-beta.35 EXTRA_TAGS="latest"
```

Por padrão, o `Makefile` usa o `Dockerfile` raiz.

## Documentação detalhada

- `docs/books-api.md`
- `docs/music-api.md`
- `docs/movies-api.md`
- `docs/shows-api.md`
- `docs/plex-movie-ingestion.md`
- `docs/plex-show-ingestion.md`
- `docs/operations-api.md`
- `docs/openapi.yaml`
