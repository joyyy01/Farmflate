package com.farmflate.service.field;

import com.farmflate.domain.farm.FarmEntity;
import com.farmflate.domain.farm.FarmRepository;
import com.farmflate.domain.farm.FieldActivityLogRepository;
import com.farmflate.domain.farm.FieldDailyReportEntity;
import com.farmflate.domain.farm.FieldDailyReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldDailyReportServiceTest {

    @Mock private FarmRepository farmRepository;
    @Mock private FieldDailyReportRepository dailyReportRepository;
    @Mock private FieldDailyReportStore dailyReportStore;
    @Mock private FieldActivityLogRepository activityLogRepository;
    @Mock private FieldWeatherService fieldWeatherService;
    @Mock private FieldGuidanceRuleEngine ruleEngine;

    @Test
    void continues_with_the_next_field_when_one_daily_report_fails() {
        FarmEntity first = field(1L, "first@example.com");
        FarmEntity second = field(2L, "second@example.com");
        LocalDate reportDate = LocalDate.of(2026, 7, 28);
        FieldDailyReportService service = new FieldDailyReportService(
                farmRepository, dailyReportRepository, dailyReportStore, activityLogRepository,
                fieldWeatherService, ruleEngine, new ObjectMapper(), Clock.system(ZoneOffset.UTC));

        when(farmRepository.findByActiveTrue()).thenReturn(List.of(first, second));
        when(dailyReportStore.findExisting(1L, "first@example.com", reportDate, FieldDailyReportService.GENERATION_REASON))
                .thenThrow(new IllegalStateException("weather unavailable"));
        when(dailyReportStore.findExisting(2L, "second@example.com", reportDate, FieldDailyReportService.GENERATION_REASON))
                .thenReturn(Optional.of(FieldDailyReportEntity.builder().id("report-2").farmId(2L)
                        .ownerEmail("second@example.com").reportDate(reportDate)
                        .generationReason(FieldDailyReportService.GENERATION_REASON)
                        .generatedAt(reportDate.atStartOfDay()).payloadJson("{\"id\":\"report-2\"}").build()));

        service.generateForAllActiveFields(reportDate);

        verify(dailyReportStore).findExisting(eq(2L), eq("second@example.com"), eq(reportDate),
                eq(FieldDailyReportService.GENERATION_REASON));
    }

    private FarmEntity field(Long id, String ownerEmail) {
        return FarmEntity.builder().id(id).userEmail(ownerEmail).fieldName("필지")
                .cropName("감자").active(true).build();
    }
}
