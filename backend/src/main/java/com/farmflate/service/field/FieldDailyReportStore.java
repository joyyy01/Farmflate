package com.farmflate.service.field;

import com.farmflate.domain.farm.FieldDailyReportEntity;
import com.farmflate.domain.farm.FieldDailyReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** Keeps report persistence short; weather and narration must complete before this store is entered. */
@Service
@RequiredArgsConstructor
public class FieldDailyReportStore {

    private final FieldDailyReportRepository dailyReportRepository;

    @Transactional(readOnly = true)
    public Optional<FieldDailyReportEntity> findExisting(Long fieldId, String ownerEmail, LocalDate reportDate, String reason) {
        return dailyReportRepository.findFirstByFarmIdAndOwnerEmailAndReportDateAndGenerationReasonOrderByGeneratedAtDesc(
                fieldId, ownerEmail, reportDate, reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FieldDailyReportEntity save(FieldDailyReportEntity report) {
        return dailyReportRepository.saveAndFlush(report);
    }
}
