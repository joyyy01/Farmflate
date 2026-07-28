package com.farmflate.domain.farm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("""
            select report from FieldDailyReportEntity report
            where report.ownerEmail = :ownerEmail
              and report.farmId in :farmIds
              and report.generatedAt = (
                  select max(candidate.generatedAt) from FieldDailyReportEntity candidate
                  where candidate.ownerEmail = :ownerEmail
                    and candidate.farmId = report.farmId
              )
            """)
    List<FieldDailyReportEntity> findLatestByFarmIdsAndOwnerEmail(
            @Param("farmIds") List<Long> farmIds, @Param("ownerEmail") String ownerEmail);

    @Query("""
            select report from FieldDailyReportEntity report
            where report.ownerEmail = :ownerEmail
              and report.farmId in :farmIds
              and report.generationReason = :generationReason
              and report.generatedAt = (
                  select max(candidate.generatedAt) from FieldDailyReportEntity candidate
                  where candidate.ownerEmail = :ownerEmail
                    and candidate.farmId = report.farmId
                    and candidate.generationReason = :generationReason
              )
            """)
    List<FieldDailyReportEntity> findLatestByFarmIdsAndOwnerEmailAndGenerationReason(
            @Param("farmIds") List<Long> farmIds,
            @Param("ownerEmail") String ownerEmail,
            @Param("generationReason") String generationReason);
}
