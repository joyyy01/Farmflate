package com.farmflate.domain.farm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_task_acknowledgements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FieldTaskAcknowledgementEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farm_id", nullable = false)
    private Long farmId;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "task_key", nullable = false, length = 80)
    private String taskKey;

    @Column(name = "acknowledged_at", nullable = false)
    private LocalDateTime acknowledgedAt;
}
