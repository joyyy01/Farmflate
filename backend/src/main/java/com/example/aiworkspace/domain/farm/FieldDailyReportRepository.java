package com.example.aiworkspace.domain.farm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FieldDailyReportRepository extends JpaRepository<FieldDailyReportEntity, String> {
    boolean existsByFarmIdAndReportDateAndGenerationReason(Long farmId, LocalDate reportDate, String generationReason);

    Optional<FieldDailyReportEntity> findFirstByFarmIdAndOwnerEmailOrderByGeneratedAtDesc(Long farmId, String ownerEmail);
}
