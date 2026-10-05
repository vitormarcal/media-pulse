CREATE TABLE magazine_publications (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    normalized_name VARCHAR(200) NOT NULL UNIQUE,
    issn VARCHAR(9) UNIQUE
);
CREATE TABLE magazine_issues (
    id BIGSERIAL PRIMARY KEY,
    publication_id BIGINT NOT NULL REFERENCES magazine_publications(id),
    number VARCHAR(100),
    cover_date VARCHAR(7),
    identity_key VARCHAR(120) NOT NULL,
    total_pages INTEGER CHECK (total_pages > 0),
    cover_url TEXT,
    activity_date DATE NOT NULL DEFAULT CURRENT_DATE,
    CONSTRAINT magazine_issue_identity UNIQUE (publication_id, identity_key),
    CHECK (number IS NOT NULL OR cover_date IS NOT NULL),
    CHECK (cover_date IS NULL OR cover_date ~ '^[0-9]{4}-(0[1-9]|1[0-2])$')
);
CREATE TABLE magazine_reads (
    id BIGSERIAL PRIMARY KEY,
    issue_id BIGINT NOT NULL REFERENCES magazine_issues(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL CHECK (status IN ('WANT_TO_READ', 'CURRENTLY_READING', 'READ', 'DID_NOT_FINISH')),
    started_at DATE,
    finished_at DATE,
    progress_pct DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (progress_pct >= 0 AND progress_pct <= 100),
    current_page INTEGER CHECK (current_page >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CHECK (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at)),
    CHECK (status <> 'READ' OR (started_at IS NOT NULL AND finished_at IS NOT NULL AND progress_pct = 100)),
    CHECK (status NOT IN ('CURRENTLY_READING', 'DID_NOT_FINISH') OR started_at IS NOT NULL),
    CHECK (status <> 'WANT_TO_READ' OR (started_at IS NULL AND finished_at IS NULL AND progress_pct = 0 AND current_page IS NULL)),
    CHECK (status = 'READ' OR finished_at IS NULL)
);
CREATE UNIQUE INDEX magazine_one_open_read ON magazine_reads(issue_id) WHERE status IN ('WANT_TO_READ', 'CURRENTLY_READING');
CREATE INDEX magazine_reads_issue ON magazine_reads(issue_id, id DESC);
CREATE INDEX magazine_issues_activity ON magazine_issues(activity_date DESC, id DESC);
CREATE INDEX magazine_issues_publication ON magazine_issues(publication_id);
