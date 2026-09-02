ALTER TABLE movies
    ADD COLUMN terms_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN terms_sync_error TEXT,
    ADD COLUMN credits_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN credits_sync_error TEXT,
    ADD COLUMN companies_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN companies_sync_error TEXT;

CREATE INDEX idx_movies_terms_enrichment_pending
    ON movies(terms_sync_attempted_at, id)
    WHERE terms_synced_at IS NULL AND tmdb_id IS NOT NULL;

CREATE INDEX idx_movies_credits_enrichment_pending
    ON movies(credits_sync_attempted_at, id)
    WHERE credits_synced_at IS NULL AND tmdb_id IS NOT NULL;

CREATE INDEX idx_movies_companies_enrichment_pending
    ON movies(companies_sync_attempted_at, id)
    WHERE companies_synced_at IS NULL AND tmdb_id IS NOT NULL;
