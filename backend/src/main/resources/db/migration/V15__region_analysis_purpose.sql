-- Distinguishes a user's primary/representative region analysis (drives Home,
-- My Page, and the community author-region tag) from an analysis created only
-- to back a specific field's crop-suitability scoring. Without this, picking
-- a region for a field silently became the user's "latest" analysis and
-- overwrote what Home/My Page displayed.
ALTER TABLE region_analyses
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(20) NOT NULL DEFAULT 'PRIMARY';

CREATE INDEX IF NOT EXISTS ix_region_analysis_user_purpose
    ON region_analyses (user_email, purpose, analyzed_at DESC);
