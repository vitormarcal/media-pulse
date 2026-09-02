ALTER TABLE tv_shows
    ADD COLUMN credits_sync_attempted_at TIMESTAMPTZ,
    ADD COLUMN credits_sync_error TEXT;

CREATE INDEX idx_tv_shows_credits_pending
    ON tv_shows(credits_synced_at, credits_sync_attempted_at, id);
