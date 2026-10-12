CREATE TABLE artwork_artists (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(trim(name)) > 0),
    biography TEXT,
    nationality VARCHAR(200),
    born_at DATE,
    died_at DATE,
    portrait_url TEXT,
    CHECK (died_at IS NULL OR born_at IS NULL OR died_at >= born_at)
);
CREATE TABLE artworks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL CHECK (length(trim(title)) > 0),
    artist_id BIGINT REFERENCES artwork_artists(id),
    year INTEGER,
    type VARCHAR(100),
    movement VARCHAR(200),
    technique VARCHAR(200),
    description TEXT,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE TABLE artwork_experiences (
    id BIGSERIAL PRIMARY KEY,
    artwork_id BIGINT NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    experienced_at DATE,
    experience_type VARCHAR(32) CHECK (experience_type IN ('DIGITAL_VIEWING', 'IN_PERSON')),
    notes TEXT,
    source_name VARCHAR(300),
    source_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE TABLE artwork_images (
    id BIGSERIAL PRIMARY KEY,
    artwork_id BIGINT NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    source_url TEXT,
    caption VARCHAR(500),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX artwork_one_primary_image ON artwork_images(artwork_id) WHERE is_primary;
CREATE TABLE artwork_lists (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(trim(name)) > 0),
    description TEXT
);
CREATE TABLE artwork_list_items (
    id BIGSERIAL PRIMARY KEY,
    list_id BIGINT NOT NULL REFERENCES artwork_lists(id) ON DELETE CASCADE,
    artwork_id BIGINT NOT NULL REFERENCES artworks(id) ON DELETE CASCADE,
    UNIQUE (list_id, artwork_id)
);
CREATE INDEX artworks_artist ON artworks(artist_id);
CREATE INDEX artwork_experiences_work ON artwork_experiences(artwork_id, experienced_at DESC, id DESC);
CREATE INDEX artwork_images_work ON artwork_images(artwork_id, id);
CREATE INDEX artwork_list_items_work ON artwork_list_items(artwork_id);
