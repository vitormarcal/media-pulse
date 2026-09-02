ALTER TABLE movies
    ADD COLUMN tmdb_resolution_state TEXT,
    ADD COLUMN tmdb_resolution_error TEXT;

ALTER TABLE movies
    ADD CONSTRAINT chk_movies_tmdb_resolution_state
    CHECK (tmdb_resolution_state IS NULL OR tmdb_resolution_state IN ('NOT_FOUND', 'FAILED'));

UPDATE movies
SET tmdb_resolution_state = 'FAILED'
WHERE tmdb_id IS NULL
  AND imdb_id IS NOT NULL
  AND tmdb_resolution_checked_at IS NOT NULL;
