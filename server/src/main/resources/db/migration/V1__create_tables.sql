CREATE TABLE event_sources
(
    id            BIGSERIAL PRIMARY KEY,
    provider      VARCHAR(100)             NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    update_at     TIMESTAMP WITH TIME ZONE,
    payload       TEXT                     NOT NULL,
    fingerprint   VARCHAR(255) UNIQUE      NOT NULL,
    status        VARCHAR(20)              NOT NULL,
    error_message TEXT
);

CREATE TABLE canonical_tracks
(
    id             BIGSERIAL PRIMARY KEY,
    canonical_id   VARCHAR(255) NOT NULL,
    canonical_type VARCHAR(255) NOT NULL,
    title          VARCHAR(255),
    album          VARCHAR(255),
    artist         VARCHAR(255),
    CONSTRAINT uq_canonical_tracks_canonical_id_type UNIQUE (canonical_id, canonical_type)
);

CREATE TABLE track_playbacks
(
    id                 BIGSERIAL PRIMARY KEY,
    canonical_track_id BIGINT                   NOT NULL,
    source_event_id    BIGINT,
    playback_source    VARCHAR(50)              NOT NULL,
    played_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_track_playbacks_canonical_track FOREIGN KEY (canonical_track_id) REFERENCES canonical_tracks (id) ON DELETE CASCADE,
    CONSTRAINT fk_track_playbacks_source_event FOREIGN KEY (source_event_id) REFERENCES event_sources (id) ON DELETE SET NULL
);

CREATE INDEX idx_track_playbacks_canonical_track_id ON track_playbacks (canonical_track_id);
