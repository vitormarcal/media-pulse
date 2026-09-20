ALTER TABLE movie_lists
    ADD COLUMN include_favorites BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN include_abandoned BOOLEAN NOT NULL DEFAULT FALSE;

-- Read-only union: manual membership wins, automatic membership is never persisted.
CREATE VIEW movie_list_members AS
SELECT i.id, i.list_id, i.movie_id, i.position
FROM movie_list_items i
UNION ALL
SELECT -m.id AS id, l.id AS list_id, m.id AS movie_id, NULL::INTEGER AS position
FROM movie_lists l
JOIN movies m ON (l.include_favorites AND m.favorite) OR (l.include_abandoned AND m.abandoned)
WHERE NOT EXISTS (
    SELECT 1 FROM movie_list_items i WHERE i.list_id = l.id AND i.movie_id = m.id
);

ALTER TABLE show_lists
    ADD COLUMN include_favorites BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN include_abandoned BOOLEAN NOT NULL DEFAULT FALSE;

-- Read-only union: manual membership wins, automatic membership is never persisted.
CREATE VIEW show_list_members AS
SELECT i.id, i.list_id, i.show_id, i.position
FROM show_list_items i
UNION ALL
SELECT -m.id AS id, l.id AS list_id, m.id AS show_id, NULL::INTEGER AS position
FROM show_lists l
JOIN tv_shows m ON (l.include_favorites AND m.favorite) OR (l.include_abandoned AND m.abandoned)
WHERE NOT EXISTS (
    SELECT 1 FROM show_list_items i WHERE i.list_id = l.id AND i.show_id = m.id
);
