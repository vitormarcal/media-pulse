CREATE TABLE artist_biographies (
    artist_id BIGINT PRIMARY KEY REFERENCES artists(id) ON DELETE CASCADE,
    snapshot JSONB NOT NULL
);
