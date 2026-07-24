CREATE TABLE IF NOT EXISTS farms (
    id BIGSERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    crop_name VARCHAR(50) NOT NULL,
    days_planted INT DEFAULT 1,
    stage VARCHAR(50) DEFAULT '생장 초기',
    status_badge VARCHAR(50) DEFAULT '물주기 필요',
    status_badge_color VARCHAR(20) DEFAULT 'yellow',
    today_task TEXT,
    report_time VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_farms_user_email ON farms(user_email);
