CREATE TABLE IF NOT EXISTS album_terms (
  id              BIGSERIAL PRIMARY KEY,
  name            TEXT        NOT NULL,
  normalized_name TEXT        NOT NULL,
  slug            TEXT        NOT NULL,
  kind            TEXT        NOT NULL,
  source          TEXT        NOT NULL,
  hidden          BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_album_terms_kind_normalized_name
ON album_terms(kind, normalized_name);

CREATE INDEX IF NOT EXISTS idx_album_terms_kind_hidden
ON album_terms(kind, hidden);

CREATE TABLE IF NOT EXISTS album_term_assignments (
  id         BIGSERIAL PRIMARY KEY,
  album_id    BIGINT      NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
  term_id     BIGINT      NOT NULL REFERENCES album_terms(id) ON DELETE CASCADE,
  source      TEXT        NOT NULL,
  hidden      BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_album_term_assignments_album_term
ON album_term_assignments(album_id, term_id);

CREATE INDEX IF NOT EXISTS idx_album_term_assignments_album_hidden
ON album_term_assignments(album_id, hidden);

CREATE INDEX IF NOT EXISTS idx_album_term_assignments_term_hidden
ON album_term_assignments(term_id, hidden);
