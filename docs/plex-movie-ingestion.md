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

Movie identity uses fingerprint by `original_title + year`.

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

## Automatic enrichment

Movies imported from the Plex library or received through scrobble webhooks are enriched asynchronously from TMDb.

- Movies with a TMDb id enter the pending terms, credits, and companies synchronization immediately.
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
