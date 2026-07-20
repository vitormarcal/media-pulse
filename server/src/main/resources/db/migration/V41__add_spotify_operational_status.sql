ALTER TABLE spotify_sync_state
ADD COLUMN authorization_status TEXT NOT NULL DEFAULT 'UNKNOWN',
ADD COLUMN last_success_at TIMESTAMPTZ,
ADD COLUMN last_failure_at TIMESTAMPTZ,
ADD COLUMN last_error_code TEXT,
ADD CONSTRAINT chk_spotify_sync_authorization_status
  CHECK (authorization_status IN ('UNKNOWN', 'HEALTHY', 'REAUTHORIZATION_REQUIRED', 'ERROR'));
