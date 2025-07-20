CREATE TABLE webhook_events
(
    id              SERIAL PRIMARY KEY,
    provider        VARCHAR(100) NOT NULL,
    event_timestamp TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    payload         text        NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed       BOOLEAN               DEFAULT FALSE
);
