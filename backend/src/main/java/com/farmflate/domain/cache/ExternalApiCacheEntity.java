package com.farmflate.domain.cache;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "external_api_cache")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalApiCacheEntity {

    @Id
    @Column(name = "cache_key", length = 500)
    private String cacheKey;

    @Column(name = "provider", length = 50, nullable = false)
    private String provider;

    @Column(name = "service_name", length = 100, nullable = false)
    private String serviceName;

    @Column(name = "region_code", length = 50)
    private String regionCode;

    @Column(name = "request_fingerprint", length = 500, nullable = false)
    private String requestFingerprint;

    @Column(name = "response_body", columnDefinition = "TEXT", nullable = false)
    private String responseBody;

    @Column(name = "normalized_json", columnDefinition = "TEXT")
    private String normalizedJson;

    @Column(name = "provider_data_at")
    private Instant providerDataAt;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_success_at", nullable = false)
    private Instant lastSuccessAt;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isFresh(Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }
}
