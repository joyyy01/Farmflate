package com.example.aiworkspace.domain.farm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FieldDailyReportRepository extends JpaRepository<FieldDailyReportEntity, String> {
    Optional<FieldDailyReportEntity> findFirstByFarmIdAndOwnerEmailOrderByGeneratedAtDesc(Long farmId, String ownerEmail);

    Optional<FieldDailyReportEntity> findFirstByFarmIdAndOwnerEmailAndGenerationReasonOrderByGeneratedAtDesc(
            Long farmId, String ownerEmail, String generationReason);

    Optional<FieldDailyReportEntity> findFirstByFarmIdAndOwnerEmailAndReportDateAndGenerationReasonOrderByGeneratedAtDesc(
            Long farmId, String ownerEmail, LocalDate reportDate, String generationReason);

    List<FieldDailyReportEntity> findByFarmIdAndOwnerEmailAndReportDateBetweenAndGenerationReasonOrderByReportDateDesc(
            Long farmId, String ownerEmail, LocalDate from, LocalDate to, String generationReason);
}
