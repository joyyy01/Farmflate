package com.example.aiworkspace.domain.farm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FieldTaskAcknowledgementRepository extends JpaRepository<FieldTaskAcknowledgementEntity, Long> {
    List<FieldTaskAcknowledgementEntity> findByFarmIdAndOwnerEmailAndReportDate(Long farmId, String ownerEmail, LocalDate reportDate);

    Optional<FieldTaskAcknowledgementEntity> findByFarmIdAndOwnerEmailAndReportDateAndTaskKey(
            Long farmId, String ownerEmail, LocalDate reportDate, String taskKey);
}
