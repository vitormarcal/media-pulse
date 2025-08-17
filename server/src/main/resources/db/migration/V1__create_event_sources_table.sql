CREATE TABLE IF NOT EXISTS event_sources
(
    id            BIGSERIAL PRIMARY KEY,
    provider      VARCHAR(100)             NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE,
    payload       TEXT                     NOT NULL,
    fingerprint   VARCHAR(255) UNIQUE      NOT NULL,
    status        VARCHAR(20)              NOT NULL,
    error_message TEXT
);