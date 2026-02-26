# Plex movie scrobble ingestion

## Scope

This feature ingests only Plex `media.scrobble` events for `Metadata.type=movie`.

- `media.play` and `media.stop` are ignored for movie completion.
- Domain persistence is decoupled from `event_sources` (no FK, no `source_event_id`).

It also supports full movie library import during pipeline startup.

## Mapping

Plex payload -> Media Pulse domain:

- `Metadata.originalTitle` (fallback: `Metadata.title`) -> `movies.original_title`
- `Metadata.year` -> `movies.year`
- `Metadata.summary` -> `movies.description`
- `Metadata.title` -> `movie_titles.title` (localized/alternate title)
- `Metadata.lastViewedAt` -> `movie_watches.watched_at`
- `Metadata.Guid` with `tmdb://` and `imdb://` -> `external_identifiers` (`entity_type=MOVIE`)

## Canonical identity

Movie identity uses fingerprint by `original_title + year`.

- Plex GUID is not used as canonical identity.
- Third-party IDs (TMDB/IMDB) are stored as external identifiers when available.

## Tables

- `movies`
- `movie_titles`
- `movie_watches`

Migration: `V7__create_movies_schema.sql`.

## Startup full import

Movie library import runs in the existing startup pipeline (`ApplicationReadyEvent`) when enabled.

- Property: `media-pulse.plex.import.movies-enabled` (env: `PLEX_IMPORT_MOVIES_ENABLED`, default `true`)
- It imports from Plex `movie` sections using paginated reads.
- It persists canonical movies, localized titles, and TMDB/IMDB external ids.
- It does not create rows in `movie_watches` (watch history comes only from scrobble events).
