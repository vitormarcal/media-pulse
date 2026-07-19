DELETE FROM external_identifiers
WHERE provider = 'PLEX';

ALTER TABLE external_identifiers
DROP CONSTRAINT IF EXISTS external_identifiers_provider_check;

ALTER TABLE external_identifiers
ADD CONSTRAINT external_identifiers_provider_check
CHECK (provider IN ('SPOTIFY','MUSICBRAINZ','ISBN_10','ISBN_13','TMDB','IMDB','TVDB','IGDB','STEAMGRIDDB'));
