ALTER TABLE movie_companies
    ADD COLUMN members_synced_at TIMESTAMPTZ,
    ADD COLUMN members_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN members_sync_error TEXT;

CREATE TABLE movie_company_members (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES movie_companies(id) ON DELETE CASCADE,
    tmdb_id TEXT NOT NULL,
    title TEXT NOT NULL,
    original_title TEXT,
    release_year INTEGER,
    overview TEXT,
    poster_url TEXT,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_movie_company_members_identity
    ON movie_company_members(company_id, tmdb_id);

CREATE INDEX idx_movie_company_members_order
    ON movie_company_members(company_id, position, id);

CREATE INDEX idx_movie_companies_members_pending
    ON movie_companies(members_synced_at, members_sync_attempted_at, id);
