package com.example.aiworkspace.service.farm;

import com.example.aiworkspace.domain.farm.FarmEntity;
import com.example.aiworkspace.domain.farm.FarmRepository;
import com.example.aiworkspace.domain.farm.FieldDailyReportEntity;
import com.example.aiworkspace.domain.farm.FieldDailyReportRepository;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.dto.field.CreateFieldRequestDto;
import com.example.aiworkspace.dto.field.FieldDailyReportDto;
import com.example.aiworkspace.dto.field.FieldProfileResponseDto;
import com.example.aiworkspace.dto.field.FieldSuitabilityPreviewDto;
import com.example.aiworkspace.dto.field.FieldSuitabilityReportDto;
import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import com.example.aiworkspace.service.analysis.LocationResolution;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal authenticated My Farm lifecycle.  It never calls an external
 * provider: a field decision is explicitly derived from an owner-linked,
 * persisted region analysis snapshot and therefore carries its basis date.
 */
@Slf4j
@Service
public class FieldService {

    private final FarmRepository farmRepository;
    private final FieldDailyReportRepository dailyReportRepository;
    private final RegionAnalysisRepository regionAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public FieldService(FarmRepository farmRepository,
                        FieldDailyReportRepository dailyReportRepository,
                        RegionAnalysisRepository regionAnalysisRepository,
                        ObjectMapper objectMapper) {
        this(farmRepository, dailyReportRepository, regionAnalysisRepository, objectMapper, Clock.systemDefaultZone());
    }

