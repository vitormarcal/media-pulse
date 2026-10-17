-- Durable attempts and post-commit cleanup; file transfer runs in the application.
CREATE TABLE local_image_migrations (
    local_url TEXT PRIMARY KEY,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    remote_url TEXT,
    sha256 VARCHAR(64),
    cleaned BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK ((remote_url IS NULL) = (sha256 IS NULL))
);
CREATE INDEX local_image_migrations_pending_cleanup
    ON local_image_migrations(attempted_at) WHERE remote_url IS NOT NULL AND NOT cleaned;
