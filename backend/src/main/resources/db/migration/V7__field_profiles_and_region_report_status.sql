-- Keep the legacy farms table as the owner-scoped field-profile table so old
-- rows remain readable while new clients can store the decision linkage.
ALTER TABLE farms
    ADD COLUMN IF NOT EXISTS crop_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS region_analysis_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS location_json TEXT,
    ADD COLUMN IF NOT EXISTS cultivation_method VARCHAR(30),
    ADD COLUMN IF NOT EXISTS cultivation_start_date DATE,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS ix_farms_owner_active
    ON farms (user_email, active, created_at DESC);

CREATE TABLE IF NOT EXISTS field_daily_reports (
    id VARCHAR(36) PRIMARY KEY,
    farm_id BIGINT NOT NULL REFERENCES farms(id),
    owner_email VARCHAR(255) NOT NULL,
    report_date DATE NOT NULL,
    generation_reason VARCHAR(32) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_field_daily_reports_owner_generated
    ON field_daily_reports (owner_email, generated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_field_daily_report_reason
    ON field_daily_reports (farm_id, report_date, generation_reason);

ALTER TABLE region_analyses
    ADD COLUMN IF NOT EXISTS report_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED';
