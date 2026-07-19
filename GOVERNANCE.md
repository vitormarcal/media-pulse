Before implementing or changing any Media Pulse functionality, also read `PRODUCT_CONSTITUTION.md`.

`PRODUCT_CONSTITUTION.md` defines the mission, product principles, stack, and roadmap.
This `GOVERNANCE.md` defines engineering, architecture, and execution rules.

Before implementing or changing any Media Pulse functionality, follow these rules.

# Feature Discovery

Before starting a new feature or broad refactor, consult:

* `docs/feature-discovery.md`

This workflow defines how features should be refined before implementation in order to keep scope, product intent, and implementation complexity aligned.

# Objective

Keep the project simple, incremental, safe, and consistent with the existing architecture.

# Mandatory Architecture

## 1. Namespace and Structure

The backend lives under `dev.marcal.mediapulse.server`.

Expected base structure:

* `api/` -> HTTP contracts (request/response DTOs)
* `config/` -> Spring configuration and properties binding
* `controller/` -> HTTP endpoints
* `integration/` -> external integrations and clients
* `model/` -> internal domain and integration models
* `repository/` -> data access
* `repository/query/` and read repositories -> read-only/report queries
* `repository/crud/` and specific repositories -> persistence and operational lookup
* `service/` -> business rules, pipelines, and orchestration
* `util/` -> technical helpers

Do not introduce a parallel structure without a clear need.

## 2. Persistence

* Every schema change must go through Flyway in `server/src/main/resources/db/migration`
* Do not create tables outside migrations
* Do not rely on `ddl-auto` for schema evolution

## 3. Code Style

* Idiomatic Kotlin
* Simple DTOs and models using `data class` where appropriate
* Thin controllers
* Business rules belong in `service`
* Repositories are responsible for data access, not business flow
* Avoid giant classes and premature abstractions

## 4. Tests

When a change affects relevant behavior, add or update tests proportional to the risk:

* service tests for important business rules
* repository tests for critical query/persistence behavior
* integration tests when HTTP contracts or cross-layer flows are sensitive

Full coverage is not required, but critical parts should not remain unvalidated.

## 5. Documentation

Update documentation whenever there are changes to:

* migrations
* HTTP endpoints
* variables/configuration
* relevant operational behavior
* non-obvious decisions discovered during debugging or integration

Files to review depending on the change:

* `README.md`
* `docs/*.md`
* `docs/openapi.yaml` when the published contract changes
* `frontend/README.md` if the UI/local dev flow changes

## 6. Rule of Simplicity

If two viable solutions exist:

* choose the simpler one
* avoid extra frameworks
* avoid premature abstractions

## 7. Incremental Rule

For larger changes:

* write a short checklist
* explicitly list the main files to be changed
* mention required migrations, if any
* confirm the acceptance criteria before implementation

## 8. Scope

* Do not add functionality outside the requested scope
* Do not incidentally fix unrelated parts without necessity

## 9. Minimum Quality Criteria

The final result must:

* compile
* start with `./server/gradlew bootRun` when the required configuration is present
* preserve clear separation of responsibilities
* avoid leaving documentation contradicting real code behavior

## 10. Mandatory Finalization

After backend code changes:

* run `./server/gradlew ktlintFormat`
* fix issues revealed by formatting or related checks
* only finish when the state is consistent

If the task is documentation-only and no Kotlin file was changed, running `ktlintFormat` is not necessary.

## 11. Documentation-First

* Before implementing or debugging, consult `README.md`, `docs/`, and migration notes when relevant
* Reuse already documented patterns instead of creating parallel paths
* If docs and code diverge, explicitly align one side

## 12. Operational Hygiene

* Do not document real secrets in versioned files
* Prefer environment variables and neutral examples
* When documenting external provider behavior, record symptoms, hypothesis/cause, chosen decision, and how to validate it
* Never read `server/src/main/resources/application-local.yml` for analysis, documentation, or implementation
* Treat `application-local.yml` as a user-local file for real-data testing, outside the normal inspection scope

## 13. Mandatory Design

Every frontend change must consult and follow `DESIGN.md` before implementation.

Rules:

* `DESIGN.md` is the source of truth for visual direction, UX, tone, hierarchy, grid, color, typography, spacing, and navigation
* do not introduce visual patterns, components, or flows that contradict `DESIGN.md` without explicit justification
* preserve consistency between already implemented pages
* if code and `DESIGN.md` diverge, explicitly align one side
* if a non-obvious design decision affecting future patterns emerges, document it

Before implementing frontend changes, explicitly state:

1. which parts of `DESIGN.md` guide the solution
2. which main files will be changed
3. how visual consistency with the existing product will be preserved

# Rule for New Features

Before writing code for a new feature or broad refactor, you must:

1. write a short checklist with at most 12 items
2. state which main files will be created/changed
3. inform whether there will be a migration
4. confirm the acceptance criteria

Only then implement.
