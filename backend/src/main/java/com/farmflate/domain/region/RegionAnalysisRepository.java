package com.farmflate.domain.region;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    List<RegionAnalysisEntity> findByIdInAndUserEmail(Collection<String> ids, String userEmail);

    /**
     * A pending row can always start; a processing row can only be reclaimed
     * after it has stopped updating its progress for the stale threshold.
     * The conditional update is the single-worker claim boundary shared by
     * event dispatch and recovery dispatch.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update RegionAnalysisEntity analysis
                set analysis.reportStatus = 'PROCESSING',
                    analysis.currentStep = 'REGION',
                    analysis.completedSteps = 'REGION',
                    analysis.executionToken = :executionToken,
                    analysis.errorCode = null,
                   analysis.errorMessage = null,
                   analysis.retryable = null,
                   analysis.updatedAt = CURRENT_TIMESTAMP
             where analysis.id = :analysisId
               and (
                   analysis.reportStatus = 'PENDING'
                   or (analysis.reportStatus = 'PROCESSING' and analysis.updatedAt < :staleBefore)
               )
             """)
    int claimForExecution(@Param("analysisId") String analysisId, @Param("staleBefore") LocalDateTime staleBefore,
                          @Param("executionToken") String executionToken);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update RegionAnalysisEntity analysis
               set analysis.currentStep = :currentStep,
                   analysis.completedSteps = :completedSteps,
                   analysis.updatedAt = CURRENT_TIMESTAMP
             where analysis.id = :analysisId
               and analysis.reportStatus = 'PROCESSING'
               and analysis.executionToken = :executionToken
            """)
    int updateProgressIfOwned(@Param("analysisId") String analysisId,
                              @Param("executionToken") String executionToken,
                              @Param("currentStep") String currentStep,
                              @Param("completedSteps") String completedSteps);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update RegionAnalysisEntity analysis
               set analysis.reportStatus = :reportStatus,
                   analysis.currentStep = 'REPORT',
                   analysis.completedSteps = :completedSteps,
                   analysis.regionScore = :regionScore,
                   analysis.grade = :grade,
                   analysis.summary = :summary,
                   analysis.confidenceGrade = :confidenceGrade,
                   analysis.confidenceScore = :confidenceScore,
                   analysis.confidenceMessage = :confidenceMessage,
                   analysis.payloadJson = :payloadJson,
                   analysis.analyzedAt = :analyzedAt,
                   analysis.errorCode = null,
                   analysis.errorMessage = null,
                   analysis.retryable = false,
                   analysis.updatedAt = CURRENT_TIMESTAMP
             where analysis.id = :analysisId
               and analysis.reportStatus = 'PROCESSING'
               and analysis.executionToken = :executionToken
            """)
    int completeIfOwned(@Param("analysisId") String analysisId,
                        @Param("executionToken") String executionToken,
                        @Param("reportStatus") String reportStatus,
                        @Param("regionScore") Integer regionScore,
                        @Param("grade") String grade,
                        @Param("summary") String summary,
                        @Param("confidenceGrade") String confidenceGrade,
                        @Param("confidenceScore") Integer confidenceScore,
                        @Param("confidenceMessage") String confidenceMessage,
                        @Param("payloadJson") String payloadJson,
                        @Param("analyzedAt") LocalDateTime analyzedAt,
                        @Param("completedSteps") String completedSteps);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            update RegionAnalysisEntity analysis
               set analysis.reportStatus = 'FAILED',
                   analysis.currentStep = :currentStep,
                   analysis.completedSteps = :completedSteps,
                   analysis.errorCode = :errorCode,
                   analysis.errorMessage = :errorMessage,
                   analysis.retryable = :retryable,
                   analysis.updatedAt = CURRENT_TIMESTAMP
             where analysis.id = :analysisId
               and analysis.reportStatus = 'PROCESSING'
               and analysis.executionToken = :executionToken
            """)
    int failIfOwned(@Param("analysisId") String analysisId,
                    @Param("executionToken") String executionToken,
                    @Param("currentStep") String currentStep,
                    @Param("completedSteps") String completedSteps,
                    @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage,
                    @Param("retryable") boolean retryable);

    @Query("""
            select analysis.id from RegionAnalysisEntity analysis
             where analysis.reportStatus = 'PENDING'
                or (analysis.reportStatus = 'PROCESSING' and analysis.updatedAt < :staleBefore)
             order by analysis.updatedAt asc
            """)
    List<String> findRecoveryCandidateIds(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);
}
