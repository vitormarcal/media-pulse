CREATE TABLE person_filmography_syncs (
    person_id BIGINT NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    media_type TEXT NOT NULL CHECK (media_type IN ('MOVIE', 'SHOW')),
    synced_at TIMESTAMPTZ,
    sync_attempted_at TIMESTAMPTZ,
    sync_error TEXT,
    PRIMARY KEY (person_id, media_type)
);

CREATE TABLE person_filmography_members (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL REFERENCES people(id) ON DELETE CASCADE,
    media_type TEXT NOT NULL CHECK (media_type IN ('MOVIE', 'SHOW')),
    tmdb_id TEXT NOT NULL,
    title TEXT NOT NULL,
    original_title TEXT,
    release_year INTEGER,
    overview TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    role_label TEXT NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_person_filmography_members_identity
    ON person_filmography_members(person_id, media_type, tmdb_id);

CREATE INDEX idx_person_filmography_members_order
    ON person_filmography_members(person_id, media_type, position, id);

CREATE INDEX idx_person_filmography_syncs_pending
    ON person_filmography_syncs(media_type, synced_at, sync_attempted_at, person_id);
