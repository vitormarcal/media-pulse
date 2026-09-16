ALTER TABLE person_filmography_syncs
    ADD COLUMN compacted_at TIMESTAMPTZ;

CREATE INDEX idx_person_filmography_syncs_compaction
    ON person_filmography_syncs(synced_at, person_id, media_type)
    WHERE compacted_at IS NULL AND synced_at IS NOT NULL;
