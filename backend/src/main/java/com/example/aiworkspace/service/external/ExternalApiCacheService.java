package com.example.aiworkspace.service.external;

import com.example.aiworkspace.domain.cache.ExternalApiCacheEntity;
import com.example.aiworkspace.domain.cache.ExternalApiCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalApiCacheService {

    private final ExternalApiCacheRepository cacheRepository;

    private static final Map<String, Duration> STALE_MAX_AGE = Map.of(
            "KMA_SHORT_FORECAST", Duration.ofHours(6),
            "KMA_ASOS_30D", Duration.ofHours(48),
            "LEGAL_DISTRICTS", Duration.ofDays(180),
            "RDA_SOIL_CHEMISTRY", Duration.ofDays(365),
            "RDA_SOIL_SUITABILITY", Duration.ofDays(365),
            "RDA_CROP_CODES", Duration.ofDays(365)
    );

    @Transactional(readOnly = true)
    public Optional<ExternalApiCacheEntity> findFresh(String cacheKey, Instant now) {
        return cacheRepository.findByCacheKeyAndExpiresAtAfter(cacheKey, now);
    }

    @Transactional(readOnly = true)
    public Optional<ExternalApiCacheEntity> findLatestSuccessful(String cacheKey) {
        return cacheRepository.findFirstByCacheKeyOrderByLastSuccessAtDesc(cacheKey);
    }

    public boolean isStaleUsable(String cacheKey, ExternalApiCacheEntity entity) {
        String provider = entity.getProvider();
        Duration maxAge = STALE_MAX_AGE.getOrDefault(provider, Duration.ofHours(24));
        Instant cutoff = Instant.now().minus(maxAge);
        return entity.getLastSuccessAt() != null && entity.getLastSuccessAt().isAfter(cutoff);
    }

    @Transactional
    public void saveSuccess(String cacheKey, String provider, String serviceName, String regionCode,
                            String fingerprint, String responseBody, String normalizedJson,
                            Instant providerDataAt, Duration ttl) {
        Instant now = Instant.now();
        ExternalApiCacheEntity entity = cacheRepository.findById(cacheKey)
                .map(existing -> {
                    existing = ExternalApiCacheEntity.builder()
                            .cacheKey(cacheKey)
                            .provider(provider)
                            .serviceName(serviceName)
                            .regionCode(regionCode)
                            .requestFingerprint(fingerprint)
                            .responseBody(responseBody)
                            .normalizedJson(normalizedJson)
                            .providerDataAt(providerDataAt)
                            .retrievedAt(now)
                            .expiresAt(now.plus(ttl))
                            .lastSuccessAt(now)
                            .contentType(existing.getContentType())
                            .createdAt(existing.getCreatedAt())
                            .updatedAt(now)
                            .build();
                    return existing;
                })
                .orElseGet(() -> ExternalApiCacheEntity.builder()
                        .cacheKey(cacheKey)
                        .provider(provider)
                        .serviceName(serviceName)
                        .regionCode(regionCode)
                        .requestFingerprint(fingerprint)
                        .responseBody(responseBody)
                        .normalizedJson(normalizedJson)
                        .providerDataAt(providerDataAt)
                        .retrievedAt(now)
                        .expiresAt(now.plus(ttl))
                        .lastSuccessAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
        cacheRepository.save(entity);
    }
}
