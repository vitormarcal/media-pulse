# Rediscovered Albums

## Owner-Facing Problem

The music experience currently highlights recent listening activity and general library browsing, but it does not surface a very personal pattern: albums that were once important, disappeared for a long period, and later returned to active listening.

Today the owner must rediscover these patterns manually through memory or album pages.

Media Pulse should make these moments visible directly from the owned listening archive.

---

# Intended Experience

Add a small editorial section to the music experience showing albums that:

* had meaningful listening history
* became inactive for a long period
* recently returned to active listening

The section should feel reflective and personal rather than analytical or recommendation-engine driven.

Each item should quickly communicate:

* album
* artist
* cover art
* recent activity
* historical activity
* how long the album stayed inactive

The experience should feel like rediscovering forgotten parts of the owner's history.

---

# Affected Domains

Music only.

Uses existing canonical music data:

* albums
* artists
* track playbacks

The pilot should remain isolated from other domains.

---

# Data Stored Locally

No new persistent data is required for the pilot.

Rediscovery should be computed dynamically from existing local playback history already stored in the canonical archive.

Derived response fields may include:

* historical play count
* recent play count
* last historical play
* first recent play
* latest play
* quiet gap duration

These values remain read-only computed results.

---

# External Providers

No new provider integration or provider reads at request time.

The feature operates entirely on already-imported local playback history.

Underlying playback data may originate from:

* Spotify imports
* Plex music activity

Providers remain ingestion sources only, not runtime dependencies.

---

# Temporal Semantics

Rediscovery is computed relative to the current time when the request executes.

The pilot intentionally uses rolling relative windows instead of fixed calendar periods.

The first version should prioritize understandable behavior and use a small, explainable score only for ordering.

---

# Backend/API Changes

Add a small read-only endpoint:

`GET /api/music/albums/rediscovered?limit=8`

The endpoint should:

* return only canonical local archive data
* expose enough information to explain why an album appears
* remain read-only
* avoid provider-specific concepts

The first version should expose only:

* `limit`

Thresholds and heuristics should remain internal implementation details stored near the query/service layer as clearly named constants.

No settings UI or runtime configuration is needed for the pilot.

When implemented, document the endpoint in `docs/music-api.md`.

---

# Frontend/UI Changes

Add one editorial section to the music experience.

The implementation should:

* reuse existing music card/strip patterns
* remain cover-led and visually dense
* preserve current music page hierarchy
* follow `DESIGN.md`

The section should feel integrated into the existing browsing experience rather than visually isolated.

If no rediscovered albums exist, the section should quietly disappear or render a minimal empty state.

---

# Non-Goals

The pilot must not introduce:

* ML or AI recommendation systems
* collaborative filtering
* embeddings/vector search
* recommendation infrastructure
* new service boundaries
* new dependencies
* provider reads during requests
* cached recommendation tables
* threshold configuration UI
* cross-domain rediscovery
* mutation or feedback workflows

The pilot is intentionally small and heuristic-based.

---

# Initial Heuristic

Suggested first-pass thresholds:

* `recentWindowDays = 30`
* `minHistoricalPlays = 5`
* `minRecentPlays = 2`
* `minGapDays = 90`
* `limit = 8`

Definitions:

* recent plays:

  * `played_at >= recentStart`
* historical plays:

  * `played_at < recentStart`
* quiet gap:

  * duration between the last historical play and the first recent play

A rediscovered album is an album with:

* meaningful historical listening activity
* renewed recent listening activity
* a sufficiently long inactive gap between those periods

The first implementation should stay intentionally simple and easy to reason about.

Ordering should favor the strongest rediscovery moments rather than only the most recently played albums. The pilot score is internal and explainable:

* `quietGapDays * ln(historicalPlayCount + 1) * ln(recentPlayCount + 1)`

This favors albums with a long inactive gap, meaningful historical weight, and a real recent return while keeping very large play counts from dominating linearly. Ties are resolved by quiet gap, historical plays, recent plays, latest play, and album id.

---

# Implementation Notes

The pilot is expected to use a single aggregate read-only query over canonical playback history.

PostgreSQL aggregate filtering and date arithmetic are preferred because they make the rediscovery rules explicit and understandable.

The implementation should prefer existing indexed playback data and avoid schema changes unless a real performance or correctness issue is discovered.

---

# Acceptance Criteria

* The feature is implemented as a small read-only music experience.
* Results are computed entirely from locally stored playback history.
* Albums only appear when they satisfy historical activity, recent activity, and quiet-gap requirements.
* The endpoint returns enough information to explain rediscovery behavior.
* Existing music APIs remain compatible.
* Empty results are handled gracefully.
* No migration is introduced unless implementation proves one is necessary.
* The frontend section feels editorial and personal rather than analytical or dashboard-oriented.
* The first implementation remains intentionally small in scope.

---

# Testable Acceptance Criteria

* Albums below the historical threshold are excluded.
* Albums below the recent activity threshold are excluded.
* Albums below the quiet-gap threshold are excluded.
* Historical and recent windows do not overlap.
* Results are consistently ordered by rediscovery strength, not only latest play.
* Empty datasets return an empty response safely.
* `limit` handling follows existing music endpoint conventions.

---

# Minimal Deliverable

The smallest acceptable first slice is:

1. backend query
2. response DTO
3. read-only endpoint
4. API documentation
5. repository-level coverage tests

Frontend integration may be implemented afterward as a second slice.

---

# Expected Files

Likely files involved:

* music response DTO
* music query repository
* music controller
* repository tests
* `docs/music-api.md`

Frontend files may be added in a second slice.

---

# Migration

No migration is expected for the pilot.

Existing playback history already contains the required temporal and album linkage information for the initial heuristic.
