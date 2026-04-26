ALTER TABLE movies
ADD COLUMN IF NOT EXISTS terms_synced_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_movies_terms_synced_at ON movies(terms_synced_at);
