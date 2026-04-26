UPDATE movies
SET credits_synced_at = NULL,
    updated_at = NOW()
WHERE credits_synced_at IS NOT NULL;
