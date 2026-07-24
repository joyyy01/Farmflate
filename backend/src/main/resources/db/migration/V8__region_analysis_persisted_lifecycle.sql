-- A region report is now created before provider work starts.  These columns
-- make every actual provider-stage transition observable through /status.
ALTER TABLE region_analyses
    ADD COLUMN IF NOT EXISTS location_request_json TEXT,
    ADD COLUMN IF NOT EXISTS current_step VARCHAR(32),
    ADD COLUMN IF NOT EXISTS completed_steps TEXT,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS retryable BOOLEAN;
