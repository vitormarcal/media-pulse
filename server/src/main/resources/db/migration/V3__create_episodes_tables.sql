CREATE TABLE IF NOT EXISTS episode_sources (
  id              BIGSERIAL PRIMARY KEY,
  show_title      VARCHAR(255) NOT NULL,
  season_number   INTEGER       NOT NULL,
  episode_number  INTEGER       NOT NULL,
  episode_title   VARCHAR(255)  NOT NULL,
  year            INTEGER,
  fingerprint     VARCHAR(255) UNIQUE NOT NULL,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS episode_source_identifiers (
  id                   BIGSERIAL PRIMARY KEY,
  external_type        VARCHAR(50) NOT NULL,
  external_id          VARCHAR(50) NOT NULL,
  episode_source_id    BIGINT NOT NULL REFERENCES episode_sources(id) ON DELETE CASCADE,
  created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_episode_identifier UNIQUE (external_type, external_id, episode_source_id)
);

CREATE TABLE IF NOT EXISTS episode_playbacks (
  id                 BIGSERIAL PRIMARY KEY,
  episode_source_id  BIGINT NOT NULL REFERENCES episode_sources(id) ON DELETE CASCADE,
  playback_source    VARCHAR(50) NOT NULL,
  played_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  source_event_id    BIGINT REFERENCES event_sources(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_episode_playbacks_source_id ON episode_playbacks (episode_source_id);
CREATE INDEX IF NOT EXISTS idx_episode_playbacks_played_at ON episode_playbacks (played_at);