# Media Pulse

Media Pulse centraliza dados pessoais de consumo (music, books e movies) com ingestão de provedores externos e APIs read-only para consulta.

## Server

- Stack: Kotlin + Spring Boot
- Migrations: Flyway em `server/src/main/resources/db/migration`
- Run: `./server/gradlew bootRun`

## Movies migrations

- `V7__create_movies_schema.sql`
- `V8__add_movie_images.sql`
- `V9__add_slug_to_movies.sql` (adiciona `movies.slug` para persistir o slug do Plex)
- `V10__allow_manual_movie_sources.sql` (habilita `MANUAL` em `movie_titles`/`movie_watches` e índice para lookup em `external_identifiers`)

## Movies endpoints

- `GET /api/movies/recent`
- `GET /api/movies/{movieId}`
- `GET /api/movies/slug/{slug}`
- `GET /api/movies/search` (busca por título e slug)
- `GET /api/movies/summary`
- `GET /api/movies/stats`
- `GET /api/movies/year/{year}` (stats anuais, filmes assistidos no ano e filmes nunca assistidos)
- `POST /api/movies/watches` (ingestão manual idempotente de watches com resolução por TMDB/IMDB/fingerprint)

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
- `docs/books-api.md`
- `docs/movies-api.md`
