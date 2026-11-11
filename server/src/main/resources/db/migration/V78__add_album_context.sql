CREATE TABLE album_context (
    album_id BIGINT PRIMARY KEY REFERENCES albums(id) ON DELETE CASCADE,
    snapshot JSONB NOT NULL
);
