CREATE TABLE book_author_profiles (
    author_id BIGINT PRIMARY KEY REFERENCES authors(id) ON DELETE CASCADE,
    hardcover_id BIGINT NOT NULL UNIQUE CHECK (hardcover_id > 0),
    snapshot JSONB NOT NULL,
    CHECK ((snapshot->>'hardcoverId')::BIGINT = hardcover_id)
);
