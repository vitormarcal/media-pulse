ALTER TABLE movies
ADD COLUMN IF NOT EXISTS slug TEXT;

CREATE INDEX IF NOT EXISTS idx_movies_slug ON movies(slug);
