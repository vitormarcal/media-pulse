CREATE TABLE album_lists (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL UNIQUE,
    slug TEXT NOT NULL UNIQUE,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE album_list_items (
    id BIGSERIAL PRIMARY KEY,
    list_id BIGINT NOT NULL REFERENCES album_lists(id) ON DELETE CASCADE,
    album_id BIGINT NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position > 0),
    listened_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT album_list_items_identity_unique UNIQUE (list_id, album_id)
);

CREATE INDEX idx_album_list_items_list_position
    ON album_list_items(list_id, position, id);

CREATE INDEX idx_album_list_items_list_listened_at
    ON album_list_items(list_id, listened_at DESC NULLS LAST);
