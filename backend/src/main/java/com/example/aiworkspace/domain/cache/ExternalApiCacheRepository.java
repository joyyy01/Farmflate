package com.example.aiworkspace.domain.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ExternalApiCacheRepository extends JpaRepository<ExternalApiCacheEntity, String> {

    Optional<ExternalApiCacheEntity> findByCacheKeyAndExpiresAtAfter(String cacheKey, Instant now);

    Optional<ExternalApiCacheEntity> findFirstByCacheKeyOrderByLastSuccessAtDesc(String cacheKey);
}
