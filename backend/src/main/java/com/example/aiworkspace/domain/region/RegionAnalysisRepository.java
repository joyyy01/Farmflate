package com.example.aiworkspace.domain.region;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

public interface RegionAnalysisRepository extends JpaRepository<RegionAnalysisEntity, String> {
    /**
     * Force the scoped idempotency unique index to be evaluated before the
     * caller returns.  A separate transaction lets a losing concurrent request
     * roll back cleanly and then read the committed winner.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    <S extends RegionAnalysisEntity> S saveAndFlush(S entity);

    Optional<RegionAnalysisEntity> findByUserEmailAndIdempotencyKey(String userEmail, String idempotencyKey);
    Optional<RegionAnalysisEntity> findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
            String userEmail, String sigunguCode, String ruleVersion, LocalDateTime since);
    Optional<RegionAnalysisEntity> findByIdAndUserEmail(String id, String userEmail);
    Optional<RegionAnalysisEntity> findFirstByUserEmailAndPurposeOrderByAnalyzedAtDesc(String userEmail, String purpose);
    Optional<RegionAnalysisEntity> findFirstByUserEmailAndPurposeAndReportStatusInOrderByAnalyzedAtDesc(
            String userEmail, String purpose, java.util.Collection<String> reportStatuses);

    Optional<RegionAnalysisEntity> findByAnalysisScopeAndScopeSubjectAndIdempotencyKey(
            String analysisScope, String scopeSubject, String idempotencyKey);

    Optional<RegionAnalysisEntity> findFirstByAnalysisScopeAndScopeSubjectAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
            String analysisScope, String scopeSubject, String sigunguCode, String ruleVersion, LocalDateTime since);

    Optional<RegionAnalysisEntity> findByIdAndAnalysisScope(String id, String analysisScope);
}
