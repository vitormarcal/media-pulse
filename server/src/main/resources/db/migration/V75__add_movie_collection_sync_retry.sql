ALTER TABLE movies
    ADD COLUMN collection_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN collection_sync_error TEXT;
