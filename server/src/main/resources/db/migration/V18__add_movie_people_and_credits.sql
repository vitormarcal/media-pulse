ALTER TABLE movies
ADD COLUMN credits_synced_at TIMESTAMPTZ NULL;

CREATE TABLE movie_people (
    id BIGSERIAL PRIMARY KEY,
    tmdb_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    profile_url TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_movie_people_normalized_name
    ON movie_people(normalized_name);

CREATE TABLE movie_credits (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES movie_people(id) ON DELETE CASCADE,
    credit_type TEXT NOT NULL,
    department TEXT NOT NULL DEFAULT '',
    job TEXT NOT NULL DEFAULT '',
    character_name TEXT NOT NULL DEFAULT '',
    billing_order INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_movie_credits_type CHECK (credit_type IN ('CAST', 'CREW')),
    CONSTRAINT uq_movie_credits_identity UNIQUE (movie_id, person_id, credit_type, job, character_name)
);

CREATE INDEX idx_movie_credits_movie
    ON movie_credits(movie_id, credit_type, billing_order);

CREATE INDEX idx_movie_credits_person
    ON movie_credits(person_id, credit_type);
