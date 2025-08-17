CREATE TABLE IF NOT EXISTS music_sources
(
    id     BIGSERIAL PRIMARY KEY,
    title  VARCHAR(255),
    album  VARCHAR(255),
    artist VARCHAR(255),
    year   INTEGER,
    fingerprint   VARCHAR(255) UNIQUE      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE

);

CREATE TABLE IF NOT EXISTS music_source_identifiers
(
    id              BIGSERIAL PRIMARY KEY,
    external_type     VARCHAR(50) NOT NULL,
    external_id     VARCHAR(50) NOT NULL,
    music_source_id BIGINT      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_music_source_identifiers_music_source FOREIGN KEY (music_source_id) REFERENCES music_sources (id) ON DELETE CASCADE,
    CONSTRAINT uq_music_source_identifier UNIQUE (external_type, external_id, music_source_id)
);


CREATE TABLE IF NOT EXISTS track_playbacks
(
    id              BIGSERIAL PRIMARY KEY,
    music_source_id BIGINT                   NOT NULL,
    source_event_id BIGINT,
    playback_source VARCHAR(50)              NOT NULL,
    played_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_track_playbacks_music_sources FOREIGN KEY (music_source_id) REFERENCES music_sources (id) ON DELETE CASCADE,
    CONSTRAINT fk_track_playbacks_source_event FOREIGN KEY (source_event_id) REFERENCES event_sources (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_music_source_identifiers_music_source_id ON music_source_identifiers (music_source_id);
CREATE INDEX IF NOT EXISTS idx_track_playbacks_music_source_id ON track_playbacks (music_source_id);
