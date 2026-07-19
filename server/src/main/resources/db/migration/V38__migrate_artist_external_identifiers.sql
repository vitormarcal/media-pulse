ALTER TABLE artists
ADD COLUMN spotify_id TEXT,
ADD COLUMN musicbrainz_artist_id TEXT;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'ARTIST'
      AND (
        ei.provider NOT IN ('SPOTIFY', 'MUSICBRAINZ')
        OR (ei.provider = 'SPOTIFY' AND ei.external_entity_type IS NOT NULL)
        OR (
          ei.provider = 'MUSICBRAINZ'
          AND ei.external_entity_type IS NOT NULL
          AND ei.external_entity_type <> 'ARTIST'
        )
        OR BTRIM(ei.external_id) = ''
        OR (
          ei.provider = 'MUSICBRAINZ'
          AND ei.external_id !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'
        )
        OR (ei.provider = 'SPOTIFY' AND ei.external_id !~ '^[A-Za-z0-9]{22}$')
      )
  ) THEN
    RAISE EXCEPTION 'Invalid ARTIST external identifier found';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'ARTIST'
    GROUP BY ei.entity_id, ei.provider
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'Multiple external identifiers for the same artist and provider';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    LEFT JOIN artists a ON a.id = ei.entity_id
    WHERE ei.entity_type = 'ARTIST'
      AND a.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Orphan ARTIST external identifier found';
  END IF;
END $$;

UPDATE artists a
SET spotify_id = ids.spotify_id,
    musicbrainz_artist_id = ids.musicbrainz_artist_id
FROM (
  SELECT
    entity_id,
    MAX(external_id) FILTER (WHERE provider = 'SPOTIFY') AS spotify_id,
    MAX(external_id) FILTER (WHERE provider = 'MUSICBRAINZ') AS musicbrainz_artist_id
  FROM external_identifiers
  WHERE entity_type = 'ARTIST'
  GROUP BY entity_id
) ids
WHERE ids.entity_id = a.id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    JOIN artists a ON a.id = ei.entity_id
    WHERE ei.entity_type = 'ARTIST'
      AND (
        (ei.provider = 'SPOTIFY' AND a.spotify_id IS DISTINCT FROM ei.external_id)
        OR (
          ei.provider = 'MUSICBRAINZ'
          AND a.musicbrainz_artist_id IS DISTINCT FROM ei.external_id
        )
      )
  ) THEN
    RAISE EXCEPTION 'ARTIST external identifier copy validation failed';
  END IF;
END $$;

ALTER TABLE artists
ADD CONSTRAINT uq_artists_spotify_id UNIQUE (spotify_id),
ADD CONSTRAINT uq_artists_musicbrainz_artist_id UNIQUE (musicbrainz_artist_id);

DELETE FROM external_identifiers
WHERE entity_type = 'ARTIST';

DROP INDEX IF EXISTS idx_ext_ids_artist;

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_entity_type_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_entity_type_check
CHECK (entity_type IN ('ALBUM','TRACK'));
