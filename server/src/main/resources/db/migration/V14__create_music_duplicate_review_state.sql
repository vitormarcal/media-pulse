CREATE TABLE IF NOT EXISTS music_duplicate_review_ignored (
  album_id    BIGINT      NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
  title_key   TEXT        NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (album_id, title_key)
);

CREATE INDEX IF NOT EXISTS idx_music_duplicate_review_ignored_album
ON music_duplicate_review_ignored(album_id);