    public FieldService(FarmRepository farmRepository,
                        FieldDailyReportRepository dailyReportRepository,
                        RegionAnalysisRepository regionAnalysisRepository,
                        ObjectMapper objectMapper,
                        Clock clock) {
        this.farmRepository = farmRepository;
        this.dailyReportRepository = dailyReportRepository;
        this.regionAnalysisRepository = regionAnalysisRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public FieldProfileResponseDto create(String ownerEmail, CreateFieldRequestDto request) {
        validate(request);
        RegionAnalysisEntity analysis = regionAnalysisRepository
                .findByIdAndUserEmail(request.getRegionAnalysisId(), ownerEmail)
                .orElseThrow(() -> FieldException.analysisNotFound(request.getRegionAnalysisId()));
        RegionReportResponseDto regionReport = readRegionReport(analysis);
        CropResolution crop = resolveCrop(request, regionReport);
        FieldSuitabilityReportDto suitability = buildSuitability(regionReport, crop.cropCode(), crop.cropName(), request);
        LocationResolution location = regionReport.getLocation();

        FarmEntity field = FarmEntity.builder()
                .userEmail(ownerEmail)
                .fieldName(request.getFieldName().trim())
                .cropCode(crop.cropCode())
                .cropName(crop.cropName())
                .regionAnalysisId(request.getRegionAnalysisId())
                .locationJson(write(location))
                .cultivationMethod(request.getCultivationMethod().trim())
                .cultivationStartDate(request.getCultivationStartDate())
                .stage(normalizeStage(request.getStage()))
                .active(true)
                .daysPlanted(daysPlanted(request.getCultivationStartDate()))
                .statusBadge("ANALYSIS_READY")
                .statusBadgeColor("green")
                .todayTask(firstOr(suitability.getCurrentManagementPoints(), "분석 기준을 확인하세요."))
                .reportTime("분석 기준 " + suitability.getAnalysisBasisDate())
                .build();
        FarmEntity saved = farmRepository.save(field);

        LocalDateTime generatedAt = LocalDateTime.now(clock);
        FieldDailyReportDto registration = dailyReport(saved, suitability, generatedAt.toLocalDate(), generatedAt, "REGISTRATION");
        FieldDailyReportEntity stored = dailyReportRepository.save(FieldDailyReportEntity.builder()
                .id(registration.getId()).farmId(saved.getId()).ownerEmail(ownerEmail)
                .reportDate(generatedAt.toLocalDate()).generationReason("REGISTRATION")
                .generatedAt(generatedAt).payloadJson(write(registration)).build());
        return toProfile(saved, location, suitability, registrationWithId(registration, stored.getId()));
    }

    @Transactional(readOnly = true)
    public FieldSuitabilityPreviewDto preview(String ownerEmail, CreateFieldRequestDto request) {
        validate(request);
        RegionAnalysisEntity analysis = regionAnalysisRepository
                .findByIdAndUserEmail(request.getRegionAnalysisId(), ownerEmail)
                .orElseThrow(() -> FieldException.analysisNotFound(request.getRegionAnalysisId()));
        RegionReportResponseDto regionReport = readRegionReport(analysis);
        CropResolution crop = resolveCrop(request, regionReport);
        FieldSuitabilityReportDto suitability = buildSuitability(regionReport, crop.cropCode(), crop.cropName(), request);

        log.info("[preview] owner={} regionAnalysisId={} crop={}/{} score={}",
                maskEmail(ownerEmail), request.getRegionAnalysisId(),
                crop.cropCode(), crop.cropName(), suitability.getSuitabilityScore());

        return FieldSuitabilityPreviewDto.builder()
                .fieldName(request.getFieldName().trim())
                .cropCode(crop.cropCode())
                .cropName(crop.cropName())
                .cultivationMethod(request.getCultivationMethod().trim())
                .cultivationStartDate(request.getCultivationStartDate() == null ? null : request.getCultivationStartDate().toString())
                .stage(normalizeStage(request.getStage()))
                .regionAnalysisId(request.getRegionAnalysisId())
                .suitabilityReport(suitability)
                .build();
    }

    @Transactional(readOnly = true)
    public List<FieldProfileResponseDto> getFields(String ownerEmail) {
        return farmRepository.findByUserEmailOrderByCreatedAtDesc(ownerEmail).stream()
                .map(field -> {
                    FieldDailyReportDto latest = latestReport(field, ownerEmail);
                    return toProfile(field, readLocation(field.getLocationJson()), recoverSuitability(field, ownerEmail, latest), latest);
                })
                .toList();
    }

    /**
     * Scheduler-facing entry point.  No @Scheduled annotation is used because
     * this project has no configured operational scheduler.  A future scheduler
     * can call this at 06:00 Asia/Seoul; generatedAt is fixed to 06:00 so the
     * snapshot is deterministic and idempotent per field/day/reason.
     */
    @Transactional
    public void generateDailyForActiveFields(LocalDate date) {
        LocalDate reportDate = date == null ? LocalDate.now(clock) : date;
        for (FarmEntity field : farmRepository.findByActiveTrue()) {
            if (field.getId() == null || dailyReportRepository.existsByFarmIdAndReportDateAndGenerationReason(
                    field.getId(), reportDate, "DAILY_0600")) {
                continue;
            }
            try {
                RegionAnalysisEntity analysis = regionAnalysisRepository
                        .findByIdAndUserEmail(field.getRegionAnalysisId(), field.getUserEmail())
                        .orElseThrow(() -> FieldException.analysisNotFound(field.getRegionAnalysisId()));
                RegionReportResponseDto regionReport = readRegionReport(analysis);
                CreateFieldRequestDto fieldInput = CreateFieldRequestDto.builder()
                        .fieldName(field.getFieldName()).cropCode(field.getCropCode()).cropName(field.getCropName())
                        .cultivationMethod(field.getCultivationMethod()).cultivationStartDate(field.getCultivationStartDate())
                        .stage(field.getStage()).regionAnalysisId(field.getRegionAnalysisId()).build();
                CropResolution crop = resolveCrop(fieldInput, regionReport);
                FieldSuitabilityReportDto suitability = buildSuitability(regionReport, crop.cropCode(), crop.cropName(), fieldInput);
                LocalDateTime generatedAt = reportDate.atTime(6, 0);
                FieldDailyReportDto daily = dailyReport(field, suitability, reportDate, generatedAt, "DAILY_0600");
                dailyReportRepository.save(FieldDailyReportEntity.builder()
                        .id(daily.getId()).farmId(field.getId()).ownerEmail(field.getUserEmail())
                        .reportDate(reportDate).generationReason("DAILY_0600")
                        .generatedAt(generatedAt).payloadJson(write(daily)).build());
            } catch (FieldException exception) {
                log.warn("Skipping deterministic daily field report for field {}: {}", field.getId(), exception.getCode());
            }
        }
    }

    private FieldSuitabilityReportDto buildSuitability(RegionReportResponseDto report, String cropCode,
                                                        String cropName, CreateFieldRequestDto field) {
        RegionReportResponseDto.CropDecisionDto crop = findCropDecision(report, cropCode, cropName);
        RegionReportResponseDto.RecommendedCropDto recommended = findRecommendedCrop(report, cropCode, cropName);
        Integer score = crop != null ? crop.getScore() : recommended == null ? null : recommended.getScore();
        if (score == null) {
            throw FieldException.cropNotEligible(cropName);
        }

        Integer climateScore = crop == null ? componentScore(report.getComponents() == null ? null : report.getComponents().getClimate())
                : crop.getSeasonalTemperatureScore();
        Integer soilScore = crop == null ? componentScore(report.getComponents() == null ? null : report.getComponents().getSoil())
                : average(crop.getSoilSuitabilityScore(), crop.getSoilPhScore());
        Integer hazardScore = crop == null ? hazardScore(report) : crop.getSeasonReadiness();
        List<FieldSuitabilityReportDto.RiskDto> risks = applicableRisks(report, cropCode);

        List<FieldSuitabilityReportDto.ConditionDto> conditions = List.of(
                condition("CLIMATE", "기후", climateScore, "지역 분석의 계절 기온 기준 상태"),
                condition("SOIL", "토양", soilScore, "지역 토양 적성·pH 기준 상태"),
                condition("NATURAL_HAZARD", "자연재해", hazardScore, "연결된 지역 예보 위험 기준 상태"),
                FieldSuitabilityReportDto.ConditionDto.builder().key("CULTIVATION").label("재배환경")
                        .status("INPUT_RECORDED").description(field.getCultivationMethod() + " / "
                                + normalizeStage(field.getStage()) + " 입력값 기준이며, 필지 조건은 현장 확인이 필요합니다.")
                        .build());
        List<String> checklist = prePlantChecklist(conditions, risks);
        List<String> management = currentManagement(report, risks);
        String basisDate = report.getAnalyzedAt();
        return FieldSuitabilityReportDto.builder().suitabilityScore(score).grade(grade(score))
                .summary(cropName + " 적합도는 연결된 지역 분석을 기준으로 산출되었습니다. 필지 실측값은 포함하지 않습니다.")
                .analysisBasisDate(basisDate).regionAnalysisId(report.getAnalysisId())
                .conditions(conditions).keyRisks(risks).prePlantChecklist(checklist)
                .currentManagementPoints(management).build();
    }

    private List<FieldSuitabilityReportDto.RiskDto> applicableRisks(RegionReportResponseDto report, String cropCode) {
        if (report.getTopRisks() == null) return List.of();
        return report.getTopRisks().stream()
                .filter(risk -> risk.getAffectedCrops() != null && risk.getAffectedCrops().stream()
                        .anyMatch(affected -> affected.equalsIgnoreCase(cropCode)))
                .map(risk -> FieldSuitabilityReportDto.RiskDto.builder().riskCode(risk.getRiskCode())
                        .severity(firstNonBlank(risk.getSeverity(), risk.getLevel())).title(risk.getTitle())
                        .description(risk.getDescription()).actions(copyOrEmpty(risk.getActions())).build())
                .toList();
    }

    private List<String> prePlantChecklist(List<FieldSuitabilityReportDto.ConditionDto> conditions,
                                            List<FieldSuitabilityReportDto.RiskDto> risks) {
        List<String> values = new ArrayList<>();
        for (FieldSuitabilityReportDto.ConditionDto condition : conditions) {
            if ("RISK".equals(condition.getStatus()) || "CAUTION".equals(condition.getStatus())) {
                values.add(condition.getLabel() + " 상태를 현장에서 확인하세요.");
            }
        }
        risks.stream().map(FieldSuitabilityReportDto.RiskDto::getActions).flatMap(List::stream)
                .filter(this::hasText).forEach(values::add);
        if (values.isEmpty()) values.add("재배 시작 전 필지 배수와 토양 상태를 현장에서 확인하세요.");
        return values.stream().distinct().limit(4).toList();
    }

    private List<String> currentManagement(RegionReportResponseDto report, List<FieldSuitabilityReportDto.RiskDto> risks) {
        List<String> values = risks.stream().map(FieldSuitabilityReportDto.RiskDto::getActions).flatMap(List::stream)
                .filter(this::hasText).distinct().toList();
        if (!values.isEmpty()) return values;
        if ("PARTIAL".equalsIgnoreCase(report.getStatus()) || (report.getMissingMetrics() != null && !report.getMissingMetrics().isEmpty())) {
            return List.of("공공 데이터가 부족해 추가 관리 포인트를 확정할 수 없습니다.");
        }
        return List.of("연결된 지역 분석에서 해당 작물에 연결된 핵심 위험은 없습니다.");
    }

    private FieldDailyReportDto dailyReport(FarmEntity field, FieldSuitabilityReportDto suitability,
                                             LocalDate reportDate, LocalDateTime generatedAt, String reason) {
        return FieldDailyReportDto.builder().id(UUID.randomUUID().toString())
                .fieldId(field.getId() == null ? null : String.valueOf(field.getId()))
                .reportDate(reportDate.toString()).generatedAt(generatedAt.format(DateTimeFormatter.ISO_DATE_TIME))
                .generationReason(reason).suitabilityScore(suitability.getSuitabilityScore())
                .summary(suitability.getSummary()).prioritizedActions(suitability.getCurrentManagementPoints())
                .keyRisks(suitability.getKeyRisks()).conditions(suitability.getConditions()).build();
    }

    private FieldProfileResponseDto toProfile(FarmEntity field, LocationResolution location,
                                              FieldSuitabilityReportDto suitability, FieldDailyReportDto latest) {
        return FieldProfileResponseDto.builder().id(field.getId() == null ? null : String.valueOf(field.getId()))
                .fieldName(field.getFieldName()).cropCode(field.getCropCode()).cropName(field.getCropName())
                .location(location).cultivationMethod(field.getCultivationMethod())
                .cultivationStartDate(field.getCultivationStartDate() == null ? null : field.getCultivationStartDate().toString())
                .stage(field.getStage()).linkedRegionAnalysisId(field.getRegionAnalysisId()).active(field.getActive())
                .createdAt(field.getCreatedAt() == null ? null : field.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                .updatedAt(field.getUpdatedAt() == null ? null : field.getUpdatedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                .suitabilityReport(suitability).latestReport(latest).build();
    }

    private FieldDailyReportDto latestReport(FarmEntity field, String ownerEmail) {
        if (field.getId() == null) return null;
        return dailyReportRepository.findFirstByFarmIdAndOwnerEmailOrderByGeneratedAtDesc(field.getId(), ownerEmail)
                .flatMap(entity -> read(entity.getPayloadJson(), FieldDailyReportDto.class)).orElse(null);
    }

    /**
     * A field remains tied to one immutable region-analysis snapshot.  Rebuilding
     * from that stored snapshot keeps GET /fields as complete as POST /fields
     * without manufacturing a new provider result after a browser refresh.
     */
    private FieldSuitabilityReportDto recoverSuitability(FarmEntity field, String ownerEmail, FieldDailyReportDto latest) {
        if (!hasText(field.getRegionAnalysisId())) return suitabilityFromLatest(field, latest);
        try {
            RegionAnalysisEntity analysis = regionAnalysisRepository
                    .findByIdAndUserEmail(field.getRegionAnalysisId(), ownerEmail)
                    .orElseThrow(() -> FieldException.analysisNotFound(field.getRegionAnalysisId()));
            RegionReportResponseDto regionReport = readRegionReport(analysis);
            CreateFieldRequestDto storedInput = CreateFieldRequestDto.builder()
                    .fieldName(field.getFieldName()).cropCode(field.getCropCode()).cropName(field.getCropName())
                    .cultivationMethod(field.getCultivationMethod()).cultivationStartDate(field.getCultivationStartDate())
                    .stage(field.getStage()).regionAnalysisId(field.getRegionAnalysisId()).build();
            CropResolution crop = resolveCrop(storedInput, regionReport);
            return buildSuitability(regionReport, crop.cropCode(), crop.cropName(), storedInput);
        } catch (FieldException exception) {
            log.warn("Returning only a durable field snapshot for field {}: {}", field.getId(), exception.getCode());
            return suitabilityFromLatest(field, latest);
        }
    }

    private FieldSuitabilityReportDto suitabilityFromLatest(FarmEntity field, FieldDailyReportDto latest) {
        if (latest == null || latest.getSuitabilityScore() == null) return null;
        return FieldSuitabilityReportDto.builder().suitabilityScore(latest.getSuitabilityScore())
                .grade(grade(latest.getSuitabilityScore())).summary(latest.getSummary())
                .regionAnalysisId(field.getRegionAnalysisId()).conditions(copyOrEmpty(latest.getConditions()))
                .keyRisks(copyOrEmpty(latest.getKeyRisks())).prePlantChecklist(List.of())
                .currentManagementPoints(copyOrEmpty(latest.getPrioritizedActions())).build();
    }

    private RegionReportResponseDto readRegionReport(RegionAnalysisEntity analysis) {
        if (!hasText(analysis.getPayloadJson())) throw FieldException.analysisPayloadUnavailable();
        RegionReportResponseDto report = read(analysis.getPayloadJson(), RegionReportResponseDto.class)
                .orElseThrow(FieldException::analysisPayloadUnavailable);
        // The stored payload carries a random analysisId from build time;
        // replace with the real entity id so suitability references are correct.
        return report.toBuilder().analysisId(analysis.getId()).build();
    }

    private CropResolution resolveCrop(CreateFieldRequestDto request, RegionReportResponseDto report) {
        List<CropResolution> eligible = eligibleCrops(report);
        CropResolution codeMatch = hasText(request.getCropCode())
                ? findEligibleByCode(eligible, request.getCropCode()) : null;
        CropResolution nameMatch = findEligibleByName(eligible, request.getCropName());

        if (hasText(request.getCropCode()) && hasText(request.getCropName())) {
            if (codeMatch == null && nameMatch == null) throw FieldException.cropNotEligible(request.getCropName());
            if (codeMatch == null || nameMatch == null || !sameCrop(codeMatch, nameMatch)) {
                throw FieldException.cropCodeNameMismatch(request.getCropCode(), request.getCropName());
            }
            return codeMatch;
        }
        CropResolution resolved = codeMatch == null ? nameMatch : codeMatch;
        if (resolved == null) throw FieldException.cropNotEligible(request.getCropName());
        return resolved;
    }

    private List<CropResolution> eligibleCrops(RegionReportResponseDto report) {
        Map<String, CropResolution> values = new LinkedHashMap<>();
        if (report.getCropResults() != null) {
            report.getCropResults().stream()
                    .filter(crop -> crop.getScore() != null)
                    .forEach(crop -> addEligibleCrop(values, crop.getCropCode(), crop.getCropName()));
        }
        if (report.getRecommendedCrops() != null) {
            report.getRecommendedCrops().stream()
                    .filter(crop -> crop.getScore() != null)
                    .forEach(crop -> addEligibleCrop(values, crop.getCropCode(), crop.getCropName()));
        }
        return List.copyOf(values.values());
    }

    private void addEligibleCrop(Map<String, CropResolution> values, String cropCode, String cropName) {
        if (!hasText(cropCode) || !hasText(cropName)) return;
        String canonicalCode = cropCode.trim().toUpperCase(Locale.ROOT);
        values.putIfAbsent(canonicalCode, new CropResolution(canonicalCode, cropName.trim()));
    }

    private CropResolution findEligibleByCode(List<CropResolution> candidates, String cropCode) {
        if (!hasText(cropCode)) return null;
        String canonical = cropCode.trim().toUpperCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.cropCode().equals(canonical)).findFirst().orElse(null);
    }

    private CropResolution findEligibleByName(List<CropResolution> candidates, String cropName) {
        if (!hasText(cropName)) return null;
        return candidates.stream().filter(candidate -> candidate.cropName().equalsIgnoreCase(cropName.trim())).findFirst().orElse(null);
    }

    private boolean sameCrop(CropResolution left, CropResolution right) {
        return left.cropCode().equals(right.cropCode()) && left.cropName().equalsIgnoreCase(right.cropName());
    }

    private RegionReportResponseDto.CropDecisionDto findCropDecision(RegionReportResponseDto report, String cropCode, String cropName) {
        if (report.getCropResults() == null) return null;
        return report.getCropResults().stream().filter(crop -> matchesCrop(crop.getCropCode(), crop.getCropName(), cropCode, cropName))
                .findFirst().orElse(null);
    }

    private RegionReportResponseDto.RecommendedCropDto findRecommendedCrop(RegionReportResponseDto report, String cropCode, String cropName) {
        if (report.getRecommendedCrops() == null) return null;
        return report.getRecommendedCrops().stream().filter(crop -> matchesCrop(crop.getCropCode(), crop.getCropName(), cropCode, cropName))
                .findFirst().orElse(null);
    }

    private boolean matchesCrop(String candidateCode, String candidateName, String cropCode, String cropName) {
        if (hasText(cropCode)) {
            return hasText(candidateCode) && candidateCode.equalsIgnoreCase(cropCode);
        }
        return hasText(cropName) && hasText(candidateName) && candidateName.equalsIgnoreCase(cropName);
    }

    private FieldSuitabilityReportDto.ConditionDto condition(String key, String label, Integer score, String description) {
        return FieldSuitabilityReportDto.ConditionDto.builder().key(key).label(label).score(score)
                .status(status(score)).description(description).build();
    }

    private int daysPlanted(LocalDate startDate) {
        return startDate == null ? 1 : Math.max(1, (int) Math.min(9999, LocalDate.now(clock).toEpochDay() - startDate.toEpochDay() + 1));
    }

    private Integer componentScore(RegionReportResponseDto.ComponentDetailDto component) {
        return component == null ? null : component.getScore();
    }

    private Integer hazardScore(RegionReportResponseDto report) {
        return report.getComponents() == null || report.getComponents().getHazard() == null
                ? null : report.getComponents().getHazard().getSafetyScore();
    }

    private Integer average(Integer first, Integer second) {
        if (first == null && second == null) return null;
        if (first == null) return second;
        if (second == null) return first;
        return (int) Math.round((first + second) / 2.0);
    }

    private String grade(Integer score) {
        if (score == null) return "UNAVAILABLE";
        if (score >= 85) return "VERY_GOOD";
        if (score >= 70) return "GOOD";
        if (score >= 55) return "MODERATE";
        return "CAUTION";
    }

    private String status(Integer score) {
        if (score == null) return "UNAVAILABLE";
        if (score >= 80) return "GOOD";
        if (score >= 60) return "CAUTION";
        return "RISK";
    }

    private String normalizeStage(String stage) {
        return hasText(stage) ? stage.trim() : "UNSPECIFIED";
    }

    private String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

    private String firstOr(List<String> values, String fallback) {
        return values == null || values.isEmpty() || !hasText(values.get(0)) ? fallback : values.get(0);
    }

    private <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private <T> Optional<T> read(String json, Class<T> type) {
        if (!hasText(json)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private LocationResolution readLocation(String json) {
        return read(json, LocationResolution.class).orElse(null);
    }

    private String write(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw FieldException.persistenceUnavailable();
        }
    }

    private FieldDailyReportDto registrationWithId(FieldDailyReportDto report, String id) {
        return id == null || id.equals(report.getId()) ? report : FieldDailyReportDto.builder().id(id)
                .fieldId(report.getFieldId()).reportDate(report.getReportDate()).generatedAt(report.getGeneratedAt())
                .generationReason(report.getGenerationReason()).suitabilityScore(report.getSuitabilityScore())
                .summary(report.getSummary()).prioritizedActions(report.getPrioritizedActions())
                .keyRisks(report.getKeyRisks()).conditions(report.getConditions()).build();
    }

    private void validate(CreateFieldRequestDto request) {
        if (request == null || !hasText(request.getFieldName()) || !hasText(request.getCropName())
                || !hasText(request.getCultivationMethod()) || request.getCultivationStartDate() == null
                || !hasText(request.getRegionAnalysisId())) {
            throw FieldException.invalidRequest();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "***" + domain;
        return local.substring(0, 2) + "***" + domain;
    }

    private record CropResolution(String cropCode, String cropName) {
    }

    public static class FieldException extends RuntimeException {
        private final HttpStatus httpStatus;
        private final String code;

        private FieldException(HttpStatus httpStatus, String code, String message) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
        }

        static FieldException invalidRequest() {
            return new FieldException(HttpStatus.BAD_REQUEST, "INVALID_FIELD_REQUEST", "밭 등록 정보가 올바르지 않습니다.");
        }

        static FieldException analysisNotFound(String analysisId) {
            return new FieldException(HttpStatus.NOT_FOUND, "REGION_ANALYSIS_NOT_FOUND", "소유한 지역 분석을 찾을 수 없습니다: " + analysisId);
        }

        static FieldException analysisPayloadUnavailable() {
            return new FieldException(HttpStatus.CONFLICT, "REGION_ANALYSIS_PAYLOAD_UNAVAILABLE", "지역 분석 스냅샷을 읽을 수 없습니다.");
        }

        static FieldException cropNotEligible(String cropName) {
            return new FieldException(HttpStatus.UNPROCESSABLE_ENTITY, "FIELD_CROP_NOT_ELIGIBLE",
                    "선택한 작물은 연결된 지역 분석의 계산 가능한 작물이 아닙니다: " + cropName);
        }

        static FieldException cropCodeNameMismatch(String cropCode, String cropName) {
            return new FieldException(HttpStatus.UNPROCESSABLE_ENTITY, "FIELD_CROP_CODE_NAME_MISMATCH",
                    "작물 코드와 이름이 연결된 지역 분석에서 같은 작물을 가리키지 않습니다: " + cropCode + "/" + cropName);
        }

        static FieldException persistenceUnavailable() {
            return new FieldException(HttpStatus.SERVICE_UNAVAILABLE, "FIELD_REPORT_PERSISTENCE_UNAVAILABLE", "밭 리포트를 저장할 수 없습니다.");
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }

        public String getCode() {
            return code;
        }
    }
}
