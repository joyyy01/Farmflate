package com.farmflate.service.analysis;

import com.farmflate.domain.region.Region;
import com.farmflate.domain.region.RegionAnalysisEntity;
import com.farmflate.domain.region.RegionAnalysisRepository;
import com.farmflate.domain.region.RegionRepository;
import com.farmflate.dto.region.HomeResponseDto;
import com.farmflate.dto.region.LocationRequestDto;
import com.farmflate.dto.region.RegionAnalysisRequestDto;
import com.farmflate.dto.region.RegionAnalysisStatusDto;
import com.farmflate.dto.region.RegionDto;
import com.farmflate.dto.region.RegionReportResponseDto;
import com.farmflate.exception.ApiException;
import com.farmflate.integration.AsosAdapter;
import com.farmflate.integration.ExternalResult;
import com.farmflate.integration.MidTermForecastAdapter;
import com.farmflate.integration.ShortForecastAdapter;
import com.farmflate.integration.SoilChemistryAdapter;
import com.farmflate.integration.SoilSuitabilityAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
    public static final Duration PROCESSING_STALE_AFTER = Duration.ofMinutes(5);

    private static String normalizePurpose(String requested) {
        return FIELD_LINKED_PURPOSE.equalsIgnoreCase(requested) ? FIELD_LINKED_PURPOSE : PRIMARY_PURPOSE;
    }

    private final RegionRepository regionRepository;
    private final RegionAnalysisRepository analysisRepository;
    private final CropScoringEngine cropScoringEngine;
    private final ObjectMapper objectMapper;
    private final ShortForecastAdapter shortForecastAdapter;
    private final ExternalDataCollector externalDataCollector;
    private final LocationResolutionService locationResolutionService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RegionAnalysisResponseMapper responseMapper = new RegionAnalysisResponseMapper();
    private final RegionProviderResultInterpreter providerResultInterpreter = new RegionProviderResultInterpreter();

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
                .map(responseMapper::toRegionDto)
                .toList();
    }

    @Transactional
    public RegionAnalysisStatusDto create(String userEmail, RegionAnalysisRequestDto request) {
        validateRequest(request);
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
        ExternalDataCollector.CollectedProviderData collected = externalDataCollector.collect(region, location);
        progress.begin("FORECAST");
        ExternalResult<List<ShortForecastAdapter.DailyForecast>> forecastResult = collected.shortForecast();
        ExternalResult<List<MidTermForecastAdapter.DailyForecast>> midTermForecastResult = collected.midTermForecast();
        progress.begin("SOIL");
        ExternalResult<AsosAdapter.Asos30DaySummary> asosResult = collected.asos();
        ExternalResult<SoilChemistryAdapter.SoilChemistryResult> soilChemistryResult = collected.soilChemistry();
        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> soilSuitabilityResult = collected.soilSuitability();

        List<ExternalResult<?>> results = List.of(forecastResult, midTermForecastResult, asosResult, soilChemistryResult, soilSuitabilityResult);
        if (results.stream().allMatch(ExternalResult::isFailure)) {
            throw RegionAnalysisException.externalDataUnavailable(providerResultInterpreter.providerFailureSummary(results));
        }

        List<String> missingMetrics = new ArrayList<>();
        providerResultInterpreter.appendProviderState(missingMetrics, "FORECAST", forecastResult);
        providerResultInterpreter.appendProviderState(missingMetrics, "MIDTERM_FORECAST", midTermForecastResult);
        providerResultInterpreter.appendProviderState(missingMetrics, "ASOS", asosResult);
        providerResultInterpreter.appendProviderState(missingMetrics, "SOIL_CHEMISTRY", soilChemistryResult);
        providerResultInterpreter.appendProviderState(missingMetrics, "SOIL_SUITABILITY", soilSuitabilityResult);

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
        input.midTermForecasts = midTermForecasts.stream().map(providerResultInterpreter::toForecastDay)
                .collect(Collectors.toCollection(ArrayList::new));
        input.forecastRiskSafetyScore = cropScoringEngine.calculateForecastRisks(forecasts).safetyScore;
        for (Map.Entry<String, SoilSuitabilityAdapter.SoilSuitabilityResult> entry : suitability.entrySet()) {
            SoilSuitabilityAdapter.SoilSuitabilityResult value = entry.getValue();
            if (value != null && value.hasData) {
                input.soilSuitabilityScores.put(entry.getKey(), value.score);
            }
        }
        providerResultInterpreter.applyQuality(input, "forecast", forecastResult);
        if (!forecastResult.isSuccess() && midTermForecastResult.isSuccess()) {
            // Representative-area days 4–10 are informative but must not be
            // presented with the same certainty as the exact-grid short forecast.
            input.dataQualityScores.put("forecast", 70.0);
        }
        providerResultInterpreter.applyQuality(input, "seasonalTemperature", asosResult);
        providerResultInterpreter.applyQuality(input, "soilPh", soilChemistryResult);
        providerResultInterpreter.applyQuality(input, "soilEc", soilChemistryResult);
        providerResultInterpreter.applyQuality(input, "soilSuitability", soilSuitabilityResult);

        progress.begin("CROP");
        CropScoringEngine.AnalysisOutput output = cropScoringEngine.analyze(input);
        progress.begin("REPORT");
        List<RegionReportResponseDto.SourceDto> sources = List.of(
                providerResultInterpreter.source("기상청", "단기예보", "https://www.weather.go.kr", forecastResult),
                providerResultInterpreter.source("기상청", "중기기온예보 (대표 예보지점)", "https://apihub.kma.go.kr", midTermForecastResult),
                providerResultInterpreter.source("기상청", "ASOS 관측자료", "https://data.kma.go.kr", asosResult),
                providerResultInterpreter.source("농촌진흥청", "농경지화학성 상세조사", "https://soil.rda.go.kr", soilChemistryResult),
                providerResultInterpreter.source("농촌진흥청", "작물별 토양적성", "https://soil.rda.go.kr", soilSuitabilityResult));
        return responseMapper.assemble(region, location, output, missingMetrics, sources);
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
        LocalDateTime staleBefore = LocalDateTime.now().minus(PROCESSING_STALE_AFTER);
        String executionToken = UUID.randomUUID().toString();
        if (analysisRepository.claimForExecution(analysisId, staleBefore, executionToken) == 0) {
            log.debug("Analysis {} was already claimed or is terminal", analysisId);
            return;
        }
        RegionAnalysisEntity entity = analysisRepository.findById(analysisId).orElse(null);
        if (entity == null) {
            log.warn("Analysis {} not found for async execution", analysisId);
            return;
        }
        ExecutionState executionState = new ExecutionState("REGION", "REGION");
        try {
            Region region = regionRepository.findBySidoCodeAndSigunguCode(entity.getSidoCode(), entity.getSigunguCode())
                    .orElseThrow(() -> RegionAnalysisException.mappingNotConfigured(entity.getSidoCode(), entity.getSigunguCode()));

            LocationRequestDto locationRequest = readLocationRequest(entity);
            LocationResolution location = locationResolutionService.resolve(locationRequest, region);

            ExecutionProgress progress = step -> {
                String completedCodes = buildCompletedStepCodes(step);
                if (analysisRepository.updateProgressIfOwned(analysisId, executionToken, step, completedCodes) == 0) {
                    throw new ExecutionOwnershipLostException();
                }
                executionState.advance(step, completedCodes);
            };

            RegionReportResponseDto report = executeLiveAnalysis(region, location, progress);

            RegionReportResponseDto scopedReport = report.toBuilder().analysisScope(entity.getAnalysisScope()).build();
            String payload = objectMapper.writeValueAsString(scopedReport);

            if (analysisRepository.completeIfOwned(
                    analysisId,
                    executionToken,
                    scopedReport.getStatus(),
                    scopedReport.getRegionScore(),
                    scopedReport.getGrade(),
                    scopedReport.getSummary(),
                    scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getLevel() : null,
                    scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getScore() : null,
                    scopedReport.getDataConfidence() != null ? scopedReport.getDataConfidence().getMessage() : null,
                    payload,
                    LocalDateTime.now(),
                    "REGION,RECENT_WEATHER,FORECAST,SOIL,CROP,REPORT") == 0) {
                log.debug("Analysis {} execution lease was superseded before completion", analysisId);
            }
        } catch (ExecutionOwnershipLostException ignored) {
            log.debug("Analysis {} execution lease was superseded", analysisId);
        } catch (RegionAnalysisException exception) {
            markEntityFailed(analysisId, executionToken, executionState.currentStep(), executionState.completedSteps(),
                    exception.getCode(), exception.getMessage(), exception.isRetryable());
        } catch (Exception exception) {
            log.error("Unexpected failure in async analysis {}", analysisId, exception);
            markEntityFailed(analysisId, executionToken, executionState.currentStep(), executionState.completedSteps(),
                    "INTERNAL_ERROR", exception.getMessage(), true);
        }
    }

    /** A rejected dispatch remains PENDING and is picked up by bounded recovery. */
    public void markDispatchRejected(String analysisId) {
        log.warn("Analysis {} dispatch rejected; leaving PENDING for recovery", analysisId);
    }

    private void markEntityFailed(String analysisId, String executionToken, String step, String completedStepCodes,
                                    String failureCode, String failureMessage, boolean canRetry) {
        if (analysisRepository.failIfOwned(analysisId, executionToken, step, completedStepCodes,
                failureCode, failureMessage, canRetry) == 0) {
            log.debug("Analysis {} execution lease was superseded before failure persistence", analysisId);
        }
    }

    private LocationRequestDto readLocationRequest(RegionAnalysisEntity entity) {
        if (!hasText(entity.getLocationRequestJson())) return null;
        try {
            return objectMapper.readValue(entity.getLocationRequestJson(), LocationRequestDto.class);
        } catch (Exception exception) {
            throw RegionAnalysisException.locationRequestPayloadInvalid();
        }
    }

    private String serializeLocationRequest(LocationRequestDto location) {
        if (location == null) return null;
        try {
            return objectMapper.writeValueAsString(location);
        } catch (Exception exception) {
            throw RegionAnalysisException.locationRequestSerializationFailed();
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    private void validateRequest(RegionAnalysisRequestDto request) {
        if (request == null
                || !hasTextWithin(request.getSidoCode(), 20)
                || !hasTextWithin(request.getSidoName(), 100)
                || !hasTextWithin(request.getSigunguCode(), 20)
                || !hasTextWithin(request.getSigunguName(), 100)
                || !hasTextWithin(request.getIdempotencyKey(), 128)
                || (request.getLocation() != null && !request.getLocation().hasExactlyOnePrimaryLocator())) {
            throw RegionAnalysisException.invalidRequest();
        }
    }

    private boolean hasTextWithin(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private String firstOr(List<String> values, String fallback) {
        return values == null || values.isEmpty() || !hasText(values.get(0)) ? fallback : values.get(0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ExecutionState {
        private String currentStep;
        private String completedSteps;

        private ExecutionState(String currentStep, String completedSteps) {
            this.currentStep = currentStep;
            this.completedSteps = completedSteps;
        }

        private void advance(String currentStep, String completedSteps) {
            this.currentStep = currentStep;
            this.completedSteps = completedSteps;
        }

        private String currentStep() {
            return currentStep;
        }

        private String completedSteps() {
            return completedSteps;
        }
    }

    private static final class ExecutionOwnershipLostException extends RuntimeException {
    }

    public static class RegionAnalysisException extends ApiException {
        private RegionAnalysisException(HttpStatus httpStatus, String code, String message, boolean retryable) {
            super(httpStatus, code, message, retryable);
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

        public static RegionAnalysisException locationRequestSerializationFailed() {
            return new RegionAnalysisException(HttpStatus.UNPROCESSABLE_ENTITY, "LOCATION_REQUEST_SERIALIZATION_FAILED",
                    "입력한 위치 정보를 저장할 수 없습니다.", false);
        }

        public static RegionAnalysisException locationRequestPayloadInvalid() {
            return new RegionAnalysisException(HttpStatus.CONFLICT, "LOCATION_REQUEST_PAYLOAD_INVALID",
                    "저장된 위치 정보를 읽을 수 없습니다.", false);
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
            return getStatus();
        }
    }
}
