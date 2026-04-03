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
- `Metadata.Guid` with `tmdb://` and `tvdb://` -> `external_identifiers`
- `Image[]` + `thumb` do show -> baixados do Plex e armazenados localmente

## Canonical identity

Show identity prefers canonical third-party ids when available.

- Resolution priority during Plex library import is:
  1. `TMDB`
  2. `TVDB`
  3. show fingerprint by `original_title + year`
- Plex GUID is not used as canonical identity. It is stored only as an auxiliary external identifier.
- Plex episode scrobbles bootstrap the show by fingerprint when needed; they do not resolve the show by Plex GUID or by title-only matching.
- Third-party IDs (TMDB/TVDB) are stored as external identifiers when available.
- Episode identity uses `show_id + season_number + episode_number + title`.

## Tables

- `tv_shows`
- `tv_show_titles`
- `tv_show_images`
- `tv_episodes`
- `tv_episode_watches`

Migration: `V11__create_tv_schema.sql`, `V12__add_tv_show_images.sql`.

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
- It persists canonical shows, episodes, localized titles, and TMDB/TVDB/IMDB external ids when available.
- Show reconciliation during import prefers TMDB/TVDB identifiers over fingerprint fallback.
- It does not create rows in `tv_episode_watches`.

## Manual watch ingestion

`POST /api/shows/watches` processes a single manual episode watch.

Resolution order:

1. `tmdbId` -> existing `SHOW` by external identifier.
2. `tvdbId` -> existing `SHOW` by external identifier.
3. Show fingerprint by `showTitle + year`.
4. Episode by fingerprint or `(show_id, season_number, episode_number)`.

When `tmdbId` is present:

- TMDb `/tv/{id}` fills missing show metadata (`description`, `slug`, `year`).
- TMDb poster/backdrop are downloaded and stored under `/covers/tmdb/tv-shows/{showId}/...` when no primary image exists.

Manual sources:

- `tv_show_titles.source` supports `MANUAL`.
- `tv_episode_watches.source` supports `MANUAL`.
