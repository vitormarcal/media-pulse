CREATE TABLE artist_name_aliases (
  id         BIGSERIAL PRIMARY KEY,
  artist_id  BIGINT      NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
  name       TEXT        NOT NULL,
  name_key   TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT artist_name_aliases_name_key_unique UNIQUE (name_key)
);

CREATE INDEX idx_artist_name_aliases_artist ON artist_name_aliases(artist_id);

DROP INDEX IF EXISTS uq_albums_artist_titlekey_year_notnull;
DROP INDEX IF EXISTS uq_albums_artist_titlekey_year_null;

CREATE INDEX idx_albums_artist_titlekey_year_notnull
  ON albums(artist_id, title_key, year)
  WHERE year IS NOT NULL;

CREATE INDEX idx_albums_artist_titlekey_year_null
  ON albums(artist_id, title_key)
  WHERE year IS NULL;
