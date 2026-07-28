package com.farmflate.service.field;

import com.farmflate.domain.farm.FarmEntity;
import com.farmflate.domain.farm.FarmRepository;
import com.farmflate.domain.farm.FieldActivityLogEntity;
import com.farmflate.domain.farm.FieldActivityLogRepository;
import com.farmflate.domain.farm.FieldDailyReportEntity;
import com.farmflate.domain.farm.FieldDailyReportRepository;
import com.farmflate.domain.farm.FieldTaskAcknowledgementEntity;
import com.farmflate.domain.farm.FieldTaskAcknowledgementRepository;
import com.farmflate.domain.region.RegionAnalysisEntity;
import com.farmflate.domain.region.RegionAnalysisRepository;
import com.farmflate.dto.field.CreateFieldLogRequestDto;
import com.farmflate.dto.field.FieldActivityLogDto;
import com.farmflate.dto.field.FieldAlertDto;
import com.farmflate.dto.field.FieldDailyReportDto;
import com.farmflate.dto.field.FieldDailyStatus;
import com.farmflate.dto.field.FieldDashboardResponseDto;
import com.farmflate.dto.field.FieldHistoryItemDto;
import com.farmflate.dto.field.FieldLogCategory;
import com.farmflate.dto.field.FieldTaskDto;
import com.farmflate.dto.field.FieldWeatherDto;
import com.farmflate.dto.field.FieldWeatherStatus;
import com.farmflate.dto.field.TaskAcknowledgementResponseDto;
import com.farmflate.dto.region.RegionReportResponseDto;
import com.farmflate.service.field.FieldService.FieldException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FieldDashboardService {

    private static final Map<FieldDailyStatus, String> STATUS_LABELS = Map.of(
            FieldDailyStatus.STABLE, "안정",
            FieldDailyStatus.CAUTION, "주의",
            FieldDailyStatus.DANGER, "위험",
            FieldDailyStatus.NEEDS_CHECK, "확인 필요");

    private static final Map<FieldLogCategory, String> CATEGORY_LABELS = Map.of(
            FieldLogCategory.WATERING, "물주기",
            FieldLogCategory.FERTILIZING, "비료",
            FieldLogCategory.LEAF_CHECK, "잎 상태 확인",
            FieldLogCategory.PEST_CONTROL, "병해충 방제",
            FieldLogCategory.OTHER, "기타");

    private final FarmRepository farmRepository;
    private final FieldDailyReportService dailyReportService;
    private final FieldDailyReportRepository dailyReportRepository;
    private final FieldTaskAcknowledgementRepository ackRepository;
    private final FieldActivityLogRepository logRepository;
    private final RegionAnalysisRepository regionAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FieldDashboardResponseDto getDashboard(String ownerEmail, Long fieldId, LocalDate requestedDate) {
        FarmEntity field = requireField(ownerEmail, fieldId);
        LocalDate today = LocalDate.now(clock);
        LocalDate selectedDate = requestedDate == null ? today : requestedDate;
        if (selectedDate.isAfter(today)) throw FieldException.futureDateNotAllowed();
        if (field.getCultivationStartDate() != null && selectedDate.isBefore(field.getCultivationStartDate())) {
            throw FieldException.reportBeforeCultivation();
        }

        boolean historical = !selectedDate.isEqual(today);
        FieldDailyReportDto reportDto;
        String reportId;
        LocalDateTime generatedAt;
        if (historical) {
            FieldDailyReportEntity entity = dailyReportRepository
                    .findFirstByFarmIdAndOwnerEmailAndReportDateAndGenerationReasonOrderByGeneratedAtDesc(
                            fieldId, ownerEmail, selectedDate, FieldDailyReportService.GENERATION_REASON)
                    .orElseThrow(FieldException::reportNotAvailable);
            reportDto = dailyReportService.readPayload(entity);
            reportId = entity.getId();
            generatedAt = entity.getGeneratedAt();
        } else {
            reportDto = dailyReportService.getOrCreate(field, selectedDate);
            reportId = reportDto.getId();
            generatedAt = LocalDateTime.parse(reportDto.getGeneratedAt(), DateTimeFormatter.ISO_DATE_TIME);
        }

        List<FieldTaskAcknowledgementEntity> acks = ackRepository.findByFarmIdAndOwnerEmailAndReportDate(fieldId, ownerEmail, selectedDate);
        Set<String> ackedKeys = acks.stream().map(FieldTaskAcknowledgementEntity::getTaskKey).collect(Collectors.toSet());
        List<FieldTaskDto> allTasks = reportDto.getTasks() == null ? List.of() : reportDto.getTasks();
        int taskCountBeforeAck = allTasks.size();
        List<FieldTaskDto> remainingTasks = allTasks.stream()
                .filter(task -> !ackedKeys.contains(task.getKey()))
                .toList();

        List<FieldActivityLogEntity> logsForSelectedDate = logRepository
                .findByFarmIdAndOwnerEmailAndLoggedAtBetweenOrderByLoggedAtDesc(
                        fieldId, ownerEmail, selectedDate.atStartOfDay(), selectedDate.atTime(23, 59, 59));

        String regionName = resolveRegionName(field, ownerEmail);
        int cultivationDay = cultivationDay(field.getCultivationStartDate(), selectedDate);

        FieldDashboardResponseDto.FieldSummaryDto fieldSummary = FieldDashboardResponseDto.FieldSummaryDto.builder()
                .id(String.valueOf(field.getId())).fieldName(field.getFieldName()).cropCode(field.getCropCode())
                .cropName(field.getCropName()).regionName(regionName)
                .cultivationStartDate(field.getCultivationStartDate() == null ? null : field.getCultivationStartDate().toString())
                .cultivationDay(cultivationDay).stage(field.getStage()).build();

        FieldDailyStatus effectiveStatus = effectiveStatus(reportDto.getStatus(), reportDto.getAlerts());
        Integer statusScore = computeStatusScore(effectiveStatus, reportDto.getAlerts());
        FieldDashboardResponseDto.ReportSummaryDto reportSummary = FieldDashboardResponseDto.ReportSummaryDto.builder()
                .id(reportId).reportDate(selectedDate.toString())
                .generatedAt(generatedAt.format(DateTimeFormatter.ISO_DATE_TIME))
                .generationReason(FieldDailyReportService.GENERATION_REASON)
                .status(effectiveStatus).headline(reportDto.getHeadline()).headlineDescription(reportDto.getHeadlineDescription())
                .historical(historical).taskCountBeforeAcknowledgement(taskCountBeforeAck)
                .statusScore(statusScore).statusScoreZone(statusScoreZone(statusScore)).build();

        return FieldDashboardResponseDto.builder()
                .field(fieldSummary).report(reportSummary).weather(reportDto.getWeather())
                .soil(resolveSoilInfo(field, ownerEmail))
                .tasks(remainingTasks)
                .alerts(reportDto.getAlerts() == null ? List.of() : reportDto.getAlerts())
                .reasoning(reportDto.getReasoning())
                .todayLogs(logsForSelectedDate.stream().map(this::toLogDto).toList())
                .history(buildHistory(field, ownerEmail, today))
                .build();
    }

    @Transactional
    public TaskAcknowledgementResponseDto acknowledgeTask(String ownerEmail, Long fieldId, LocalDate reportDate, String taskKey) {
        requireField(ownerEmail, fieldId);
        FieldDailyReportEntity entity = dailyReportRepository
                .findFirstByFarmIdAndOwnerEmailAndReportDateAndGenerationReasonOrderByGeneratedAtDesc(
                        fieldId, ownerEmail, reportDate, FieldDailyReportService.GENERATION_REASON)
                .orElseThrow(FieldException::reportNotAvailable);
        FieldDailyReportDto dto = dailyReportService.readPayload(entity);
        boolean exists = dto.getTasks() != null && dto.getTasks().stream().anyMatch(task -> task.getKey().equals(taskKey));
        if (!exists) throw FieldException.taskNotFound();

        FieldTaskAcknowledgementEntity ack = ackRepository
                .findByFarmIdAndOwnerEmailAndReportDateAndTaskKey(fieldId, ownerEmail, reportDate, taskKey)
                .orElseGet(() -> saveAcknowledgement(fieldId, ownerEmail, reportDate, taskKey));

        return TaskAcknowledgementResponseDto.builder()
                .taskKey(taskKey).acknowledged(true)
                .acknowledgedAt(ack.getAcknowledgedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private FieldTaskAcknowledgementEntity saveAcknowledgement(Long fieldId, String ownerEmail, LocalDate reportDate, String taskKey) {
        try {
            return ackRepository.saveAndFlush(FieldTaskAcknowledgementEntity.builder()
                    .farmId(fieldId).ownerEmail(ownerEmail).reportDate(reportDate).taskKey(taskKey)
                    .acknowledgedAt(LocalDateTime.now(clock)).build());
        } catch (DataIntegrityViolationException race) {
            return ackRepository.findByFarmIdAndOwnerEmailAndReportDateAndTaskKey(fieldId, ownerEmail, reportDate, taskKey)
                    .orElseThrow(() -> race);
        }
    }

    @Transactional
    public FieldActivityLogDto createLog(String ownerEmail, Long fieldId, String idempotencyKey, CreateFieldLogRequestDto request) {
        requireField(ownerEmail, fieldId);

        FieldLogCategory category;
        try {
            category = FieldLogCategory.valueOf(request.getCategory().trim().toUpperCase());
        } catch (Exception exception) {
            throw FieldException.invalidLog("올바르지 않은 기록 카테고리입니다.");
        }
        String note = request.getNote() == null ? "" : request.getNote();
        if (note.length() > 500) throw FieldException.invalidLog("메모는 500자를 넘을 수 없습니다.");

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<FieldActivityLogEntity> existing = logRepository.findByOwnerEmailAndIdempotencyKey(ownerEmail, idempotencyKey);
            if (existing.isPresent()) return toLogDto(existing.get());
        }

        FieldActivityLogEntity entity = FieldActivityLogEntity.builder()
                .farmId(fieldId).ownerEmail(ownerEmail).category(category.name()).note(note)
                .idempotencyKey(idempotencyKey).loggedAt(LocalDateTime.now(clock)).build();
        try {
            entity = logRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException race) {
            entity = logRepository.findByOwnerEmailAndIdempotencyKey(ownerEmail, idempotencyKey).orElseThrow(() -> race);
        }
        return toLogDto(entity);
    }

    private FarmEntity requireField(String ownerEmail, Long fieldId) {
        return farmRepository.findByIdAndUserEmail(fieldId, ownerEmail).orElseThrow(FieldException::fieldNotFound);
    }

    private List<FieldHistoryItemDto> buildHistory(FarmEntity field, String ownerEmail, LocalDate today) {
        LocalDate from = today.minusDays(6);
        Map<LocalDate, FieldDailyReportEntity> reportsByDate = new LinkedHashMap<>();
        for (FieldDailyReportEntity entity : dailyReportService.findRecent(field.getId(), ownerEmail, from, today)) {
            reportsByDate.putIfAbsent(entity.getReportDate(), entity);
        }
        List<FieldActivityLogEntity> logs = logRepository.findByFarmIdAndOwnerEmailAndLoggedAtBetweenOrderByLoggedAtDesc(
                field.getId(), ownerEmail, from.atStartOfDay(), today.atTime(23, 59, 59));
        Map<LocalDate, List<String>> logLabelsByDate = new LinkedHashMap<>();
        for (FieldActivityLogEntity log : logs) {
            LocalDate date = log.getLoggedAt().toLocalDate();
            String label = CATEGORY_LABELS.getOrDefault(parseCategory(log.getCategory()), "기타");
            logLabelsByDate.computeIfAbsent(date, key -> new java.util.ArrayList<>());
            if (!logLabelsByDate.get(date).contains(label)) logLabelsByDate.get(date).add(label);
        }

        List<FieldHistoryItemDto> history = new java.util.ArrayList<>();
        for (LocalDate date = today; !date.isBefore(from); date = date.minusDays(1)) {
            FieldDailyReportEntity entity = reportsByDate.get(date);
            FieldDailyStatus status = null;
            String keyMetric = null;
            String headline = null;
            if (entity != null) {
                FieldDailyReportDto dto = dailyReportService.readPayload(entity);
                status = dto.getStatus();
                keyMetric = buildKeyMetric(dto);
                headline = dto.getHeadline();
            }
            history.add(FieldHistoryItemDto.builder()
                    .date(date.toString())
                    .status(status)
                    .statusLabel(status == null ? "확인 필요" : STATUS_LABELS.get(status))
                    .logLabels(logLabelsByDate.getOrDefault(date, List.of()))
                    .reportAvailable(entity != null)
                    .keyMetric(keyMetric)
                    .managementSummary(headline)
                    .build());
        }
        return history;
    }

    /**
     * Derives the 0-100 종합 상태 점수 from real alert severities already computed
     * by {@link FieldGuidanceRuleEngine} rather than inventing an unrelated number.
     * Returns null when weather data was unavailable (NEEDS_CHECK with no alerts)
     * since presenting a precise score there would be false precision.
     */
    private Integer computeStatusScore(FieldDailyStatus status, List<FieldAlertDto> alerts) {
        boolean noAlertSignal = alerts == null || alerts.isEmpty();
        if (status == FieldDailyStatus.NEEDS_CHECK && noAlertSignal) {
            return null;
        }
        int score = 0;
        if (alerts != null) {
            for (FieldAlertDto alert : alerts) {
                String severity = alert.getSeverity() == null ? "" : alert.getSeverity().toUpperCase(Locale.ROOT);
                score += switch (severity) {
                    case "HIGH" -> 75;
                    case "MEDIUM" -> 45;
                    case "LOW" -> 20;
                    default -> 35;
                };
            }
        }
        if (status == FieldDailyStatus.DANGER) score = Math.max(score, 70);
        if (status == FieldDailyStatus.CAUTION) score = Math.max(score, 35);
        return Math.max(0, Math.min(100, score));
    }

    private FieldDailyStatus effectiveStatus(FieldDailyStatus persistedStatus, List<FieldAlertDto> alerts) {
        if (alerts != null && alerts.stream().anyMatch(alert -> "HIGH".equalsIgnoreCase(alert.getSeverity()))) {
            return FieldDailyStatus.DANGER;
        }
        return persistedStatus == null ? FieldDailyStatus.NEEDS_CHECK : persistedStatus;
    }

    private String statusScoreZone(Integer score) {
        if (score == null) return "확인 필요";
        if (score <= 30) return "적정";
        if (score <= 65) return "주의";
        return "위험";
    }

    private String buildKeyMetric(FieldDailyReportDto dto) {
        if (dto == null) return null;
        FieldWeatherDto weather = dto.getWeather();
        if (weather == null || weather.getStatus() != FieldWeatherStatus.AVAILABLE) return null;
        List<String> parts = new ArrayList<>();
        if (weather.getMaxTemperature() != null) {
            parts.add("최고 " + Math.round(weather.getMaxTemperature()) + "°C");
        }
        if (weather.getHumidity() != null) {
            parts.add("습도 " + Math.round(weather.getHumidity()) + "%");
        }
        if (weather.getRainfallMm() != null) {
            parts.add("강수량 " + (weather.getRainfallMm() == 0 ? "0" : String.valueOf(weather.getRainfallMm())) + "mm");
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    private FieldLogCategory parseCategory(String value) {
        try {
            return FieldLogCategory.valueOf(value);
        } catch (Exception exception) {
            return FieldLogCategory.OTHER;
        }
    }

    private FieldActivityLogDto toLogDto(FieldActivityLogEntity entity) {
        FieldLogCategory category = parseCategory(entity.getCategory());
        return FieldActivityLogDto.builder()
                .id(String.valueOf(entity.getId()))
                .fieldId(String.valueOf(entity.getFarmId()))
                .category(category)
                .categoryLabel(CATEGORY_LABELS.getOrDefault(category, "기타"))
                .note(entity.getNote())
                .loggedAt(entity.getLoggedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                .build();
    }

    private int cultivationDay(LocalDate startDate, LocalDate selectedDate) {
        if (startDate == null) return 1;
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, selectedDate);
        return (int) Math.max(1, days + 1);
    }

    private FieldDashboardResponseDto.SoilInfoDto resolveSoilInfo(FarmEntity field, String ownerEmail) {
        if (field.getRegionAnalysisId() == null) {
            return FieldDashboardResponseDto.SoilInfoDto.builder().available(false).build();
        }
        RegionAnalysisEntity analysis = regionAnalysisRepository
                .findByIdAndUserEmail(field.getRegionAnalysisId(), ownerEmail).orElse(null);
        if (analysis == null || analysis.getPayloadJson() == null) {
            return FieldDashboardResponseDto.SoilInfoDto.builder().available(false).build();
        }
        try {
            RegionReportResponseDto report = objectMapper.readValue(analysis.getPayloadJson(), RegionReportResponseDto.class);
            RegionReportResponseDto.ComponentDetailDto soil = report.getComponents() == null ? null : report.getComponents().getSoil();
            if (soil == null || (soil.getSoilPh() == null && soil.getSoilEc() == null)) {
                return FieldDashboardResponseDto.SoilInfoDto.builder().available(false).build();
            }
            return FieldDashboardResponseDto.SoilInfoDto.builder()
                    .available(true).ph(soil.getSoilPh()).ec(soil.getSoilEc()).build();
        } catch (Exception exception) {
            log.warn("Unable to read soil info from region analysis {} for field {}: {}",
                    field.getRegionAnalysisId(), field.getId(), exception.getMessage());
            return FieldDashboardResponseDto.SoilInfoDto.builder().available(false).build();
        }
    }

    private String resolveRegionName(FarmEntity field, String ownerEmail) {
        if (field.getRegionAnalysisId() == null) return "지역 정보 없음";
        RegionAnalysisEntity analysis = regionAnalysisRepository
                .findByIdAndUserEmail(field.getRegionAnalysisId(), ownerEmail).orElse(null);
        if (analysis == null) return "지역 정보 없음";
        return (analysis.getSidoName() == null ? "" : analysis.getSidoName())
                + " " + (analysis.getSigunguName() == null ? "" : analysis.getSigunguName());
    }
}
