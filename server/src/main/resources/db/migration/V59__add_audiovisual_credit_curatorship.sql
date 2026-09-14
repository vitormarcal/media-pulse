ALTER TABLE movies
    ADD COLUMN credits_curated_at TIMESTAMPTZ;

ALTER TABLE tv_shows
    ADD COLUMN credits_curated_at TIMESTAMPTZ;

