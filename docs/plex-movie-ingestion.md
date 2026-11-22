# Plex movie scrobble ingestion

O fluxo automático de enriquecimento compartilhado por filmes e séries está descrito em [`features/automatic-media-enrichment.md`](features/automatic-media-enrichment.md). Este documento trata do mapeamento e da persistência específicos do Plex para filmes.

## Scope

This feature ingests only Plex `media.scrobble` events for `Metadata.type=movie`.

- `media.play` and `media.stop` are ignored for movie completion.
- Domain persistence is decoupled from `event_sources` (no FK, no `source_event_id`).

It also supports full movie library import during pipeline startup.

## Mapping

Plex payload -> Media Pulse domain:

- `Metadata.originalTitle` (fallback: `Metadata.title`) -> `movies.original_title`
- `Metadata.slug` -> `movies.slug`
- `Metadata.year` -> `movies.year`
- `Metadata.summary` -> `movies.description`
- `Metadata.title` -> `movie_titles.title` (localized/alternate title)
- `Metadata.lastViewedAt` -> `movie_watches.watched_at`
- `Metadata.Guid` with `tmdb://` and `imdb://` -> `movies.tmdb_id` and `movies.imdb_id`
- `Metadata.Image[]` + `Metadata.thumb` -> downloaded from Plex and stored locally

## Canonical identity

Library import first resolves known TMDb/IMDb IDs. When they identify a single canonical movie, it reuses that movie even if Plex supplies a different title/year fingerprint; alternate titles are stored without changing its canonical title or fingerprint. Without a known ID, it falls back to `original_title + year`. If IDs resolve to different movies, it logs a warning and falls back to the fingerprint; existing IDs are never reassigned. Scrobble ingestion currently uses the fingerprint first.

An `Ignoring Plex movie identifier linked to another movie` warning means the selected record and the external ID owner differ. Imports can complete despite this warning. Previously the library import resolved only by fingerprint, so title/year differences could create a duplicate before discovering an existing ID. The ID-first lookup prevents that case on subsequent imports, but does not merge or delete historical duplicates. To validate, import the same external IDs with an alternate title and confirm the canonical movie is reused without creating another row. Existing duplicates require review of metadata, watches and personal curation before repair.

- Plex `ratingKey` and `plex://...` GUIDs are never persisted or used for reconciliation.
- `ratingKey` is used only in memory while navigating the current Plex API import.
- Third-party IDs are stored in `movies.tmdb_id` and `movies.imdb_id` when available.

## Tables

- `movies`
- `movie_titles`
- `movie_watches`
- `movie_images`

## Movie images

- The service downloads all relevant movie images available in Plex payload (`Image[]`).
- Image type is not persisted in DB.
- One image is chosen as primary with deterministic rule:
  1. First image marked as `coverPoster`.
  2. Fallback to first valid image.
  3. Fallback to `thumb` if list is empty.
- Primary image path is stored in `movies.cover_url`.

## Startup full import

Movie library import runs in the existing startup pipeline (`ApplicationReadyEvent`) when enabled.

- Property: `media-pulse.plex.import.movies-enabled` (env: `PLEX_IMPORT_MOVIES_ENABLED`, default `true`)
- It imports from Plex `movie` sections using paginated reads.
- It persists canonical movies, localized titles, and TMDB/IMDB external ids.
- It does not create rows in `movie_watches` (watch history comes only from scrobble events).

Operational logs report section discovery, page fetch/load and progress every 10 movies and at the end of each page, including elapsed milliseconds. Processing is sequential and includes image download/storage before counting each completed movie. Hardcover polling can run concurrently, so `Hardcover poll done` does not mean the Plex import has finished. Use the Plex import start/progress/finish messages to follow that work; a single slow movie or request can still leave a gap between progress messages.

## Automatic enrichment

Movies imported from the Plex library or received through scrobble webhooks are enriched asynchronously from TMDb.

- Movies with a TMDb id enter the pending collection, terms, credits, and companies synchronization immediately.
- Movies with only an IMDb id are first resolved through TMDb's external-id lookup.
- Import and scrobble persistence do not depend on TMDb availability.
- Incomplete steps remain pending and are retried periodically.
- IMDb ids without a TMDb match are shown as blocked and retried after 24 hours.
- The retry interval is configured by `TMDB_ENRICHMENT_INTERVAL_MS` (default: `120000`).

## Movies API

Read-only movie endpoints:

- `GET /api/movies/recent?limit=20`
- `GET /api/movies/{movieId}`
- `GET /api/movies/search?q=...&limit=10`
- `GET /api/movies/summary?range=month|year|custom&start=...&end=...`

Imagens de filmes do Plex agora passam pelo armazenamento compartilhado com Visual Library, quando habilitado, com sugestões `filme` e título original e referência `movie`/ID real. Novos arquivos preservam a capa atual; somente uma escolha explícita de curadoria a substitui. A indisponibilidade de upload mantém o restante da ingestão utilizável. Consulte [armazenamento e migração](visual-library-images.md#filmes-armazenamento-migração-e-tags-de-coleção).

## Reparo pontual dos registros 406/486

Na inspeção de 22/11/2026, `406` (`Tim Burton's the Nightmare Before Christmas`, 1993) possui TMDb `9479`, IMDb `tt0107688`, uma visualização e créditos. `486` (`The Nightmare Before Christmas`, 1993) possui apenas títulos e imagens, sem marcas pessoais. O [script específico](sql/merge-movie-486-into-406.sql) mantém `406`, copia títulos e imagens exclusivos e remove `486`. Preserva a capa canônica e não exclui arquivos/assets. Não é migration nem uma ferramenta genérica de mesclagem.

Depois de publicar a correção da importação, faça backup e execute a simulação a partir da raiz do repositório:

```bash
umask 077
ssh kamisama@mordor 'sudo -n -u postgres pg_dump -Fc mediapulse' > ~/mediapulse-before-merge.dump
ssh kamisama@mordor 'sudo -n -u postgres pg_restore --list' < ~/mediapulse-before-merge.dump > /dev/null
ssh kamisama@mordor 'sudo -n -u postgres psql -X -d mediapulse' < docs/sql/merge-movie-486-into-406.sql
```

Verifique o sucesso do dump e seu tamanho antes de continuar; o arquivo contém dados pessoais e deve ficar fora do Git. A simulação termina com `ROLLBACK`. Para aplicar:

```bash
ssh kamisama@mordor 'sudo -n -u postgres psql -X -v apply=true -d mediapulse' < docs/sql/merge-movie-486-into-406.sql
```

O script usa uma transação, bloqueia alterações concorrentes nas tabelas envolvidas e interrompe se as identidades, a curadoria ou os vínculos do duplicado mudaram. Depois, abra `/movies/406`, confira histórico/capa/galeria e rode novamente a importação para confirmar que `486` não é recriado. O webhook ainda usa fingerprint primeiro: essa limitação não é corrigida por esta mudança da importação de biblioteca e deve ser considerada ao validar eventos futuros.
