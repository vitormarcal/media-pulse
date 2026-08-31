ALTER TABLE movie_collections
    ADD COLUMN overview TEXT,
    ADD COLUMN members_synced_at TIMESTAMPTZ,
    ADD COLUMN members_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN members_sync_error TEXT;

CREATE TABLE movie_collection_members (
    id BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES movie_collections(id) ON DELETE CASCADE,
    tmdb_id TEXT NOT NULL,
    title TEXT NOT NULL,
    original_title TEXT,
    release_year INTEGER,
    overview TEXT,
    poster_url TEXT,
    backdrop_url TEXT,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_movie_collection_members_identity
    ON movie_collection_members(collection_id, tmdb_id);

CREATE INDEX idx_movie_collection_members_order
    ON movie_collection_members(collection_id, position, id);

CREATE INDEX idx_movie_collections_members_pending
    ON movie_collections(members_synced_at, members_sync_attempted_at, id);
