ALTER TABLE album_artists DROP CONSTRAINT album_artists_source_check;
ALTER TABLE album_artists ADD CONSTRAINT album_artists_source_check
    CHECK (source IN ('LEGACY', 'SPOTIFY', 'MUSICBRAINZ_CONFIRMED'));
