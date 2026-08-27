ALTER TABLE artists
  ADD COLUMN IF NOT EXISTS profile_image_url TEXT,
  ADD COLUMN IF NOT EXISTS wikidata_entity_id TEXT,
  ADD COLUMN IF NOT EXISTS wikimedia_file_name TEXT,
  ADD COLUMN IF NOT EXISTS wikimedia_original_url TEXT,
  ADD COLUMN IF NOT EXISTS wikimedia_description_url TEXT,
  ADD COLUMN IF NOT EXISTS wikimedia_author TEXT,
  ADD COLUMN IF NOT EXISTS wikimedia_license TEXT,
  ADD COLUMN IF NOT EXISTS wikimedia_license_url TEXT,
  ADD COLUMN IF NOT EXISTS wikimedia_imported_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS wikimedia_attempted_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS wikimedia_sync_error TEXT;
