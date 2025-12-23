CREATE UNIQUE INDEX IF NOT EXISTS uq_playbacks_source_track_time
    ON track_playbacks(source, track_id, played_at);

CREATE TABLE IF NOT EXISTS spotify_sync_state (
     id BIGSERIAL PRIMARY KEY,
     cursor_after_ms BIGINT NOT NULL DEFAULT 0,
     updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);