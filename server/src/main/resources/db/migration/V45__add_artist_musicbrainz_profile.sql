ALTER TABLE artists
  ADD COLUMN IF NOT EXISTS artist_type TEXT,
  ADD COLUMN IF NOT EXISTS country_code TEXT,
  ADD COLUMN IF NOT EXISTS area_name TEXT,
  ADD COLUMN IF NOT EXISTS begin_area_name TEXT,
  ADD COLUMN IF NOT EXISTS life_span_begin TEXT,
  ADD COLUMN IF NOT EXISTS life_span_end TEXT,
  ADD COLUMN IF NOT EXISTS life_span_ended BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS disambiguation TEXT,
  ADD COLUMN IF NOT EXISTS musicbrainz_synced_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS musicbrainz_sync_error TEXT;

CREATE TABLE artist_aliases (
  id          BIGSERIAL PRIMARY KEY,
  artist_id   BIGINT      NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
  name        TEXT        NOT NULL,
  locale      TEXT,
  sort_name   TEXT,
  alias_type  TEXT,
  is_primary  BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_artist_aliases_artist_name_locale
ON artist_aliases(artist_id, LOWER(name), COALESCE(locale, ''));

CREATE TABLE artist_external_links (
  id          BIGSERIAL PRIMARY KEY,
  artist_id   BIGINT      NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
  link_type   TEXT        NOT NULL,
  url         TEXT        NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_artist_external_links_artist_type
ON artist_external_links(artist_id, link_type);

CREATE TABLE artist_term_assignments (
  id          BIGSERIAL PRIMARY KEY,
  artist_id   BIGINT      NOT NULL REFERENCES artists(id) ON DELETE CASCADE,
  term_id     BIGINT      NOT NULL REFERENCES album_terms(id) ON DELETE CASCADE,
  source      TEXT        NOT NULL,
  hidden      BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_artist_term_assignments_artist_term
ON artist_term_assignments(artist_id, term_id);

CREATE INDEX idx_artist_term_assignments_artist_hidden
ON artist_term_assignments(artist_id, hidden);
