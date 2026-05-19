# Product Constitution

This document defines the product intent for Media Pulse. It should be read together with `GOVERNANCE.md`.

`PRODUCT_CONSTITUTION.md` explains what the product is, who it serves, and which product trade-offs matter.
`GOVERNANCE.md` explains how agents and contributors should change the code safely.

# Mission

Media Pulse is a personal canonical archive of cultural consumption, combining history, curation, and discovery while preserving ownership of the data independently from Big Tech platforms.

The product exists for one primary user: its owner. Decisions should optimize for the owner's best experience, not for generic enterprise workflows, public growth, social engagement, or SaaS scalability.

# Product Principles

## 1. Personal ownership first

Media Pulse must preserve a local canonical view of media data.

If useful data can be read from an external source and the product depends on it for the experience, prefer storing it locally instead of repeatedly depending on the provider at read time.

External providers enrich the archive. They must not become the archive.

## 2. Personal product, not SaaS

Media Pulse is not intended to become a social network, a multi-tenant platform, or a commercial SaaS product in the near or medium term.

Architecture decisions should respect good engineering practices, but they should not be justified by mass-scale assumptions alone. Balance is key: maintainability, clarity, and user experience matter more than generic platform architecture.

## 3. The best owner experience wins

The target audience is the owner of this installation.

When product decisions are ambiguous, optimize for the owner's day-to-day use:

- fast access to personal history
- low-friction capture of media activity
- rich visual browsing
- useful discovery from owned data
- simple correction of incorrect metadata
- clear paths from one piece of media to related people, works, lists, and moments

## 4. Automation reduces friction

Manual actions should be minimized when reliable automation is possible.

The product should import, enrich, classify, and connect data automatically where reasonable. Manual workflows are still valuable when they give the owner control, correct provider mistakes, or support deliberate curation.

## 5. Rich media matters

Media Pulse should feel visual, personal, and alive. Images are part of the product experience, not decoration.

The product should support rich image browsing, image replacement, and image review where it improves ownership or curation. Provider images are useful starting points, but the owner should be able to choose what represents their archive.

## 6. Simplicity should shine

The code exists to serve the product. It should not become a barrier to improving the product.

Prefer simple, maintainable solutions when they satisfy the need. Avoid over-engineering, unnecessary abstractions, and architectural ceremony that does not materially improve the owner experience or future maintainability.

## 7. Data should be usable

The database should remain understandable and operationally useful. Data modeling should support real product workflows, not only implementation convenience.

When adding new data, prefer structures that make the archive easier to query, inspect, migrate, and repair.

# Product Boundaries

Media Pulse is:

- a personal media archive
- a canonical local record of cultural consumption
- a visual exploration and curation tool
- a bridge between external providers and owned data
- a product optimized for one owner's experience

Media Pulse is not:

- a social network
- a public sharing platform
- a SaaS product
- a multi-tenant enterprise system
- a generic analytics dashboard
- a product whose architecture is driven primarily by massive scale

# Tech Stack

## Current stack

- Backend: Kotlin, Spring Boot, Java 21
- Persistence: PostgreSQL, Flyway migrations, Spring Data JPA
- HTTP integrations: Spring WebFlux/WebClient where appropriate
- Frontend: Nuxt 4, Vue 3, TypeScript
- Frontend quality: ESLint, Prettier
- Packaging/runtime: Docker, root `Dockerfile`, development Compose files
- External providers currently represented in the codebase: Plex, Spotify, Hardcover, TMDb, IGDB, SteamGridDB, MusicBrainz

## Architecture commitments

- The backend owns the canonical product model.
- The database stores the durable archive.
- Flyway is the source of truth for schema evolution.
- The frontend is a product interface over the canonical backend APIs.
- External APIs are inputs for import, enrichment, reconciliation, and imagery; they should not be treated as the durable source of truth.
- Generated or provider-derived data should be stored with enough source context to support future correction, replacement, and debugging.
- New technology should not be added lightly. Every dependency creates upgrade, security, compatibility, and lock-in obligations.

## Dependency policy

Before adding a new library, framework, provider, or infrastructure component, verify that it clearly improves the product or materially reduces implementation risk.

Prefer existing stack capabilities when they are good enough. Add dependencies only when the benefit is concrete and the maintenance cost is acceptable for a personal long-lived product.

# Roadmap By Domain

This roadmap is directional. It should guide product decisions without becoming a rigid release plan.

## Music

Current foundation:

- Spotify playback import
- Plex music events
- albums, artists, tracks, genres, and duplicate review
- MusicBrainz enrichment support
- music library, album, artist, and term pages

Future direction:

- enrich albums from sources beyond Plex
- support enrichment directly from album and artist pages
- improve correction and reconciliation of album, artist, track, genre, and image metadata
- make provider-derived metadata easier to inspect, replace, and trust
- deepen discovery from listening history, underplayed albums, tags, terms, and personal trends

## Books

Current foundation:

- Hardcover integration
- books, editions, authors, reads, reviews, and library views
- author and book detail pages

Future direction:

- improve personal reading history exploration
- strengthen author and book context pages
- connect books to other domains through mentions, themes, lists, and timeline events
- improve curation and correction of covers, editions, and reading sessions

## Movies

Current foundation:

- Plex watch import
- TMDb enrichment
- movies, people, companies, terms, lists, collections, watches, ratings, comments, and images
- movie library, detail, people, company, term, list, and collection pages

Future direction:

- improve cross-domain lists and thematic curation
- deepen people, company, collection, and term exploration
- make image selection and replacement more flexible
- improve manual correction and enrichment flows

## Shows

Current foundation:

- Plex watch import
- TMDb enrichment
- shows, seasons, episodes, people, watches, ratings, comments, and images
- show library, detail, season, progress, and timeline views

Future direction:

- improve continuing-watch and rewatch workflows
- deepen show, season, episode, people, and term connections
- improve manual correction and enrichment flows
- connect show activity into the global timeline and cross-domain lists

## Games

Current foundation:

- games schema
- IGDB and SteamGridDB integration
- game library and session-oriented UI

Future direction:

- improve game session capture and correction
- improve game imagery, metadata, and enrichment
- connect games to cross-domain lists, mentions, and timeline events
- support better personal context around play history, platforms, and completion state

## Cross-Domain Product

Future direction:

- connect domains through mentions and relationships
- add cross-domain lists
- add a global timeline across music, books, movies, shows, and games
- improve global search and navigation across related media
- support authentication and authorization for protecting the personal archive
- make import, enrichment, correction, and image workflows feel consistent across domains

# Spec-Driven Development Expectations

For new features or broad refactors, start from a short product spec before implementation.

A useful spec should include:

- the owner-facing problem
- the intended experience
- affected domain or domains
- data that must be stored locally
- external providers involved, if any
- main UI/API/data changes
- non-goals
- acceptance criteria

Specs should be practical and concise. They exist to keep product intent, data ownership, and implementation scope aligned before code is written.
