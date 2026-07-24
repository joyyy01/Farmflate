package com.example.aiworkspace.domain.region;

import com.example.aiworkspace.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "region_analyses",
    indexes = {
        @Index(name = "uq_region_analysis_scope_idempotency", columnList = "analysis_scope, scope_subject, idempotency_key", unique = true),
        @Index(name = "ix_region_analysis_scope_reuse", columnList = "analysis_scope, scope_subject, sigungu_code, rule_version, analyzed_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RegionAnalysisEntity extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id; // UUID

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "rule_version", nullable = false, length = 64)
    private String ruleVersion;

    /** Present only for a private owner analysis; public regional snapshots carry no user identity. */
    @Column(name = "user_email", length = 255)
    private String userEmail;

    /** OWNER or PUBLIC.  This is an explicit access/cache scope, never a synthetic user account. */
    @Column(name = "analysis_scope", nullable = false, length = 20)
    private String analysisScope;

    /** Owner e-mail for OWNER, fixed non-personal scope subject for PUBLIC. */
    @Column(name = "scope_subject", nullable = false, length = 255)
    private String scopeSubject;

    @Column(name = "sido_code", nullable = false, length = 20)
    private String sidoCode;

    @Column(name = "sido_name", nullable = false, length = 100)
    private String sidoName;

    @Column(name = "sigungu_code", nullable = false, length = 20)
    private String sigunguCode;

    @Column(name = "sigungu_name", nullable = false, length = 100)
    private String sigunguName;

    @Column(name = "region_score")
    private Integer regionScore;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "confidence_grade", length = 50)
    private String confidenceGrade;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "confidence_message", columnDefinition = "TEXT")
    private String confidenceMessage;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson; // Complete JSON payload snapshot

    /** Original optional precision-location input, retained so an async job can resolve it after POST returns. */
    @Column(name = "location_request_json", columnDefinition = "TEXT")
    private String locationRequestJson;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "data_mode", length = 20)
    private String dataMode; // LIVE or REPLAY

    @Column(name = "report_status", length = 20)
    private String reportStatus; // PENDING, PROCESSING, COMPLETED, PARTIAL, or FAILED

    @Column(name = "current_step", length = 32)
    private String currentStep;

    /** Stable comma-separated stage codes; user-facing labels are supplied by the status DTO. */
    @Column(name = "completed_steps", columnDefinition = "TEXT")
    private String completedSteps;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retryable")
    private Boolean retryable;

    public void markProcessing(String step, String completedStepCodes) {
        this.reportStatus = "PROCESSING";
        this.currentStep = step;
        this.completedSteps = completedStepCodes;
        this.errorCode = null;
        this.errorMessage = null;
        this.retryable = null;
    }

    public void markCompleted(
            String status,
            Integer resultRegionScore,
            String resultGrade,
            String resultSummary,
            String resultConfidenceGrade,
            Integer resultConfidenceScore,
            String resultConfidenceMessage,
            String resultPayloadJson,
            LocalDateTime resultAnalyzedAt,
            String completedStepCodes) {
        this.reportStatus = status;
        this.currentStep = "REPORT";
        this.completedSteps = completedStepCodes;
        this.regionScore = resultRegionScore;
        this.grade = resultGrade;
        this.summary = resultSummary;
        this.confidenceGrade = resultConfidenceGrade;
        this.confidenceScore = resultConfidenceScore;
        this.confidenceMessage = resultConfidenceMessage;
        this.payloadJson = resultPayloadJson;
        this.analyzedAt = resultAnalyzedAt;
        this.errorCode = null;
        this.errorMessage = null;
        this.retryable = false;
    }

    public void markFailed(String step, String completedStepCodes, String failureCode, String failureMessage,
                           boolean canRetry) {
        this.reportStatus = "FAILED";
        this.currentStep = step;
        this.completedSteps = completedStepCodes;
        this.errorCode = failureCode;
        this.errorMessage = failureMessage;
        this.retryable = canRetry;
    }
}
