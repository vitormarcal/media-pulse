CREATE TABLE media_ratings (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    rating SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT media_ratings_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT media_ratings_entity_unique UNIQUE (entity_type, entity_id)
);

CREATE INDEX idx_media_ratings_entity ON media_ratings(entity_type, entity_id);
