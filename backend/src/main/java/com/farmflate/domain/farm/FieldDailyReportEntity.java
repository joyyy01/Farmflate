package com.farmflate.domain.farm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_daily_reports", indexes = {
        @Index(name = "ix_field_daily_reports_owner_generated", columnList = "owner_email, generated_at"),
        @Index(name = "uq_field_daily_report_reason", columnList = "farm_id, report_date, generation_reason", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FieldDailyReportEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "owner_email", nullable = false, length = 255)
    private String ownerEmail;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "generation_reason", nullable = false, length = 32)
    private String generationReason;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
}
