ALTER TABLE movie_titles
DROP CONSTRAINT IF EXISTS movie_titles_source_check;

ALTER TABLE movie_titles
ADD CONSTRAINT movie_titles_source_check
CHECK (source IN ('PLEX', 'MANUAL'));

ALTER TABLE movie_watches
DROP CONSTRAINT IF EXISTS movie_watches_source_check;

ALTER TABLE movie_watches
ADD CONSTRAINT movie_watches_source_check
CHECK (source IN ('PLEX', 'MANUAL'));

CREATE INDEX IF NOT EXISTS idx_ext_ids_entity_provider_external
ON external_identifiers(entity_type, provider, external_id);
