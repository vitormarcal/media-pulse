ALTER TABLE people
    ADD COLUMN biography TEXT,
    ADD COLUMN birthday TEXT,
    ADD COLUMN deathday TEXT,
    ADD COLUMN place_of_birth TEXT,
    ADD COLUMN known_for_department TEXT,
    ADD COLUMN homepage TEXT,
    ADD COLUMN imdb_id TEXT,
    ADD COLUMN popularity DOUBLE PRECISION,
    ADD COLUMN tmdb_synced_at TIMESTAMPTZ,
    ADD COLUMN tmdb_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN tmdb_sync_error TEXT;

CREATE TABLE person_aliases (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_person_aliases_person_name
    ON person_aliases(person_id, LOWER(name));

CREATE INDEX idx_people_tmdb_enrichment_pending
    ON people(tmdb_synced_at, tmdb_sync_attempted_at, id);
