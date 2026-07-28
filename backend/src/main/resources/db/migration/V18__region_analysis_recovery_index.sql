-- Recovery scans queued rows and provider jobs whose progress heartbeat stopped.
CREATE INDEX IF NOT EXISTS ix_region_analysis_recovery
    ON region_analyses (report_status, updated_at);
