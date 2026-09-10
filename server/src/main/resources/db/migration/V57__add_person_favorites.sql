ALTER TABLE people ADD COLUMN favorited_at TIMESTAMPTZ;

CREATE INDEX idx_people_favorites
    ON people(favorited_at DESC, id)
    WHERE favorited_at IS NOT NULL;
