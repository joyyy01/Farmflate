-- A reclaimed PROCESSING row receives a new fencing token.  Every worker
-- mutation is conditioned on that token so a stale worker cannot overwrite
-- progress, failure, or the completed report produced by its replacement.
ALTER TABLE region_analyses
    ADD COLUMN IF NOT EXISTS execution_token VARCHAR(36);
