ALTER TABLE external_identifiers
ADD COLUMN external_entity_type TEXT;

ALTER TABLE external_identifiers
ADD CONSTRAINT ck_external_identifiers_external_entity_type
CHECK (external_entity_type IS NULL OR external_entity_type IN ('ARTIST','RELEASE_GROUP','RELEASE','RECORDING'));

CREATE UNIQUE INDEX uq_external_identifiers_typed_entity
ON external_identifiers(entity_type, entity_id, provider, external_entity_type)
WHERE external_entity_type IS NOT NULL;

COMMENT ON COLUMN external_identifiers.external_entity_type IS
'Provider entity kind. NULL preserves identifiers created before typed MusicBrainz links.';
