ALTER TABLE movies
ADD COLUMN companies_synced_at TIMESTAMPTZ NULL;

CREATE TABLE movie_companies (
    id BIGSERIAL PRIMARY KEY,
    tmdb_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    logo_url TEXT NULL,
    origin_country TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_movie_companies_normalized_name
    ON movie_companies(normalized_name);

CREATE TABLE movie_company_assignments (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES movie_companies(id) ON DELETE CASCADE,
    company_type TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_movie_company_assignments_type CHECK (company_type IN ('PRODUCTION')),
    CONSTRAINT uq_movie_company_assignments_identity UNIQUE (movie_id, company_id, company_type)
);

CREATE INDEX idx_movie_company_assignments_movie
    ON movie_company_assignments(movie_id, company_type);

CREATE INDEX idx_movie_company_assignments_company
    ON movie_company_assignments(company_id, company_type);
