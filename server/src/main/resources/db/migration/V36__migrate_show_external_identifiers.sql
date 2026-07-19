ALTER TABLE tv_shows
ADD COLUMN tmdb_id TEXT,
ADD COLUMN tvdb_id TEXT,
ADD COLUMN imdb_id TEXT;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'SHOW'
      AND (
        ei.provider NOT IN ('TMDB', 'TVDB', 'IMDB')
        OR ei.external_entity_type IS NOT NULL
        OR BTRIM(ei.external_id) = ''
        OR (ei.provider IN ('TMDB', 'TVDB') AND ei.external_id !~ '^[0-9]+$')
        OR (ei.provider = 'IMDB' AND ei.external_id !~ '^tt[0-9]+$')
      )
  ) THEN
    RAISE EXCEPTION 'Invalid SHOW external identifier found';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'SHOW'
    GROUP BY ei.entity_id, ei.provider
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'Multiple external identifiers for the same show and provider';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    LEFT JOIN tv_shows s ON s.id = ei.entity_id
    WHERE ei.entity_type = 'SHOW'
      AND s.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Orphan SHOW external identifier found';
  END IF;
END $$;

UPDATE tv_shows s
SET tmdb_id = ids.tmdb_id,
    tvdb_id = ids.tvdb_id,
    imdb_id = ids.imdb_id
FROM (
  SELECT
    entity_id,
    MAX(external_id) FILTER (WHERE provider = 'TMDB') AS tmdb_id,
    MAX(external_id) FILTER (WHERE provider = 'TVDB') AS tvdb_id,
    MAX(external_id) FILTER (WHERE provider = 'IMDB') AS imdb_id
  FROM external_identifiers
  WHERE entity_type = 'SHOW'
  GROUP BY entity_id
) ids
WHERE ids.entity_id = s.id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    JOIN tv_shows s ON s.id = ei.entity_id
    WHERE ei.entity_type = 'SHOW'
      AND (
        (ei.provider = 'TMDB' AND s.tmdb_id IS DISTINCT FROM ei.external_id)
        OR (ei.provider = 'TVDB' AND s.tvdb_id IS DISTINCT FROM ei.external_id)
        OR (ei.provider = 'IMDB' AND s.imdb_id IS DISTINCT FROM ei.external_id)
      )
  ) THEN
    RAISE EXCEPTION 'SHOW external identifier copy validation failed';
  END IF;
END $$;

ALTER TABLE tv_shows
ADD CONSTRAINT uq_tv_shows_tmdb_id UNIQUE (tmdb_id),
ADD CONSTRAINT uq_tv_shows_tvdb_id UNIQUE (tvdb_id),
ADD CONSTRAINT uq_tv_shows_imdb_id UNIQUE (imdb_id);

DELETE FROM external_identifiers
WHERE entity_type = 'SHOW';

DROP INDEX IF EXISTS idx_ext_ids_show;

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_entity_type_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_entity_type_check
CHECK (entity_type IN ('ARTIST','ALBUM','TRACK','EPISODE'));
