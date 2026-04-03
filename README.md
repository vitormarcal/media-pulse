# Media Pulse

Media Pulse centraliza dados pessoais de consumo (music, books, movies e TV shows) com ingestão de provedores externos e APIs read-only para consulta.

## Server

- Stack: Kotlin + Spring Boot
- Migrations: Flyway em `server/src/main/resources/db/migration`
- Run: `./server/gradlew bootRun`

## Movies migrations

- `V7__create_movies_schema.sql`
- `V8__add_movie_images.sql`
- `V9__add_slug_to_movies.sql` (adiciona `movies.slug` para persistir o slug do Plex)
- `V10__allow_manual_movie_sources.sql` (habilita `MANUAL` em `movie_titles`/`movie_watches` e índice para lookup em `external_identifiers`)

## TV migrations

- `V11__create_tv_schema.sql` (cria `tv_shows`, `tv_show_titles`, `tv_episodes`, `tv_episode_watches` e índices iniciais)
- `V12__add_tv_show_images.sql` (adiciona `cover_url` e `tv_show_images`)

## Movies endpoints

- `GET /api/movies/recent`
- `GET /api/movies/{movieId}`
- `GET /api/movies/slug/{slug}`
- `GET /api/movies/search` (busca por título e slug)
- `GET /api/movies/summary`
- `GET /api/movies/stats`
- `GET /api/movies/year/{year}` (stats anuais, filmes assistidos no ano e filmes nunca assistidos)
- `POST /api/movies/watches` (ingestão manual idempotente de watches com resolução por TMDB/IMDB/fingerprint)

## Shows endpoints

- `GET /api/shows/recent`
- `GET /api/shows/{showId}`
- `GET /api/shows/slug/{slug}`
- `GET /api/shows/search` (busca por título e slug)
- `GET /api/shows/summary`
- `GET /api/shows/stats`
- `GET /api/shows/year/{year}` (stats anuais, séries assistidas no ano e séries nunca assistidas)
- `POST /api/shows/watches` (ingestão manual idempotente de episode watches com resolução por TMDB/TVDB/fingerprint)

## Configuração TMDb

- `TMDB_ENABLED` (default `true`)
- `TMDB_API_BASE_URL` (default `https://api.themoviedb.org/3`)
- `TMDB_IMAGE_BASE_URL` (default `https://image.tmdb.org`)
- `TMDB_TOKEN` (Bearer token; quando ausente usa `TMDB_API_KEY`)
- `TMDB_API_KEY` (fallback para autenticação em query param)
- `TMDB_RATE_LIMIT_PER_SECOND` (default `10`, conservador para evitar burst)
- `TMDB_MAX_429_RETRIES` (default `2`)
- `TMDB_RETRY_BACKOFF_MS` (default `1000`)

## Docs

- `docs/plex-movie-ingestion.md`
- `docs/plex-show-ingestion.md`
- `docs/books-api.md`
- `docs/movies-api.md`
- `docs/shows-api.md`
