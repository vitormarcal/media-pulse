ALTER TABLE book_author_profiles ADD COLUMN managed_manually BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE hardcover_author_candidates (
    author_id BIGINT NOT NULL REFERENCES authors(id) ON DELETE CASCADE,
    hardcover_id BIGINT NOT NULL CHECK (hardcover_id >= 0),
    last_attempt_at TIMESTAMPTZ,
    PRIMARY KEY (author_id, hardcover_id)
);
CREATE INDEX hardcover_author_candidates_external_idx ON hardcover_author_candidates(hardcover_id);
