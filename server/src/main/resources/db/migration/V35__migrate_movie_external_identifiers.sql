ALTER TABLE movies
ADD COLUMN tmdb_id TEXT,
ADD COLUMN imdb_id TEXT;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'MOVIE'
      AND (
        ei.provider NOT IN ('TMDB', 'IMDB')
        OR ei.external_entity_type IS NOT NULL
        OR BTRIM(ei.external_id) = ''
        OR (ei.provider = 'TMDB' AND ei.external_id !~ '^[0-9]+$')
        OR (ei.provider = 'IMDB' AND ei.external_id !~ '^tt[0-9]+$')
      )
  ) THEN
    RAISE EXCEPTION 'Invalid MOVIE external identifier found';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'MOVIE'
    GROUP BY ei.entity_id, ei.provider
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'Multiple external identifiers for the same movie and provider';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    LEFT JOIN movies m ON m.id = ei.entity_id
    WHERE ei.entity_type = 'MOVIE'
      AND m.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Orphan MOVIE external identifier found';
  END IF;
END $$;

UPDATE movies m
SET tmdb_id = ids.tmdb_id,
    imdb_id = ids.imdb_id
FROM (
  SELECT
    entity_id,
    MAX(external_id) FILTER (WHERE provider = 'TMDB') AS tmdb_id,
    MAX(external_id) FILTER (WHERE provider = 'IMDB') AS imdb_id
  FROM external_identifiers
  WHERE entity_type = 'MOVIE'
  GROUP BY entity_id
) ids
WHERE ids.entity_id = m.id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    JOIN movies m ON m.id = ei.entity_id
    WHERE ei.entity_type = 'MOVIE'
      AND (
        (ei.provider = 'TMDB' AND m.tmdb_id IS DISTINCT FROM ei.external_id)
        OR (ei.provider = 'IMDB' AND m.imdb_id IS DISTINCT FROM ei.external_id)
      )
  ) THEN
    RAISE EXCEPTION 'MOVIE external identifier copy validation failed';
  END IF;
END $$;

ALTER TABLE movies
ADD CONSTRAINT uq_movies_tmdb_id UNIQUE (tmdb_id),
ADD CONSTRAINT uq_movies_imdb_id UNIQUE (imdb_id);

DELETE FROM external_identifiers
WHERE entity_type = 'MOVIE';

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_entity_type_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_entity_type_check
CHECK (entity_type IN ('ARTIST','ALBUM','TRACK','SHOW','EPISODE'));
