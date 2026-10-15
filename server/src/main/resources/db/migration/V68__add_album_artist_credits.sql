CREATE TABLE album_artists (
    album_id BIGINT NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    position INT NOT NULL CHECK (position >= 0),
    artist_id BIGINT NOT NULL REFERENCES artists(id) ON DELETE RESTRICT,
    credited_name TEXT NOT NULL CHECK (btrim(credited_name) <> ''),
    join_phrase TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL CHECK (source IN ('LEGACY', 'MUSICBRAINZ_CONFIRMED')),
    PRIMARY KEY (album_id, position)
);

CREATE INDEX idx_album_artists_artist ON album_artists(artist_id);

INSERT INTO album_artists(album_id, position, artist_id, credited_name, join_phrase, source)
SELECT al.id, 0, a.id, a.name, '', 'LEGACY'
FROM albums al JOIN artists a ON a.id = al.artist_id;
