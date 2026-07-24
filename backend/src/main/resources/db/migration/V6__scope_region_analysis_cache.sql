-- Scope reuse and idempotency to either an authenticated owner or the explicit public regional scope.
ALTER TABLE region_analyses
    ADD COLUMN IF NOT EXISTS rule_version VARCHAR(64) NOT NULL DEFAULT 'crop-score-v1';

CREATE UNIQUE INDEX IF NOT EXISTS uq_region_analysis_scope_idempotency
    ON region_analyses (analysis_scope, scope_subject, idempotency_key);

CREATE INDEX IF NOT EXISTS ix_region_analysis_scope_reuse
    ON region_analyses (analysis_scope, scope_subject, sigungu_code, rule_version, analyzed_at DESC);
