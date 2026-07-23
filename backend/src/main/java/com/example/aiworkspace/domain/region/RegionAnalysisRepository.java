package com.example.aiworkspace.domain.region;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RegionAnalysisRepository extends JpaRepository<RegionAnalysisEntity, String> {
    Optional<RegionAnalysisEntity> findByIdempotencyKey(String idempotencyKey);
    List<RegionAnalysisEntity> findByUserEmailAndSigunguCodeAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
            String userEmail, String sigunguCode, LocalDateTime since);
    Optional<RegionAnalysisEntity> findFirstByUserEmailOrderByAnalyzedAtDesc(String userEmail);
}
