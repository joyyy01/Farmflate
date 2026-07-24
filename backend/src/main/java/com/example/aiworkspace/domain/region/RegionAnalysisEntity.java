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

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    @Column(name = "data_mode", length = 20)
    private String dataMode; // LIVE or REPLAY

    @Column(name = "report_status", length = 20)
    private String reportStatus; // COMPLETED or PARTIAL
}
