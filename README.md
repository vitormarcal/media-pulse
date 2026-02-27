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

## Movies endpoints

- `GET /api/movies/recent`
- `GET /api/movies/{movieId}`
- `GET /api/movies/slug/{slug}`
- `GET /api/movies/search` (busca por título e slug)
- `GET /api/movies/summary`
- `GET /api/movies/year/{year}` (stats anuais, filmes assistidos no ano e filmes nunca assistidos)

## Docs

- `docs/plex-movie-ingestion.md`
- `docs/books-api.md`
- `docs/movies-api.md`
