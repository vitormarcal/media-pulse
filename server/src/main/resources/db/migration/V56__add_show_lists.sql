CREATE TABLE show_lists (
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    normalized_name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    description TEXT NULL,
    cover_show_id BIGINT NULL REFERENCES tv_shows(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX uq_show_lists_normalized_name ON show_lists(normalized_name);

CREATE TABLE show_list_items (
    id BIGSERIAL PRIMARY KEY,
    list_id BIGINT NOT NULL REFERENCES show_lists(id) ON DELETE CASCADE,
    show_id BIGINT NOT NULL REFERENCES tv_shows(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_show_list_items_identity UNIQUE (list_id, show_id)
);

CREATE INDEX idx_show_list_items_list_position ON show_list_items(list_id, position, id);
