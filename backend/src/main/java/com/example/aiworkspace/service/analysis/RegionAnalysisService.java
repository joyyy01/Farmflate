package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.domain.region.Region;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.domain.region.RegionRepository;
import com.example.aiworkspace.dto.region.HomeResponseDto;
import com.example.aiworkspace.dto.region.LocationRequestDto;
import com.example.aiworkspace.dto.region.RegionAnalysisRequestDto;
import com.example.aiworkspace.dto.region.RegionAnalysisStatusDto;
import com.example.aiworkspace.dto.region.RegionDto;
import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import com.example.aiworkspace.service.external.AsosAdapter;
import com.example.aiworkspace.service.external.ExternalResult;
import com.example.aiworkspace.service.external.MidTermForecastAdapter;
import com.example.aiworkspace.service.external.NormalizedMetric;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import com.example.aiworkspace.service.external.SoilChemistryAdapter;
import com.example.aiworkspace.service.external.SoilSuitabilityAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the region-analysis lifecycle and the persisted screen snapshot.  The
 * service deliberately keeps provider failure distinct from an empty provider
 * response: only usable provider values enter the decision engine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionAnalysisService {

    private static final List<String> COMPLETED_STEPS = List.of(
            "REGION", "RECENT_WEATHER", "FORECAST", "SOIL", "CROP", "REPORT");
    private static final Map<String, String> STEP_LABELS = Map.of(
            "REGION", "지역 정보 확인 중",
            "RECENT_WEATHER", "기상청 데이터를 불러오는 중",
            "FORECAST", "기상청 데이터를 불러오는 중",
            "SOIL", "흙토람 토양 정보를 분석하는 중",
            "CROP", "추천 작물을 계산하는 중",
            "REPORT", "지역 농사 환경 점수를 산출하는 중");
    private static final String OWNER_SCOPE = "OWNER";
    private static final String PRIMARY_PURPOSE = "PRIMARY";
    private static final String FIELD_LINKED_PURPOSE = "FIELD_LINKED";

    private static String normalizePurpose(String requested) {
        return FIELD_LINKED_PURPOSE.equalsIgnoreCase(requested) ? FIELD_LINKED_PURPOSE : PRIMARY_PURPOSE;
    }

    private final RegionRepository regionRepository;
    private final RegionAnalysisRepository analysisRepository;
    private final CropScoringEngine cropScoringEngine;
    private final ObjectMapper objectMapper;
    private final ShortForecastAdapter shortForecastAdapter;
    private final MidTermForecastAdapter midTermForecastAdapter;
    private final AsosAdapter asosAdapter;
    private final SoilChemistryAdapter soilChemistryAdapter;
    private final SoilSuitabilityAdapter soilSuitabilityAdapter;
    private final LocationResolutionService locationResolutionService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(readOnly = true)
    public List<RegionDto> getSidos() {
        List<Region> list = regionRepository.findByEnabledTrueOrderBySidoNameAscSigunguNameAsc();
        Map<String, String> sidos = new LinkedHashMap<>();
        for (Region region : list) {
            sidos.putIfAbsent(region.getSidoCode(), region.getSidoName());
        }
        return sidos.entrySet().stream()
                .map(entry -> RegionDto.builder().sidoCode(entry.getKey()).sidoName(entry.getValue()).build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RegionDto> getSigungus(String sidoCode) {
        return regionRepository.findBySidoCodeAndEnabledTrueOrderBySigunguNameAsc(sidoCode).stream()
                .map(region -> RegionDto.builder()
                        .sidoCode(region.getSidoCode())
                        .sidoName(region.getSidoName())
                        .sigunguCode(region.getSigunguCode())
                        .sigunguName(region.getSigunguName())
                        .build())
                .toList();
    }

    @Transactional
    public RegionAnalysisStatusDto create(String userEmail, RegionAnalysisRequestDto request) {
        return createInScope(userEmail, OWNER_SCOPE, userEmail, request);
    }

    private RegionAnalysisStatusDto createInScope(String ownerEmail, String analysisScope,
                                                   String scopeSubject, RegionAnalysisRequestDto request) {
        if (hasText(request.getIdempotencyKey())) {
            Optional<RegionAnalysisEntity> existing = findByScopedIdempotency(
                    ownerEmail, analysisScope, scopeSubject, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return statusFor(existing.get(), false);
            }
        }

        if (!Boolean.TRUE.equals(request.getForceRefresh()) && !hasExplicitLocation(request)) {
            Optional<RegionAnalysisEntity> cached = findRecentSuccessful(ownerEmail, analysisScope, scopeSubject,
                    request.getSigunguCode());
            if (cached.isPresent()) {
                return statusFor(cached.get(), true);
            }
        }

        Region region = regionRepository.findBySidoCodeAndSigunguCode(request.getSidoCode(), request.getSigunguCode())
                .orElseThrow(() -> RegionAnalysisException.mappingNotConfigured(request.getSidoCode(), request.getSigunguCode()));

        RegionAnalysisEntity pending = RegionAnalysisEntity.builder()
                .id(UUID.randomUUID().toString())
                .idempotencyKey(request.getIdempotencyKey())
                .ruleVersion(CropScoringEngine.RULE_VERSION)
                .userEmail(ownerEmail)
                .analysisScope(analysisScope)
                .scopeSubject(scopeSubject)
                .purpose(normalizePurpose(request.getPurpose()))
                .sidoCode(region.getSidoCode())
                .sidoName(region.getSidoName())
                .sigunguCode(region.getSigunguCode())
                .sigunguName(region.getSigunguName())
                .locationRequestJson(serializeLocationRequest(request.getLocation()))
                .analyzedAt(LocalDateTime.now())
                .dataMode("API")
                .reportStatus("PENDING")
                .currentStep("REGION")
                .completedSteps("")
                .build();
        try {
            RegionAnalysisEntity saved = analysisRepository.saveAndFlush(pending);
            applicationEventPublisher.publishEvent(new RegionAnalysisJobRequestedEvent(saved.getId()));
            return statusFor(saved, false);
        } catch (DataIntegrityViolationException exception) {
            Optional<RegionAnalysisEntity> winner = hasText(request.getIdempotencyKey())
                    ? findByScopedIdempotency(ownerEmail, analysisScope, scopeSubject, request.getIdempotencyKey())
                    : Optional.empty();
            if (winner.isPresent()) {
                return statusFor(winner.get(), false);
            }
            throw exception;
        }
    }

    /**
     * Runs only the LIVE provider chain.  A FAILURE never becomes a synthetic
     * metric; a successful subset is persisted as PARTIAL with the exact
     * missing/failure identifiers shown to the caller.
     */
    private RegionReportResponseDto executeLiveAnalysis(Region region, LocationResolution location,
                                                          ExecutionProgress progress) {
        progress.begin("RECENT_WEATHER");
        ExternalResult<AsosAdapter.Asos30DaySummary> asosResult =
                asosAdapter.get30DaySummary(location.asosStationId());
        progress.begin("FORECAST");
        ExternalResult<List<ShortForecastAdapter.DailyForecast>> forecastResult =
                shortForecastAdapter.getForecast3Days(location.kmaNx(), location.kmaNy());
        ExternalResult<List<MidTermForecastAdapter.DailyForecast>> midTermForecastResult =
                midTermForecastAdapter.getForecast4To10Days(region.getSidoName(), region.getSigunguCode());
        progress.begin("SOIL");
        ExternalResult<SoilChemistryAdapter.SoilChemistryResult> soilChemistryResult =
                soilChemistryAdapter.getSoilChemistry(region.getSigunguCode(), region.getSidoName(), region.getSigunguName());
        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> soilSuitabilityResult =
                soilSuitabilityAdapter.getSoilSuitability(region.getSigunguCode(), region.getSidoName(), region.getSigunguName());

        List<ExternalResult<?>> results = List.of(forecastResult, midTermForecastResult, asosResult, soilChemistryResult, soilSuitabilityResult);
        if (results.stream().allMatch(ExternalResult::isFailure)) {
            throw RegionAnalysisException.externalDataUnavailable(providerFailureSummary(results));
        }

        List<String> missingMetrics = new ArrayList<>();
        appendProviderState(missingMetrics, "FORECAST", forecastResult);
        appendProviderState(missingMetrics, "MIDTERM_FORECAST", midTermForecastResult);
        appendProviderState(missingMetrics, "ASOS", asosResult);
        appendProviderState(missingMetrics, "SOIL_CHEMISTRY", soilChemistryResult);
        appendProviderState(missingMetrics, "SOIL_SUITABILITY", soilSuitabilityResult);

        List<ShortForecastAdapter.DailyForecast> forecasts = forecastResult.valueOr(List.of());
        List<MidTermForecastAdapter.DailyForecast> midTermForecasts = midTermForecastResult.valueOr(List.of());
        AsosAdapter.Asos30DaySummary asos = asosResult.valueOr(new AsosAdapter.Asos30DaySummary());
        SoilChemistryAdapter.SoilChemistryResult soilChemistry =
                soilChemistryResult.valueOr(new SoilChemistryAdapter.SoilChemistryResult());
        Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult> suitability = soilSuitabilityResult.valueOr(Map.of());

        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = asos.meanTemperature30d;
        input.soilPh = soilChemistry.ph;
        input.soilEc = soilChemistry.ec;
        input.shortForecasts = new ArrayList<>(forecasts);
        input.midTermForecasts = midTermForecasts.stream().map(this::toForecastDay).collect(Collectors.toCollection(ArrayList::new));
        input.forecastRiskSafetyScore = cropScoringEngine.calculateForecastRisks(forecasts).safetyScore;
        for (Map.Entry<String, SoilSuitabilityAdapter.SoilSuitabilityResult> entry : suitability.entrySet()) {
            SoilSuitabilityAdapter.SoilSuitabilityResult value = entry.getValue();
            if (value != null && value.hasData) {
                input.soilSuitabilityScores.put(entry.getKey(), value.score);
            }
        }
        applyQuality(input, "forecast", forecastResult);
        if (!forecastResult.isSuccess() && midTermForecastResult.isSuccess()) {
            // Representative-area days 4–10 are informative but must not be
            // presented with the same certainty as the exact-grid short forecast.
            input.dataQualityScores.put("forecast", 70.0);
        }
        applyQuality(input, "seasonalTemperature", asosResult);
        applyQuality(input, "soilPh", soilChemistryResult);
        applyQuality(input, "soilEc", soilChemistryResult);
        applyQuality(input, "soilSuitability", soilSuitabilityResult);

        progress.begin("CROP");
        CropScoringEngine.AnalysisOutput output = cropScoringEngine.analyze(input);
        progress.begin("REPORT");
        List<RegionReportResponseDto.RiskDto> risks = toRiskDtos(output.decisionOutput.riskEvents);
        List<RegionReportResponseDto.RecommendedCropDto> recommended = toRecommendedCrops(output.topRecommended);
        List<RegionReportResponseDto.CropDecisionDto> cropResults = toCropDecisions(output.allCropResults);
        RegionReportResponseDto.ComponentsDto components = enrichComponents(output.components);
        RegionReportResponseDto.ConfidenceDto dataConfidence = toDataConfidence(output.decisionOutput.dataConfidence);
        boolean partial = !missingMetrics.isEmpty() || recommended.isEmpty();
        if (recommended.isEmpty() && missingMetrics.isEmpty()) {
            missingMetrics.add("INSUFFICIENT_CALCULABLE_INPUTS");
            partial = true;
        }

        List<RegionReportResponseDto.SourceDto> sources = List.of(
                providerSource("기상청", "단기예보", "https://www.weather.go.kr", forecastResult),
                providerSource("기상청", "중기기온예보 (대표 예보지점)", "https://apihub.kma.go.kr", midTermForecastResult),
                providerSource("기상청", "ASOS 관측자료", "https://data.kma.go.kr", asosResult),
                providerSource("농촌진흥청", "농경지화학성 상세조사", "https://soil.rda.go.kr", soilChemistryResult),
                providerSource("농촌진흥청", "작물별 토양적성", "https://soil.rda.go.kr", soilSuitabilityResult));

        List<String> features = environmentFeatures(components, risks, missingMetrics);
        int regionScore = output.regionScoreCompatibility == null ? 0 : output.regionScoreCompatibility;
        return RegionReportResponseDto.builder()
                .analysisId(UUID.randomUUID().toString())
                .status(partial ? "PARTIAL" : "COMPLETED")
                .region(toRegionDto(region))
                .location(location)
                .regionScore(output.regionScoreCompatibility)
                .grade(output.regionGrade)
                .summary(buildSummary(region.getSigunguName(), regionScore, risks, missingMetrics))
                .confidence(output.confidence)
                .baseFitness(output.decisionOutput.baseFitness)
                .seasonReadiness(output.decisionOutput.seasonReadiness)
                .dataConfidence(dataConfidence)
                .components(components)
                .environment(RegionReportResponseDto.EnvironmentSummaryDto.builder()
                        .score(output.regionScoreCompatibility)
                        .grade(output.regionGrade)
                        .status(statusForScore(output.regionScoreCompatibility))
                        .features(features)
                        .conditions(components)
                        .build())
                .environmentFeatures(features)
                .recommendedCrops(recommended)
                .cropResults(cropResults)
                .topRisks(risks)
                .tips(buildOfficialTips(missingMetrics))
                .sources(sources)
                .missingMetrics(missingMetrics)
                .analyzedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .isCached(false)
                .build();
    }

    private void applyQuality(CropScoringEngine.AnalysisInput input, String key, ExternalResult<?> result) {
        input.dataQualityScores.put(key, result.isSuccess() ? 100.0 : result.isEmpty() ? 35.0 : 0.0);
    }

    private CropScoringEngine.ForecastDay toForecastDay(MidTermForecastAdapter.DailyForecast source) {
        CropScoringEngine.ForecastDay day = new CropScoringEngine.ForecastDay();
        day.date = source.date;
        day.minTemp = source.minTemp;
        day.maxTemp = source.maxTemp;
        return day;
    }

    private void appendProviderState(List<String> missingMetrics, String metric, ExternalResult<?> result) {
        if (result.isFailure()) {
            String state = isAvailabilityLimitation(result) ? "_UNAVAILABLE:" : "_PROVIDER_FAILURE:";
            missingMetrics.add(metric + state + result.errorCode());
        } else if (result.isEmpty()) {
            missingMetrics.add(metric + "_NO_RECORDS");
        }
    }

    private boolean isAvailabilityLimitation(ExternalResult<?> result) {
        String errorCode = result.errorCode();
        return errorCode != null && (errorCode.endsWith("_UNSUPPORTED_FOR_PH")
                || errorCode.contains("_LOCATION_NOT_RESOLVED")
                || errorCode.contains("_LOCATION_LOOKUP_FAILED"));
    }

    private String providerFailureSummary(Collection<ExternalResult<?>> results) {
        return results.stream().map(ExternalResult::errorCode).filter(this::hasText).collect(Collectors.joining(", "));
    }

    private RegionAnalysisStatusDto saveAndReturn(RegionReportResponseDto report, Region region, String ownerEmail,
                                                   String analysisScope, String scopeSubject,
                                                   String idempotencyKey, String mode) {
        RegionReportResponseDto scopedReport = report.toBuilder().analysisScope(analysisScope).build();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(scopedReport);
        } catch (Exception exception) {
            throw RegionAnalysisException.reportPayloadUnavailable("리포트를 저장할 수 없습니다.");
        }
        RegionAnalysisEntity entity = RegionAnalysisEntity.builder()
                .id(scopedReport.getAnalysisId())
                .idempotencyKey(idempotencyKey)
                .ruleVersion(CropScoringEngine.RULE_VERSION)
                .userEmail(ownerEmail)
                .analysisScope(analysisScope)
                .scopeSubject(scopeSubject)
                .sidoCode(region.getSidoCode())
                .sidoName(region.getSidoName())
                .sigunguCode(region.getSigunguCode())
                .sigunguName(region.getSigunguName())
                .regionScore(scopedReport.getRegionScore())
                .grade(scopedReport.getGrade())
                .summary(scopedReport.getSummary())
                .confidenceGrade(scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getLevel() : null)
                .confidenceScore(scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getScore() : null)
                .confidenceMessage(scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getMessage() : null)
                .payloadJson(payload)
                .analyzedAt(LocalDateTime.now())
                .dataMode("API")
                .reportStatus(scopedReport.getStatus())
                .build();
        try {
            // Flush in an isolated transaction so the unique index race is
            // observable here, rather than surfacing as a late HTTP 500 at the
            // outer transaction commit.
            analysisRepository.saveAndFlush(entity);
            return completedStatus(entity.getId(), scopedReport.getStatus(), false, analysisScope);
        } catch (DataIntegrityViolationException exception) {
            Optional<RegionAnalysisEntity> winner = hasText(idempotencyKey)
                    ? findByScopedIdempotency(ownerEmail, analysisScope, scopeSubject, idempotencyKey)
                    : Optional.empty();
            if (winner.isPresent()) {
                return completedStatus(winner.get().getId(), reportStatus(winner.get()), false, analysisScope);
            }
            throw exception;
        }
    }

    /**
     * Async entry point called by {@link RegionAnalysisJobDispatcher} after the
     * PENDING row has been committed.  Loads the entity, resolves location,
     * runs the live provider chain, and persists the final report.
     */
    public void executePersistedAnalysis(String analysisId) {
        RegionAnalysisEntity entity = analysisRepository.findById(analysisId).orElse(null);
        if (entity == null) {
            log.warn("Analysis {} not found for async execution", analysisId);
            return;
        }
        String currentStatus = reportStatus(entity);
        if ("COMPLETED".equals(currentStatus) || "PARTIAL".equals(currentStatus) || "FAILED".equals(currentStatus)) {
            return;
        }
        try {
            entity.markProcessing("REGION", "REGION");
            analysisRepository.saveAndFlush(entity);

            Region region = regionRepository.findBySidoCodeAndSigunguCode(entity.getSidoCode(), entity.getSigunguCode())
                    .orElseThrow(() -> RegionAnalysisException.mappingNotConfigured(entity.getSidoCode(), entity.getSigunguCode()));

            LocationRequestDto locationRequest = readLocationRequest(entity);
            LocationResolution location = locationResolutionService.resolve(locationRequest, region);

            ExecutionProgress progress = step -> {
                String completedCodes = buildCompletedStepCodes(step);
                entity.markProcessing(step, completedCodes);
                analysisRepository.saveAndFlush(entity);
            };

            RegionReportResponseDto report = executeLiveAnalysis(region, location, progress);

            RegionReportResponseDto scopedReport = report.toBuilder().analysisScope(entity.getAnalysisScope()).build();
            String payload = objectMapper.writeValueAsString(scopedReport);

            entity.markCompleted(
                    scopedReport.getStatus(),
                    scopedReport.getRegionScore(),
                    scopedReport.getGrade(),
                    scopedReport.getSummary(),
                    scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getLevel() : null,
                    scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getScore() : null,
                    scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getMessage() : null,
                    payload,
                    LocalDateTime.now(),
                    "REGION,RECENT_WEATHER,FORECAST,SOIL,CROP,REPORT");
            analysisRepository.saveAndFlush(entity);
        } catch (RegionAnalysisException exception) {
            markEntityFailed(entity, firstOr(java.util.List.of(entity.getCurrentStep()), "ANALYSIS"),
                    firstOr(java.util.List.of(entity.getCompletedSteps()), ""),
                    exception.getCode(), exception.getMessage(), exception.isRetryable());
        } catch (Exception exception) {
            log.error("Unexpected failure in async analysis {}", analysisId, exception);
            markEntityFailed(entity, firstOr(java.util.List.of(entity.getCurrentStep()), "ANALYSIS"),
                    firstOr(java.util.List.of(entity.getCompletedSteps()), ""),
                    "INTERNAL_ERROR", exception.getMessage(), true);
        }
    }

    /** Marks a queued analysis as FAILED when the async executor rejects it. */
    public void markDispatchRejected(String analysisId) {
        RegionAnalysisEntity entity = analysisRepository.findById(analysisId).orElse(null);
        if (entity == null) return;
        markEntityFailed(entity, "REGION", "", "DISPATCH_REJECTED",
                "분석 작업 큐가 가득 차 요청을 처리할 수 없습니다.", true);
    }

    private void markEntityFailed(RegionAnalysisEntity entity, String step, String completedStepCodes,
                                   String failureCode, String failureMessage, boolean canRetry) {
        entity.markFailed(step, completedStepCodes, failureCode, failureMessage, canRetry);
        analysisRepository.saveAndFlush(entity);
    }

    private LocationRequestDto readLocationRequest(RegionAnalysisEntity entity) {
        if (!hasText(entity.getLocationRequestJson())) return null;
        try {
            return objectMapper.readValue(entity.getLocationRequestJson(), LocationRequestDto.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private String serializeLocationRequest(LocationRequestDto location) {
        if (location == null) return null;
        try {
            return objectMapper.writeValueAsString(location);
        } catch (Exception exception) {
            return null;
        }
    }

    /** Builds a status DTO from a persisted entity with Korean step labels. */
    private RegionAnalysisStatusDto statusFor(RegionAnalysisEntity entity, boolean reused) {
        String status = reportStatus(entity);
        String normalized = "PARTIAL".equalsIgnoreCase(status) ? "PARTIAL"
                : "FAILED".equalsIgnoreCase(status) ? "FAILED"
                : "PROCESSING".equalsIgnoreCase(status) ? "PROCESSING"
                : "PENDING".equalsIgnoreCase(status) ? "PROCESSING"
                : "COMPLETED";
        if ("FAILED".equals(normalized)) {
            return RegionAnalysisStatusDto.builder()
                    .analysisId(entity.getId())
                    .status("FAILED")
                    .analysisScope(entity.getAnalysisScope())
                    .completedSteps(completedStepCodes(entity))
                    .currentStep(translateStep(entity.getCurrentStep()))
                    .completedStepCodes(rawCompletedStepCodes(entity))
                    .currentStepCode(entity.getCurrentStep())
                    .retryable(Boolean.TRUE.equals(entity.getRetryable()))
                    .reused(false)
                    .errorCode(entity.getErrorCode())
                    .errorMessage(entity.getErrorMessage())
                    .build();
        }
        return RegionAnalysisStatusDto.builder()
                .analysisId(entity.getId())
                .status(normalized)
                .analysisScope(entity.getAnalysisScope())
                .completedSteps(completedStepCodes(entity))
                .currentStep(translateStep(entity.getCurrentStep()))
                .completedStepCodes(rawCompletedStepCodes(entity))
                .currentStepCode(entity.getCurrentStep())
                .retryable(false)
                .reused(reused)
                .build();
    }

    private Optional<RegionAnalysisEntity> findByScopedIdempotency(
            String ownerEmail, String analysisScope, String scopeSubject, String idempotencyKey) {
        return OWNER_SCOPE.equals(analysisScope)
                ? analysisRepository.findByUserEmailAndIdempotencyKey(ownerEmail, idempotencyKey)
                : analysisRepository.findByAnalysisScopeAndScopeSubjectAndIdempotencyKey(
                        analysisScope, scopeSubject, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public RegionAnalysisStatusDto getStatus(String ownerEmail, UUID analysisId) {
        RegionAnalysisEntity entity = findAccessibleAnalysis(ownerEmail, analysisId);
        return statusFor(entity, false);
    }

    @Transactional(readOnly = true)
    public RegionReportResponseDto getReport(String ownerEmail, UUID analysisId) {
        RegionAnalysisEntity entity = findAccessibleAnalysis(ownerEmail, analysisId);
        return readReport(entity, analysisId);
    }

    private RegionReportResponseDto readReport(RegionAnalysisEntity entity, UUID analysisId) {
        if (!hasText(entity.getPayloadJson())) {
            throw RegionAnalysisException.reportPayloadUnavailable("저장된 분석 스냅샷이 없습니다.");
        }
        try {
            RegionReportResponseDto report = objectMapper.readValue(entity.getPayloadJson(), RegionReportResponseDto.class);
            // The stored payload carries a random analysisId generated at build time;
            // replace it with the real entity id so downstream lookups (field preview,
            // report re-fetch) resolve against the persisted row.
            return report.toBuilder().analysisId(analysisId.toString()).build();
        } catch (Exception exception) {
            log.warn("Unable to deserialize region report {}", analysisId, exception);
            throw RegionAnalysisException.reportPayloadUnavailable("저장된 분석 스냅샷을 읽을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public HomeResponseDto getHome(String userEmail, String userDisplayName) {
        String displayName = hasText(userDisplayName) ? userDisplayName : "Farmflate 사용자";
        Optional<RegionAnalysisEntity> latest = analysisRepository
                .findFirstByUserEmailAndPurposeAndReportStatusInOrderByAnalyzedAtDesc(userEmail, PRIMARY_PURPOSE, List.of("COMPLETED", "PARTIAL"))
                .or(() -> analysisRepository.findFirstByUserEmailAndPurposeOrderByAnalyzedAtDesc(userEmail, PRIMARY_PURPOSE));
        
        Region region = null;
        if (latest.isPresent()) {
            region = regionRepository.findBySidoCodeAndSigunguCode(latest.get().getSidoCode(), latest.get().getSigunguCode()).orElse(null);
        }

        if (latest.isEmpty() || region == null) {
            return HomeResponseDto.builder()
                    .user(HomeResponseDto.UserDto.builder().displayName(displayName).build())
                    .weather(HomeResponseDto.WeatherDto.builder().status("UNAVAILABLE").build())
                    .todayAction(null)
                    .latestRegionAnalysis(null)
                    .farms(Collections.emptyList())
                    .build();
        }

        HomeResponseDto.WeatherDto weatherDto = buildWeatherForRegion(region);

        RegionAnalysisEntity analysis = latest.get();
        RegionReportResponseDto report;
        try {
            report = getReport(userEmail, UUID.fromString(analysis.getId()));
        } catch (RuntimeException exception) {
            report = null;
        }
        List<HomeResponseDto.TopCropDto> recommendedCrops = List.of();
        if (report != null && report.getRecommendedCrops() != null && !report.getRecommendedCrops().isEmpty()) {
            recommendedCrops = report.getRecommendedCrops().stream()
                    .sorted(Comparator.comparing(
                            RegionReportResponseDto.RecommendedCropDto::getRank,
                            Comparator.nullsLast(Integer::compareTo)))
                    .limit(3)
                    .map(crop -> HomeResponseDto.TopCropDto.builder()
                            .rank(crop.getRank())
                            .cropCode(crop.getCropCode())
                            .cropName(crop.getCropName())
                            .score(crop.getScore())
                            .reason(firstOr(crop.getPositiveReasons(), "지역 분석 근거를 확인하세요."))
                            .build())
                    .toList();
        }
        HomeResponseDto.TodayActionDto todayAction = null;
        if (report != null && report.getTopRisks() != null && !report.getTopRisks().isEmpty()) {
            RegionReportResponseDto.RiskDto risk = report.getTopRisks().get(0);
            todayAction = HomeResponseDto.TodayActionDto.builder()
                    .title(firstOr(risk.getActions(), risk.getTitle()))
                    .reason(risk.getDescription()).riskCode(risk.getRiskCode()).build();
        }
        return HomeResponseDto.builder()
                .user(HomeResponseDto.UserDto.builder().displayName(displayName).build())
                .weather(weatherDto)
                .todayAction(todayAction)
                .latestRegionAnalysis(HomeResponseDto.LatestRegionAnalysisDto.builder()
                        .analysisId(analysis.getId())
                        .regionName(analysis.getSidoName() + " " + analysis.getSigunguName())
                        .score(analysis.getRegionScore())
                        .recommendedCrops(recommendedCrops)
                        .analyzedAt(analysis.getAnalyzedAt() == null ? null : analysis.getAnalyzedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                        .build())
                .farms(Collections.emptyList())
                .build();
    }

    private HomeResponseDto.WeatherDto buildWeatherForRegion(Region region) {
        if (region == null) {
            return HomeResponseDto.WeatherDto.builder().status("UNAVAILABLE").build();
        }
        try {
            ExternalResult<List<ShortForecastAdapter.DailyForecast>> forecastRes =
                    shortForecastAdapter.getForecast3Days(region.getKmaNx(), region.getKmaNy());
            
            List<ShortForecastAdapter.DailyForecast> forecasts = forecastRes != null ? forecastRes.valueOr(List.of()) : List.of();
            if (forecasts == null || forecasts.isEmpty()) {
                return HomeResponseDto.WeatherDto.builder().status("UNAVAILABLE").build();
            }

            ShortForecastAdapter.DailyForecast today = forecasts.get(0);
            Double currentTemp = (today.tmpValues != null && !today.tmpValues.isEmpty())
                    ? today.tmpValues.get(0)
                    : (today.minTemp != null && today.maxTemp != null ? (today.minTemp + today.maxTemp) / 2.0 : null);

            Double minTemp = today.minTemp;
            Double maxTemp = today.maxTemp;
            Integer pop = today.popMax;
            Double pcp = today.pcpTotal;
            Double humidity = today.rehAvg;
            Double windSpeed = today.wsdMax;

            String condition = null;
            if (pcp != null && pcp > 5.0) condition = "RAIN";
            else if (pop != null && pop >= 60) condition = "RAIN";
            else if (pop != null && pop >= 30) condition = "CLOUDY";
            else if (pop != null) condition = "SUNNY";

            String timeStr = null;
            if (today.date != null) {
                timeStr = "기상청 예보 (" + today.date + ")";
            }

            return HomeResponseDto.WeatherDto.builder()
                    .status("AVAILABLE")
                    .temperature(currentTemp != null ? (double) Math.round(currentTemp * 10.0) / 10.0 : null)
                    .minTemperature(minTemp != null ? (double) Math.round(minTemp * 10.0) / 10.0 : null)
                    .maxTemperature(maxTemp != null ? (double) Math.round(maxTemp * 10.0) / 10.0 : null)
                    .precipitationProbability(pop)
                    .humidity(humidity != null ? (double) Math.round(humidity * 10.0) / 10.0 : null)
                    .windSpeed(windSpeed != null ? (double) Math.round(windSpeed * 10.0) / 10.0 : null)
                    .condition(condition)
                    .observedOrForecastAt(timeStr)
                    .isCached(false)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to fetch weather for region {}: {}", region.getSigunguName(), e.getMessage());
            return HomeResponseDto.WeatherDto.builder().status("UNAVAILABLE").build();
        }
    }

    private Optional<RegionAnalysisEntity> findRecentSuccessful(String ownerEmail, String analysisScope,
                                                                String scopeSubject, String sigunguCode) {
        LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(6);
        Optional<RegionAnalysisEntity> candidate = OWNER_SCOPE.equals(analysisScope)
                ? analysisRepository.findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                        ownerEmail, sigunguCode, CropScoringEngine.RULE_VERSION, sixHoursAgo)
                : analysisRepository.findFirstByAnalysisScopeAndScopeSubjectAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                        analysisScope, scopeSubject, sigunguCode, CropScoringEngine.RULE_VERSION, sixHoursAgo);
        return candidate
                .filter(entity -> "COMPLETED".equals(reportStatus(entity)));
    }

    private RegionAnalysisStatusDto completedStatus(String analysisId, String status, boolean reused, String analysisScope) {
        String normalized = "PARTIAL".equalsIgnoreCase(status) ? "PARTIAL" : "COMPLETED";
        List<String> translatedCompleted = COMPLETED_STEPS.stream()
                .map(code -> STEP_LABELS.getOrDefault(code, code))
                .distinct()
                .toList();
        return RegionAnalysisStatusDto.builder()
                .analysisId(analysisId)
                .status(normalized)
                .analysisScope(analysisScope)
                .completedSteps(translatedCompleted)
                .currentStep(normalized)
                .retryable(false)
                .reused(reused)
                .build();
    }

    private RegionAnalysisStatusDto failedStatus(String code, String message, boolean retryable, String analysisScope) {
        return RegionAnalysisStatusDto.builder()
                .analysisId(UUID.randomUUID().toString())
                .status("FAILED")
                .analysisScope(analysisScope)
                .completedSteps(List.of())
                .currentStep("ANALYSIS")
                .retryable(retryable)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private List<String> completedStepCodes(RegionAnalysisEntity entity) {
        if (entity == null || !hasText(entity.getCompletedSteps())) {
            return List.of();
        }
        return Arrays.stream(entity.getCompletedSteps().split(","))
                .map(code -> STEP_LABELS.getOrDefault(code.trim(), code.trim()))
                .distinct()
                .toList();
    }

    private List<String> rawCompletedStepCodes(RegionAnalysisEntity entity) {
        if (entity == null || !hasText(entity.getCompletedSteps())) {
            return List.of();
        }
        return Arrays.stream(entity.getCompletedSteps().split(","))
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String translateStep(String stepCode) {
        if (!hasText(stepCode)) return null;
        return STEP_LABELS.getOrDefault(stepCode, stepCode);
    }

    private String buildCompletedStepCodes(String upToStep) {
        StringBuilder codes = new StringBuilder();
        for (String step : COMPLETED_STEPS) {
            if (codes.length() > 0) codes.append(",");
            codes.append(step);
            if (step.equals(upToStep)) break;
        }
        return codes.toString();
    }

    private String reportStatus(RegionAnalysisEntity entity) {
        return hasText(entity.getReportStatus()) ? entity.getReportStatus() : "COMPLETED";
    }

    private RegionAnalysisEntity findAccessibleAnalysis(String ownerEmail, UUID analysisId) {
        return analysisRepository.findByIdAndUserEmail(analysisId.toString(), ownerEmail)
                .orElseThrow(() -> RegionAnalysisException.analysisNotFound(analysisId));
    }

    private boolean hasExplicitLocation(RegionAnalysisRequestDto request) {
        return request.getLocation() != null && !request.getLocation().isRegionReference();
    }

    private RegionDto toRegionDto(Region region) {
        return RegionDto.builder().sidoCode(region.getSidoCode()).sidoName(region.getSidoName())
                .sigunguCode(region.getSigunguCode()).sigunguName(region.getSigunguName()).build();
    }

    private List<RegionReportResponseDto.RecommendedCropDto> toRecommendedCrops(
            List<CropScoringEngine.CropResult> crops) {
        if (crops == null) return List.of();
        List<RegionReportResponseDto.RecommendedCropDto> values = new ArrayList<>();
        for (int index = 0; index < crops.size(); index++) {
            CropScoringEngine.CropResult crop = crops.get(index);
            values.add(RegionReportResponseDto.RecommendedCropDto.builder()
                    .rank(index + 1).cropCode(crop.cropCode).cropName(crop.cropName)
                    .score(score(crop.totalScore)).positiveReasons(copyOrEmpty(crop.positiveReasons))
                    .cautionReason(crop.cautionReason).build());
        }
        return values;
    }

    private List<RegionReportResponseDto.CropDecisionDto> toCropDecisions(List<CropScoringEngine.CropResult> crops) {
        if (crops == null) return List.of();
        return crops.stream().map(crop -> RegionReportResponseDto.CropDecisionDto.builder()
                .cropCode(crop.cropCode).cropName(crop.cropName)
                .score(crop.calculable ? score(crop.totalScore) : null)
                .baseFitness(crop.baseFitness).seasonReadiness(crop.seasonReadiness)
                .baseCriticalCap(crop.baseCriticalCap).criticalRiskCap(crop.criticalRiskCap)
                .soilSuitabilityScore(score(crop.soilSuitabilityStatScore))
                .soilPhScore(score(crop.soilPhScore))
                .seasonalTemperatureScore(score(crop.seasonalTemperatureScore))
                .calculable(crop.calculable).notCalculableReason(crop.notCalculableReason)
                .positiveReasons(copyOrEmpty(crop.positiveReasons)).cautionReason(crop.cautionReason).build()).toList();
    }

    private List<RegionReportResponseDto.RiskDto> toRiskDtos(List<CropScoringEngine.RiskEvent> risks) {
        if (risks == null) return List.of();
        List<RegionReportResponseDto.RiskDto> values = new ArrayList<>();
        for (int index = 0; index < risks.size(); index++) {
            CropScoringEngine.RiskEvent risk = risks.get(index);
            List<RegionReportResponseDto.SourceDto> evidence = sourceRefs(risk.evidenceRefs);
            values.add(RegionReportResponseDto.RiskDto.builder()
                    .rank(index + 1).riskCode(risk.code).severity(risk.severity == null ? null : risk.severity.name())
                    .level(risk.severity == null ? null : risk.severity.name())
                    .title(riskTitle(risk.code)).description(riskDescription(risk.code))
                    .period(periodFor(risk.evidenceRefs)).affectedCrops(copyOrEmpty(risk.affectedCrops))
                    .actions(List.of(actionTitle(risk.code))).causalChain(copyOrEmpty(risk.causalChain))
                    .criticalCap(risk.criticalCap).remainingRisk(risk.remainingRisk)
                    .evidenceRefs(evidence).source(evidence.isEmpty() ? null : evidence.get(0)).build());
        }
        return values;
    }

    private RegionReportResponseDto.ConfidenceDto toDataConfidence(CropScoringEngine.DataConfidence confidence) {
        if (confidence == null) return null;
        RegionReportResponseDto.ScoreRangeDto range = confidence.scoreRange == null ? null
                : RegionReportResponseDto.ScoreRangeDto.builder()
                .min(confidence.scoreRange.min).max(confidence.scoreRange.max).build();
        return RegionReportResponseDto.ConfidenceDto.builder().grade(confidence.level == null ? null : confidence.level.name())
                .level(confidence.level == null ? null : confidence.level.name()).score(confidence.score)
                .message(confidence.message).range(range).build();
    }

    private RegionReportResponseDto.ComponentsDto enrichComponents(RegionReportResponseDto.ComponentsDto source) {
        if (source == null) return null;
        RegionReportResponseDto.ComponentDetailDto climate = component(source.getClimate());
        RegionReportResponseDto.ComponentDetailDto soil = component(source.getSoil());
        RegionReportResponseDto.ComponentDetailDto cultivation = component(source.getCultivation());
        RegionReportResponseDto.HazardComponentDetailDto hazard = source.getHazard() == null ? null
                : RegionReportResponseDto.HazardComponentDetailDto.builder().safetyScore(source.getHazard().getSafetyScore())
                .grade(source.getHazard().getGrade()).status(statusForScore(source.getHazard().getSafetyScore()))
                .description("예보에서 확인된 자연재해 노출 상태").build();
        return RegionReportResponseDto.ComponentsDto.builder().climate(climate).soil(soil)
                .hazard(hazard).cultivation(cultivation).build();
    }

    private RegionReportResponseDto.ComponentDetailDto component(RegionReportResponseDto.ComponentDetailDto source) {
        if (source == null) return null;
        return RegionReportResponseDto.ComponentDetailDto.builder().score(source.getScore()).grade(source.getGrade())
                .status(statusForScore(source.getScore())).description(componentDescription(source.getScore()))
                .soilPh(source.getSoilPh()).soilEc(source.getSoilEc()).build();
    }

    private List<String> environmentFeatures(RegionReportResponseDto.ComponentsDto components,
                                             List<RegionReportResponseDto.RiskDto> risks,
                                             List<String> missingMetrics) {
        List<String> values = new ArrayList<>();
        if (components != null && components.getClimate() != null) values.add("기후 상태: " + statusKorean(components.getClimate().getStatus()));
        if (components != null && components.getSoil() != null) values.add("토양 상태: " + statusKorean(components.getSoil().getStatus()));
        if (components != null && components.getHazard() != null) values.add("자연재해 상태: " + statusKorean(components.getHazard().getStatus()));
        if (components != null && components.getCultivation() != null) values.add("재배환경 상태: " + statusKorean(components.getCultivation().getStatus()));
        if (!risks.isEmpty()) values.add("핵심 위험: " + risks.get(0).getTitle());
        if (!missingMetrics.isEmpty()) values.add("일부 공공 데이터 미확인");
        return values;
    }

    /* environmentFeatures() concatenates this into a Korean sentence shown directly
     * in the report's "환경 특징" list, so the raw status code (GOOD/CAUTION/RISK/
     * UNAVAILABLE from statusForScore) must never leak through untranslated. */
    private String statusKorean(String status) {
        if (status == null) return "자료 부족";
        return switch (status) {
            case "GOOD" -> "양호";
            case "CAUTION" -> "주의";
            case "RISK" -> "위험";
            default -> "자료 부족";
        };
    }

    private List<RegionReportResponseDto.TipDto> buildOfficialTips(List<String> missingMetrics) {
        RegionReportResponseDto.SourceDto nongsaro = RegionReportResponseDto.SourceDto.builder()
                .provider("농촌진흥청").service("농사로 영농기술").sourceUrl("https://www.nongsaro.go.kr")
                .dataDate(LocalDate.now().toString()).evidenceLevel("OFFICIAL_GUIDE").build();
        RegionReportResponseDto.SourceDto soil = RegionReportResponseDto.SourceDto.builder()
                .provider("농촌진흥청").service("흙토람 토양검정").sourceUrl("https://soil.rda.go.kr")
                .dataDate(LocalDate.now().toString()).evidenceLevel("OFFICIAL_GUIDE").build();
        List<RegionReportResponseDto.TipDto> tips = new ArrayList<>(List.of(
                RegionReportResponseDto.TipDto.builder().rank(1).tipCode("DRAINAGE_BEFORE_RAIN")
                        .title("강수 전 배수로 확인").summary("작업 전 밭 주변 배수 경로와 막힌 구간을 점검하세요.")
                        .reason("공식 영농기술 자료 참고").sourceType("OFFICIAL_GUIDE").sourceName("농사로 공식자료")
                        .sourceUrl(nongsaro.getSourceUrl()).actionLabel("농사로 공식자료 보기")
                        .dataDate(nongsaro.getDataDate()).sourceRefs(List.of(nongsaro)).build(),
                RegionReportResponseDto.TipDto.builder().rank(2).tipCode("SOIL_TEST_GUIDE")
                        .title("토양검정 결과 확인").summary("필지별 pH와 비료 처방은 토양검정 결과로 확인하세요.")
                        .reason("공식 토양검정 안내 참고").sourceType("OFFICIAL_GUIDE").sourceName("농촌진흥청 흙토람")
                        .sourceUrl(soil.getSourceUrl()).actionLabel("흙토람 보기")
                        .dataDate(soil.getDataDate()).sourceRefs(List.of(soil)).build()));
        if (missingMetrics != null && missingMetrics.contains(
                "SOIL_CHEMISTRY_UNAVAILABLE:SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH")) {
            tips.add(RegionReportResponseDto.TipDto.builder().rank(3).tipCode("SOIL_STATISTICS_LIMITATION")
                    .title("지역 토양통계의 pH 한계 안내")
                    .summary("이번 지역 통계 응답은 pH 원값이 아닌 구간별 면적만 제공해 점수로 환산하지 않았습니다. 필지별 토양검정 결과를 확인하세요.")
                    .reason("농촌진흥청 토양통계 응답 형식").sourceType("DATA_LIMITATION")
                    .sourceName("농촌진흥청 토양통계").sourceUrl(soil.getSourceUrl())
                    .actionLabel("흙토람 보기").dataDate(soil.getDataDate()).sourceRefs(List.of(soil)).build());
        }
        return tips;
    }

    private RegionReportResponseDto.SourceDto providerSource(String provider, String service, String url,
                                                               ExternalResult<?> result) {
        boolean availabilityLimitation = isAvailabilityLimitation(result);
        String fallback = result.isFailure() ? result.errorCode() : result.isEmpty() ? "NO_RECORDS" : null;
        List<String> transformations = providerTransformations(result);
        return RegionReportResponseDto.SourceDto.builder().provider(provider).service(service).sourceUrl(url)
                .dataDate(LocalDate.now().toString()).status(availabilityLimitation ? "UNAVAILABLE" : result.status().name())
                .evidenceLevel(result.isSuccess() ? "PROVIDER_NORMALIZED" : "UNAVAILABLE")
                .isFallback(false).fallbackReason(fallback).transformations(transformations).build();
    }

    private List<String> providerTransformations(ExternalResult<?> result) {
        List<String> transformations = new ArrayList<>();
        if ("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH".equals(result.errorCode())) {
            transformations.add("AREA_DISTRIBUTION_NOT_COERCED_TO_PH");
        }
        if (result.errorCode() != null && result.errorCode().contains("_LOCATION_")) {
            transformations.add("LEGAL_DONG_NOT_RESOLVED");
        }
        if (result.errorCode() != null && result.errorCode().contains("CROP_NAME_")) {
            transformations.add("SOIL_FIT_CROP_NAME_VALIDATION_FAILED");
        }
        if (result.isEmpty()) {
            transformations.add("OFFICIAL_NO_RECORDS");
        }
        appendLegalDongSampleCoverage(transformations, result.metrics());
        return List.copyOf(transformations);
    }

    private void appendLegalDongSampleCoverage(List<String> transformations, List<NormalizedMetric> metrics) {
        Map<String, Double[]> coverageByCrop = new LinkedHashMap<>();
        for (NormalizedMetric metric : metrics) {
            if (metric == null || metric.numericValue() == null) {
                continue;
            }
            String cropCode = null;
            int position = switch (metric.metric()) {
                case "soil.eligible_legal_dongs" -> 0;
                case "soil.sampled_legal_dongs" -> 1;
                case "soil.data_backed_legal_dongs" -> 2;
                case "soil.suitability.eligible_legal_dongs" -> {
                    cropCode = metric.textValue();
                    yield 0;
                }
                case "soil.suitability.sampled_legal_dongs" -> {
                    cropCode = metric.textValue();
                    yield 1;
                }
                case "soil.suitability.data_backed_legal_dongs" -> {
                    cropCode = metric.textValue();
                    yield 2;
                }
                default -> -1;
            };
            if (position < 0) {
                continue;
            }
            String key = cropCode == null ? "" : cropCode;
            Double[] counts = coverageByCrop.computeIfAbsent(key, ignored -> new Double[3]);
            counts[position] = metric.numericValue();
        }
        coverageByCrop.forEach((cropCode, counts) -> {
            if (counts[0] == null || counts[1] == null || counts[2] == null) {
                return;
            }
            String cropQualifier = cropCode.isBlank() ? "" : "[" + cropCode + "]";
            transformations.add("LEGAL_DONG_SAMPLE_COVERAGE" + cropQualifier + ":"
                    + countText(counts[2]) + "/" + countText(counts[1]) + "_OF_" + countText(counts[0]));
        });
    }

    private String countText(double value) {
        return Math.rint(value) == value ? Long.toString((long) value) : Double.toString(value);
    }

    private List<RegionReportResponseDto.SourceDto> sourceRefs(List<String> refs) {
        if (refs == null) return List.of();
        return refs.stream().filter(this::hasText).map(ref -> RegionReportResponseDto.SourceDto.builder()
                .provider("기상청").service("단기예보").sourceUrl("https://www.weather.go.kr")
                .dataDate(ref.startsWith("forecast:") ? ref.substring("forecast:".length()) : null)
                .sourceRecordId(ref).evidenceLevel("FORECAST_EVIDENCE").build()).toList();
    }

    private RegionReportResponseDto.PeriodDto periodFor(List<String> refs) {
        List<LocalDate> dates = refs == null ? List.of() : refs.stream()
                .filter(ref -> ref.startsWith("forecast:"))
                .map(ref -> ref.substring("forecast:".length()))
                .map(this::parseForecastDate)
                .flatMap(Optional::stream)
                .sorted()
                .toList();
        if (dates.isEmpty()) return null;
        return RegionReportResponseDto.PeriodDto.builder()
                .start(dates.get(0).toString())
                .end(dates.get(dates.size() - 1).toString())
                .build();
    }

    private Optional<LocalDate> parseForecastDate(String value) {
        if (!hasText(value)) return Optional.empty();
        String normalized = value.trim();
        try {
            if (normalized.matches("\\d{8}")) {
                return Optional.of(LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE));
            }
            if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return Optional.of(LocalDate.parse(normalized));
            }
        } catch (RuntimeException ignored) {
            log.debug("Ignoring invalid forecast evidence date: {}", normalized);
        }
        return Optional.empty();
    }

    private String buildSummary(String sigunguName, int score, List<RegionReportResponseDto.RiskDto> risks,
                                List<String> missingMetrics) {
        String prefix = hasText(sigunguName) ? sigunguName + " 분석 결과: " : "지역 분석 결과: ";
        String core = score >= 80 ? "재배 기준이 양호한 편입니다." : score >= 60
                ? "일부 조건을 확인해야 합니다." : "작물 선택 전 현장 확인이 필요합니다.";
        if (!missingMetrics.isEmpty()) core += " 일부 공공 데이터가 없어 판단 범위가 제한됩니다.";
        if (!risks.isEmpty()) core += " 핵심 위험은 " + risks.get(0).getTitle() + "입니다.";
        return prefix + core;
    }

    private String riskTitle(String code) {
        if (code == null) return "환경 위험";
        return switch (code) {
            case "POTATO_WATERLOGGING", "WATERLOGGING" -> "배수 불량·침수 위험";
            case "PEAR_BLOSSOM_FROST" -> "배 개화기 서리 위험";
            case "COLD_FROST" -> "저온·서리 위험";
            case "CONCENTRATED_RAIN" -> "집중 강수 위험";
            case "HEAT" -> "고온 위험";
            case "WIND" -> "강풍 위험";
            case "DROUGHT" -> "건조 위험";
            case "HIGH_HUMIDITY" -> "고습 위험";
            case "CUCUMBER_POST_TRANSPLANT_NIGHT_COLD" -> "오이 정식 초기 저온 위험";
            case "LETTUCE_HEAT_HUMIDITY" -> "상추 고온다습 위험";
            default -> code;
        };
    }

    private String actionTitle(String code) {
        if (code == null) return "현장 조건 점검";
        return switch (code) {
            case "POTATO_WATERLOGGING", "WATERLOGGING", "CONCENTRATED_RAIN" -> "배수로와 고인 물 배출 경로 점검";
            case "PEAR_BLOSSOM_FROST", "COLD_FROST" -> "보온 덮개와 야간 보온 준비";
            case "HEAT" -> "차광과 관수 가능량 점검";
            case "WIND" -> "지지대와 시설 고정 상태 점검";
            case "DROUGHT" -> "관수와 토양 수분 상태 점검";
            case "HIGH_HUMIDITY" -> "환기와 밀식 구간 점검";
            default -> "현장 조건 점검";
        };
    }

    /* risk.causalChain is an internal, English-only debugging trail (e.g.
     * "high maximum temperature" -> "heat stress exposure") -- it must never
     * be joined and shown to the user directly. This maps each risk code to
     * a proper Korean sentence instead. */
    private String riskDescription(String code) {
        if (code == null) return "예보 기반 위험 조건이 감지되었습니다.";
        return switch (code) {
            case "POTATO_WATERLOGGING", "WATERLOGGING", "CONCENTRATED_RAIN" -> "집중 강수로 배수 부담이 커질 것으로 예상됩니다.";
            case "PEAR_BLOSSOM_FROST" -> "배 개화기에 저온이 예보되어 서리 피해 위험이 있습니다.";
            case "COLD_FROST" -> "저온이 이어질 것으로 예보되어 서리 피해가 우려됩니다.";
            case "HEAT" -> "고온이 이어질 것으로 예보되어 작물이 열 스트레스를 받을 수 있습니다.";
            case "WIND" -> "강한 바람이 예보되어 작물과 시설물이 흔들릴 수 있습니다.";
            case "DROUGHT" -> "건조한 날이 이어져 토양 수분이 부족해질 수 있습니다.";
            case "HIGH_HUMIDITY" -> "높은 습도가 이어져 병해충 발생 위험이 커질 수 있습니다.";
            case "CUCUMBER_POST_TRANSPLANT_NIGHT_COLD" -> "오이 정식 초기에 저온이 예보되어 활착에 어려움을 겪을 수 있습니다.";
            case "LETTUCE_HEAT_HUMIDITY" -> "상추 재배 시기에 고온다습이 이어져 생육 스트레스가 우려됩니다.";
            default -> "예보 기반 위험 조건이 감지되었습니다.";
        };
    }

    private String statusForScore(Integer score) {
        if (score == null) return "UNAVAILABLE";
        if (score >= 80) return "GOOD";
        if (score >= 60) return "CAUTION";
        return "RISK";
    }

    private String componentDescription(Integer score) {
        if (score == null) return "공공 데이터가 부족해 상태를 판단할 수 없습니다.";
        return statusForScore(score).equals("GOOD") ? "현재 분석 기준에서 양호한 상태입니다."
                : statusForScore(score).equals("CAUTION") ? "추가 관리 또는 현장 확인이 필요합니다."
                : "현재 분석 기준에서 위험 신호가 있습니다.";
    }

    private Integer score(Double value) {
        return value == null || !Double.isFinite(value) ? null : (int) Math.round(value);
    }

    private List<String> copyOrEmpty(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String firstOr(List<String> values, String fallback) {
        return values == null || values.isEmpty() || !hasText(values.get(0)) ? fallback : values.get(0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class RegionAnalysisException extends RuntimeException {
        private final HttpStatus httpStatus;
        private final String code;
        private final boolean retryable;

        private RegionAnalysisException(HttpStatus httpStatus, String code, String message, boolean retryable) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
            this.retryable = retryable;
        }

        public static RegionAnalysisException mappingNotConfigured(String sidoCode, String sigunguCode) {
            return new RegionAnalysisException(HttpStatus.UNPROCESSABLE_ENTITY, "REGION_MAPPING_NOT_CONFIGURED",
                    "해당 지역의 매핑 정보가 설정되어 있지 않습니다: " + sidoCode + "/" + sigunguCode, false);
        }

        public static RegionAnalysisException invalidRequest() {
            return new RegionAnalysisException(HttpStatus.BAD_REQUEST, "INVALID_REGION_REQUEST",
                    "요청한 지역 분석 정보가 올바르지 않습니다.", false);
        }

        public static RegionAnalysisException locationResolutionUnavailable(String detail) {
            return new RegionAnalysisException(HttpStatus.UNPROCESSABLE_ENTITY, "LOCATION_RESOLUTION_UNAVAILABLE",
                    "입력한 위치를 확인할 수 없습니다: " + detail, true);
        }

        public static RegionAnalysisException analysisNotFound(UUID analysisId) {
            return new RegionAnalysisException(HttpStatus.NOT_FOUND, "REGION_ANALYSIS_NOT_FOUND",
                    "지역 분석을 찾을 수 없습니다: " + analysisId, false);
        }

        public static RegionAnalysisException externalDataUnavailable(String detail) {
            return new RegionAnalysisException(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_DATA_UNAVAILABLE",
                    "공공 데이터 제공 상태를 확인할 수 없습니다" + (detail == null || detail.isBlank() ? "." : ": " + detail), true);
        }

        public static RegionAnalysisException reportPayloadUnavailable(String detail) {
            return new RegionAnalysisException(HttpStatus.CONFLICT, "REGION_REPORT_PAYLOAD_UNAVAILABLE", detail, false);
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }

        public String getCode() {
            return code;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
