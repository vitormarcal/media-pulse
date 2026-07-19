# Plex show scrobble ingestion

## Scope

This feature ingests Plex `media.scrobble` events for `Metadata.type=episode`.

- `media.play` and `media.stop` are ignored for episode completion.
- Domain persistence is decoupled from `event_sources`.

It also supports full show library import during pipeline startup and manual watch creation through the API.

## Mapping

Plex payload -> Media Pulse domain:

- `Metadata.originalTitle` / `Metadata.grandparentTitle` -> `tv_shows.original_title`
- `Metadata.grandparentSlug` -> `tv_shows.slug`
- `Metadata.parentYear` / `Metadata.year` -> `tv_shows.year`
- `Metadata.summary` -> `tv_episodes.summary`
- `Metadata.title` -> `tv_episodes.title`
- `Metadata.parentIndex` -> `tv_episodes.season_number`
- `Metadata.index` -> `tv_episodes.episode_number`
- `Metadata.lastViewedAt` -> `tv_episode_watches.watched_at`
- GUIDs de série `tmdb://`, `tvdb://` e `imdb://` -> `tv_shows.tmdb_id`, `tv_shows.tvdb_id` e `tv_shows.imdb_id`
- GUIDs de episódio `tmdb://`, `tvdb://` e `imdb://` -> `external_identifiers` enquanto a etapa de episódios não for migrada
- `Image[]` + `thumb` do show -> baixados do Plex e armazenados localmente

## Canonical identity

Show identity prefers canonical third-party ids when available.

- Resolution priority during Plex library import is:
  1. `TMDB`
  2. `TVDB`
  3. show fingerprint by `original_title + year`
- Plex `ratingKey` and `plex://...` GUIDs are never persisted or used for reconciliation.
- `ratingKey` is used only in memory while navigating the current Plex API import.
- Plex episode scrobbles bootstrap the show by fingerprint when needed; they do not resolve the show by Plex GUID, slug, or title-only matching.
- Show third-party IDs (TMDB/TVDB/IMDB) are stored directly in `tv_shows` when available.
- Episode third-party IDs remain in `external_identifiers` until the episode migration is complete.
- Episode resolution prefers third-party IDs and falls back to the fingerprint based on show, season, episode number, and title.

## Tables

- `tv_shows`
- `tv_show_titles`
- `tv_show_images`
- `tv_episodes`
- `tv_episode_watches`

Migrations: `V11__create_tv_schema.sql`, `V12__add_tv_show_images.sql`, `V13__add_tv_episode_season_title.sql`, `V32__remove_plex_external_identifiers.sql`, `V36__migrate_show_external_identifiers.sql`.

## Show images

- The service downloads all relevant show images available in Plex payload (`Image[]`).
- One image is chosen as primary with deterministic rule:
  1. First image marked as `coverPoster`.
  2. Fallback to first valid image.
  3. Fallback to `thumb` if list is empty.
- Primary image path is stored in `tv_shows.cover_url`.

## Startup full import

Show library import runs in the startup pipeline when enabled.

- It imports from Plex `show` sections using paginated reads.
- It persists canonical shows, episodes, localized titles, show IDs in `tv_shows`, and episode IDs in `external_identifiers` when available.
- Show reconciliation during import prefers TMDB/TVDB identifiers over fingerprint fallback.
- It does not create rows in `tv_episode_watches`.

## Manual watch ingestion

`POST /api/shows/watches` processes a single manual episode watch.

Resolution order:

1. `tmdbId` -> existing show by `tv_shows.tmdb_id`.
2. `tvdbId` -> existing show by `tv_shows.tvdb_id`.
3. Show fingerprint by `showTitle + year`.
4. Episode by fingerprint or `(show_id, season_number, episode_number)`.

When `tmdbId` is present:

- TMDb `/tv/{id}` fills missing show metadata (`description`, `slug`, `year`).
- TMDb poster/backdrop are downloaded and stored under `/covers/tmdb/tv-shows/{showId}/...` when no primary image exists.

Manual sources:

- `tv_show_titles.source` supports `MANUAL`.
- `tv_episode_watches.source` supports `MANUAL`.
