ALTER TABLE games
ADD COLUMN igdb_id TEXT,
ADD COLUMN steamgriddb_id TEXT;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'GAME'
      AND (
        ei.provider NOT IN ('IGDB', 'STEAMGRIDDB')
        OR ei.external_entity_type IS NOT NULL
        OR BTRIM(ei.external_id) = ''
      )
  ) THEN
    RAISE EXCEPTION 'Invalid GAME external identifier found';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    WHERE ei.entity_type = 'GAME'
    GROUP BY ei.entity_id, ei.provider
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'Multiple external identifiers for the same game and provider';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    LEFT JOIN games g ON g.id = ei.entity_id
    WHERE ei.entity_type = 'GAME'
      AND g.id IS NULL
  ) THEN
    RAISE EXCEPTION 'Orphan GAME external identifier found';
  END IF;
END $$;

UPDATE games g
SET igdb_id = ids.igdb_id,
    steamgriddb_id = ids.steamgriddb_id
FROM (
  SELECT
    entity_id,
    MAX(external_id) FILTER (WHERE provider = 'IGDB') AS igdb_id,
    MAX(external_id) FILTER (WHERE provider = 'STEAMGRIDDB') AS steamgriddb_id
  FROM external_identifiers
  WHERE entity_type = 'GAME'
  GROUP BY entity_id
) ids
WHERE ids.entity_id = g.id;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM external_identifiers ei
    JOIN games g ON g.id = ei.entity_id
    WHERE ei.entity_type = 'GAME'
      AND (
        (ei.provider = 'IGDB' AND g.igdb_id IS DISTINCT FROM ei.external_id)
        OR (ei.provider = 'STEAMGRIDDB' AND g.steamgriddb_id IS DISTINCT FROM ei.external_id)
      )
  ) THEN
    RAISE EXCEPTION 'GAME external identifier copy validation failed';
  END IF;
END $$;

ALTER TABLE games
ADD CONSTRAINT uq_games_igdb_id UNIQUE (igdb_id),
ADD CONSTRAINT uq_games_steamgriddb_id UNIQUE (steamgriddb_id);

DELETE FROM external_identifiers
WHERE entity_type = 'GAME';

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_provider_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_provider_check
CHECK (provider IN ('SPOTIFY','MUSICBRAINZ','TMDB','IMDB','TVDB'));

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_entity_type_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_entity_type_check
CHECK (entity_type IN ('ARTIST','ALBUM','TRACK','MOVIE','SHOW','EPISODE'));
