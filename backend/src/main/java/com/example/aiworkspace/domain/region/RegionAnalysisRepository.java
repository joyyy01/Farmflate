package com.example.aiworkspace.domain.region;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RegionAnalysisRepository extends JpaRepository<RegionAnalysisEntity, String> {
    Optional<RegionAnalysisEntity> findByUserEmailAndIdempotencyKey(String userEmail, String idempotencyKey);
    Optional<RegionAnalysisEntity> findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
            String userEmail, String sigunguCode, String ruleVersion, LocalDateTime since);
    Optional<RegionAnalysisEntity> findByIdAndUserEmail(String id, String userEmail);
    Optional<RegionAnalysisEntity> findFirstByUserEmailOrderByAnalyzedAtDesc(String userEmail);

    Optional<RegionAnalysisEntity> findByAnalysisScopeAndScopeSubjectAndIdempotencyKey(
            String analysisScope, String scopeSubject, String idempotencyKey);

    Optional<RegionAnalysisEntity> findFirstByAnalysisScopeAndScopeSubjectAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
            String analysisScope, String scopeSubject, String sigunguCode, String ruleVersion, LocalDateTime since);

    Optional<RegionAnalysisEntity> findByIdAndAnalysisScope(String id, String analysisScope);
}
