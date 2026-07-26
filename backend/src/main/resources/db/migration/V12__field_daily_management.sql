CREATE TABLE field_task_acknowledgements (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    report_date DATE NOT NULL,
    task_key VARCHAR(80) NOT NULL,
    acknowledged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_field_task_ack_farm
        FOREIGN KEY (farm_id) REFERENCES farms(id),
    CONSTRAINT uq_field_task_ack
        UNIQUE (farm_id, owner_email, report_date, task_key)
);

CREATE INDEX ix_field_task_ack_owner_date
    ON field_task_acknowledgements(owner_email, report_date DESC);

CREATE TABLE field_activity_logs (
    id BIGSERIAL PRIMARY KEY,
    farm_id BIGINT NOT NULL,
    owner_email VARCHAR(255) NOT NULL,
    category VARCHAR(40) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    idempotency_key VARCHAR(128),
    logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_field_activity_log_farm
        FOREIGN KEY (farm_id) REFERENCES farms(id),
    CONSTRAINT ck_field_activity_log_category
        CHECK (category IN (
            'WATERING',
            'FERTILIZING',
            'LEAF_CHECK',
            'PEST_CONTROL',
            'OTHER'
        ))
);

CREATE UNIQUE INDEX uq_field_activity_log_idempotency
    ON field_activity_logs(owner_email, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_field_activity_log_farm_time
    ON field_activity_logs(farm_id, logged_at DESC);

CREATE INDEX ix_field_daily_reports_farm_date
    ON field_daily_reports(farm_id, report_date DESC);
