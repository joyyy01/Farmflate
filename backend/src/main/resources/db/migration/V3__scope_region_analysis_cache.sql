-- Scope reuse and idempotency to the owning user and scoring-rule version.
ALTER TABLE region_analyses
    ADD COLUMN IF NOT EXISTS rule_version VARCHAR(64) NOT NULL DEFAULT 'crop-score-v1';

-- V2 already supplies this column in new installs; retain compatibility with older databases.
ALTER TABLE region_analyses
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

ALTER TABLE region_analyses
    ALTER COLUMN idempotency_key TYPE VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_region_analysis_owner_idempotency
    ON region_analyses (user_email, idempotency_key);

CREATE INDEX IF NOT EXISTS ix_region_analysis_reuse
    ON region_analyses (user_email, sigungu_code, rule_version, analyzed_at DESC);
