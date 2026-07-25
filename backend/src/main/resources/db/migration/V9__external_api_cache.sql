CREATE TABLE external_api_cache (
    cache_key VARCHAR(500) PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    region_code VARCHAR(50),
    request_fingerprint VARCHAR(500) NOT NULL,
    response_body TEXT NOT NULL,
    normalized_json TEXT,
    provider_data_at TIMESTAMP,
    retrieved_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_success_at TIMESTAMP NOT NULL,
    content_type VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_external_api_cache_provider_region
    ON external_api_cache(provider, service_name, region_code);

CREATE INDEX ix_external_api_cache_expires_at
    ON external_api_cache(expires_at);
