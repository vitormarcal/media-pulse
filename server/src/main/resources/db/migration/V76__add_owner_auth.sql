CREATE TABLE owner_credentials (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    password_hash TEXT,
    generation BIGINT NOT NULL DEFAULT 0
);
INSERT INTO owner_credentials (id) VALUES (1);

CREATE TABLE owner_reset_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
