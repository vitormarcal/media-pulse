ALTER TABLE albums ADD COLUMN artist_credits_revision BIGINT NOT NULL DEFAULT 0;

CREATE TABLE album_credit_backfill_batches (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Album IDs deliberately have no FK: the audit must survive album merges/deletion.
CREATE TABLE album_credit_backfill_items (
    batch_id UUID NOT NULL REFERENCES album_credit_backfill_batches(id) ON DELETE CASCADE,
    album_id BIGINT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING','READY','SKIPPED','ERROR','APPLIED','CONFLICT','UNDONE','UNDO_CONFLICT')),
    document JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (batch_id, album_id)
);
