ALTER TABLE artworks
    ADD COLUMN movements jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN movements_revision bigint NOT NULL DEFAULT 1,
    ADD COLUMN movements_sync_pending boolean NOT NULL DEFAULT false,
    ADD COLUMN movements_sync_message text;
UPDATE artworks SET movements = jsonb_build_array(movement) WHERE movement IS NOT NULL;
ALTER TABLE artworks DROP COLUMN movement;
ALTER TABLE artworks ADD CONSTRAINT artwork_movements_array CHECK (jsonb_typeof(movements) = 'array');
