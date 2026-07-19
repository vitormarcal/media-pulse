DELETE FROM external_identifiers
WHERE entity_type = 'BOOK_EDITION';

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_provider_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_provider_check
CHECK (provider IN ('SPOTIFY','MUSICBRAINZ','TMDB','IMDB','TVDB','IGDB','STEAMGRIDDB'));

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_entity_type_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_entity_type_check
CHECK (entity_type IN ('ARTIST','ALBUM','TRACK','BOOK','MOVIE','SHOW','EPISODE','GAME'));
