ALTER TABLE tv_shows
    ADD COLUMN terms_synced_at TIMESTAMPTZ,
    ADD COLUMN terms_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN terms_sync_error TEXT;

CREATE TABLE show_terms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    slug VARCHAR(64) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    source VARCHAR(16) NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_show_terms_kind_normalized_name UNIQUE (kind, normalized_name)
);

CREATE INDEX idx_show_terms_kind_hidden ON show_terms(kind, hidden);

CREATE TABLE show_term_assignments (
    show_id BIGINT NOT NULL REFERENCES tv_shows(id) ON DELETE CASCADE,
    term_id BIGINT NOT NULL REFERENCES show_terms(id) ON DELETE CASCADE,
    source VARCHAR(16) NOT NULL,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_show_term_assignments_show_term UNIQUE (show_id, term_id)
);

CREATE INDEX idx_show_term_assignments_show_hidden ON show_term_assignments(show_id, hidden);
CREATE INDEX idx_show_term_assignments_term_hidden ON show_term_assignments(term_id, hidden);
CREATE INDEX idx_tv_shows_terms_enrichment_pending
    ON tv_shows(terms_sync_attempted_at, id)
    WHERE terms_synced_at IS NULL AND tmdb_id IS NOT NULL;
