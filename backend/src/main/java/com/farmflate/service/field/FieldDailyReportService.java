package com.farmflate.service.field;

import com.farmflate.domain.farm.FarmEntity;
import com.farmflate.domain.farm.FarmRepository;
import com.farmflate.domain.farm.FieldActivityLogEntity;
import com.farmflate.domain.farm.FieldActivityLogRepository;
import com.farmflate.domain.farm.FieldDailyReportEntity;
import com.farmflate.domain.farm.FieldDailyReportRepository;
import com.farmflate.dto.field.FieldAlertDto;
import com.farmflate.dto.field.FieldDailyReportDto;
import com.farmflate.dto.field.FieldDailyStatus;
import com.farmflate.dto.field.FieldDashboardResponseDto;
import com.farmflate.dto.field.FieldTaskDto;
import com.farmflate.dto.field.FieldWeatherDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Owns the one field/date/DAILY_0630 report row: reuses it if present,
 * generates it (weather -> rule engine -> narrator, with narrator failure
 * falling back to rule-engine text) if absent, and survives a concurrent
 * generation race via the DB unique constraint instead of a distributed lock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldDailyReportService {

    public static final String GENERATION_REASON = "DAILY_0630";

    private final FarmRepository farmRepository;
    private final FieldDailyReportRepository dailyReportRepository;
    private final FieldDailyReportStore dailyReportStore;
    private final FieldActivityLogRepository activityLogRepository;
    private final FieldWeatherService fieldWeatherService;
    private final FieldGuidanceRuleEngine ruleEngine;
    private final FieldGuidanceNarrator narrator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FieldDailyReportDto getOrCreate(FarmEntity field, LocalDate reportDate) {
        return dailyReportStore.findExisting(field.getId(), field.getUserEmail(), reportDate, GENERATION_REASON)
                .map(this::read)
                .orElseGet(() -> createDaily(field, reportDate));
    }

    public FieldDailyReportDto getOrCreate(Long fieldId, LocalDate reportDate) {
        FarmEntity field = farmRepository.findById(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("field not found: " + fieldId));
        return getOrCreate(field, reportDate);
    }

    public void generateForAllActiveFields(LocalDate date) {
        LocalDate reportDate = date == null ? LocalDate.now(clock) : date;
        for (FarmEntity field : farmRepository.findByActiveTrue()) {
            try {
                getOrCreate(field, reportDate);
            } catch (Exception exception) {
                log.warn("field_daily_report.failed fieldId={} reportDate={} error={}",
                        field.getId(), reportDate, exception.getMessage());
            }
        }
    }

    private FieldDailyReportDto createDaily(FarmEntity field, LocalDate reportDate) {
        try {
            return saveGenerated(field, reportDate);
        } catch (DataIntegrityViolationException race) {
            return dailyReportStore.findExisting(field.getId(), field.getUserEmail(), reportDate, GENERATION_REASON)
                    .map(this::read)
                    .orElseThrow(() -> race);
        }
    }

    private FieldDailyReportDto saveGenerated(FarmEntity field, LocalDate reportDate) {
        long startedAt = System.currentTimeMillis();
        FieldWeatherDto weather = fieldWeatherService.load(field);
        List<FieldActivityLogEntity> recentLogs = activityLogRepository
                .findByFarmIdAndOwnerEmailAndLoggedAtBetweenOrderByLoggedAtDesc(
                        field.getId(), field.getUserEmail(),
                        reportDate.minusDays(2).atStartOfDay(), reportDate.atTime(23, 59, 59));

        FieldGuidanceRuleEngine.FieldGuidanceResult validated = ruleEngine.evaluate(
                new FieldGuidanceRuleEngine.FieldGuidanceInput(
                        field.getCropCode(), field.getCropName(), field.getStage(), weather, recentLogs));

        String headline = validated.headline();
        String headlineDescription = validated.headlineDescription();
        List<FieldTaskDto> tasks = validated.tasks();
        String reasoningSummary = validated.reasoningPoints().isEmpty() ? headlineDescription : validated.reasoningPoints().get(0);
        boolean llmFallback = false;

        try {
            FieldGuidanceNarrator.NarratedGuidance narrated = narrator.narrate(
                    field.getCropCode(), field.getCropName(), field.getStage(), reportDate, weather, validated);
            headline = narrated.headline();
            headlineDescription = narrated.headlineDescription();
            tasks = narrated.tasks();
            reasoningSummary = narrated.reasoningSummary();
        } catch (Exception exception) {
            llmFallback = true;
            log.info("field_daily_report.llm_fallback fieldId={} reportDate={} reason={}",
                    field.getId(), reportDate, exception.getMessage());
        }

        LocalDateTime generatedAt = LocalDateTime.now(clock);
        FieldDailyReportDto dto = FieldDailyReportDto.builder()
                .id(UUID.randomUUID().toString())
                .fieldId(String.valueOf(field.getId()))
                .reportDate(reportDate.toString())
                .generatedAt(generatedAt.format(DateTimeFormatter.ISO_DATE_TIME))
                .generationReason(GENERATION_REASON)
                .cropCode(field.getCropCode())
                .cropName(field.getCropName())
                .stage(field.getStage())
                .status(validated.status())
                .headline(headline)
                .headlineDescription(headlineDescription)
                .weather(weather)
                .tasks(tasks)
                .alerts(validated.alerts())
                .reasoning(FieldDashboardResponseDto.ReasoningDto.builder()
                        .summary(reasoningSummary)
                        .points(validated.reasoningPoints())
                        .build())
                .build();

        dailyReportStore.save(FieldDailyReportEntity.builder()
                .id(dto.getId()).farmId(field.getId()).ownerEmail(field.getUserEmail())
                .reportDate(reportDate).generationReason(GENERATION_REASON)
                .generatedAt(generatedAt).payloadJson(write(dto)).build());

        log.info("field_daily_report.generated fieldId={} reportDate={} status={} taskCount={} alertCount={} durationMs={} llmFallback={}",
                field.getId(), reportDate, validated.status(), tasks.size(), validated.alerts().size(),
                System.currentTimeMillis() - startedAt, llmFallback);
        return dto;
    }

    public List<FieldDailyReportEntity> findRecent(Long farmId, String ownerEmail, LocalDate from, LocalDate to) {
        return dailyReportRepository.findByFarmIdAndOwnerEmailAndReportDateBetweenAndGenerationReasonOrderByReportDateDesc(
                farmId, ownerEmail, from, to, GENERATION_REASON);
    }

    private FieldDailyReportDto read(FieldDailyReportEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayloadJson(), FieldDailyReportDto.class);
        } catch (Exception exception) {
            throw new IllegalStateException("field_daily_report payload corrupt: " + entity.getId(), exception);
        }
    }

    public FieldDailyReportDto readPayload(FieldDailyReportEntity entity) {
        return read(entity);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize field daily report", exception);
        }
    }
}
