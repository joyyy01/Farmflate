package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.domain.region.Region;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.domain.region.RegionRepository;
import com.example.aiworkspace.dto.region.HomeResponseDto;
import com.example.aiworkspace.dto.region.RegionAnalysisRequestDto;
import com.example.aiworkspace.dto.region.RegionAnalysisStatusDto;
import com.example.aiworkspace.dto.region.RegionDto;
import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import com.example.aiworkspace.service.external.AsosAdapter;
import com.example.aiworkspace.service.external.ExternalResult;
import com.example.aiworkspace.service.external.FixtureProvider;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import com.example.aiworkspace.service.external.SoilChemistryAdapter;
import com.example.aiworkspace.service.external.SoilSuitabilityAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private final RegionRepository regionRepository;
    private final RegionAnalysisRepository analysisRepository;
    private final CropScoringEngine cropScoringEngine;
    private final FixtureProvider fixtureProvider;
    private final ObjectMapper objectMapper;
    private final ShortForecastAdapter shortForecastAdapter;
    private final AsosAdapter asosAdapter;
    private final SoilChemistryAdapter soilChemistryAdapter;
    private final SoilSuitabilityAdapter soilSuitabilityAdapter;
    private final LocationResolutionService locationResolutionService;

    @Value("${app.data-mode:LIVE}")
    private String dataMode;

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
        if (hasText(request.getIdempotencyKey())) {
            Optional<RegionAnalysisEntity> existing = analysisRepository
                    .findByUserEmailAndIdempotencyKey(userEmail, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return completedStatus(existing.get().getId(), reportStatus(existing.get()), false);
            }
        }

        if (!Boolean.TRUE.equals(request.getForceRefresh()) && !hasExplicitLocation(request)) {
            Optional<RegionAnalysisEntity> cached = findRecentSuccessful(userEmail, request.getSigunguCode());
            if (cached.isPresent()) {
                return completedStatus(cached.get().getId(), reportStatus(cached.get()), true);
            }
        }

        Region region = regionRepository.findBySidoCodeAndSigunguCode(request.getSidoCode(), request.getSigunguCode())
                .orElseThrow(() -> RegionAnalysisException.mappingNotConfigured(request.getSidoCode(), request.getSigunguCode()));
        LocationResolution location = locationResolutionService.resolve(request.getLocation(), region);
        String mode = normalizedDataMode();

        if ("REPLAY".equals(mode)) {
            RegionReportResponseDto replay = fixtureProvider.getGochangFixture(
                    region.getSidoCode(), region.getSigunguCode(), region.getSidoName(), region.getSigunguName());
            RegionReportResponseDto explicitReplay = replay.toBuilder()
                    .status("COMPLETED")
                    .dataMode("REPLAY")
                    .isReplay(true)
                    .location(location)
                    .build();
            return saveAndReturn(explicitReplay, region, userEmail, request.getIdempotencyKey(), "REPLAY");
        }

        try {
            RegionReportResponseDto report = executeLiveAnalysis(region, location);
            return saveAndReturn(report, region, userEmail, request.getIdempotencyKey(), "LIVE");
        } catch (RegionAnalysisException exception) {
            if ("AUTO".equals(mode)) {
                Optional<RegionAnalysisEntity> cached = findRecentSuccessful(userEmail, request.getSigunguCode());
                if (cached.isPresent()) {
                    return completedStatus(cached.get().getId(), reportStatus(cached.get()), true);
                }
            }
            return failedStatus(exception.getCode(), exception.getMessage(), exception.isRetryable());
        } catch (Exception exception) {
            log.error("Region analysis failed for {} {}", region.getSidoCode(), region.getSigunguCode(), exception);
            if ("AUTO".equals(mode)) {
                Optional<RegionAnalysisEntity> cached = findRecentSuccessful(userEmail, request.getSigunguCode());
                if (cached.isPresent()) {
                    return completedStatus(cached.get().getId(), reportStatus(cached.get()), true);
                }
            }
            return failedStatus("REGION_ANALYSIS_UNAVAILABLE", "공공 데이터 분석을 완료하지 못했습니다.", true);
        }
    }

    /**
     * Runs only the LIVE provider chain.  A FAILURE never becomes a synthetic
     * metric; a successful subset is persisted as PARTIAL with the exact
     * missing/failure identifiers shown to the caller.
     */
    private RegionReportResponseDto executeLiveAnalysis(Region region, LocationResolution location) {
        ExternalResult<List<ShortForecastAdapter.DailyForecast>> forecastResult =
                shortForecastAdapter.getForecast3Days(location.kmaNx(), location.kmaNy());
        ExternalResult<AsosAdapter.Asos30DaySummary> asosResult =
                asosAdapter.get30DaySummary(location.asosStationId());
        ExternalResult<SoilChemistryAdapter.SoilChemistryResult> soilChemistryResult =
                soilChemistryAdapter.getSoilChemistry(region.getSigunguCode(), region.getSidoName(), region.getSigunguName());
        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> soilSuitabilityResult =
                soilSuitabilityAdapter.getSoilSuitability(region.getSigunguCode(), region.getSidoName(), region.getSigunguName());

        List<ExternalResult<?>> results = List.of(forecastResult, asosResult, soilChemistryResult, soilSuitabilityResult);
        if (results.stream().allMatch(ExternalResult::isFailure)) {
            throw RegionAnalysisException.externalDataUnavailable(providerFailureSummary(results));
        }

        List<String> missingMetrics = new ArrayList<>();
        appendProviderState(missingMetrics, "FORECAST", forecastResult);
        appendProviderState(missingMetrics, "ASOS", asosResult);
        appendProviderState(missingMetrics, "SOIL_CHEMISTRY", soilChemistryResult);
        appendProviderState(missingMetrics, "SOIL_SUITABILITY", soilSuitabilityResult);

        List<ShortForecastAdapter.DailyForecast> forecasts = forecastResult.valueOr(List.of());
        AsosAdapter.Asos30DaySummary asos = asosResult.valueOr(new AsosAdapter.Asos30DaySummary());
        SoilChemistryAdapter.SoilChemistryResult soilChemistry =
                soilChemistryResult.valueOr(new SoilChemistryAdapter.SoilChemistryResult());
        Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult> suitability = soilSuitabilityResult.valueOr(Map.of());

        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = asos.meanTemperature30d;
        input.soilPh = soilChemistry.ph;
        input.shortForecasts = new ArrayList<>(forecasts);
        input.forecastRiskSafetyScore = cropScoringEngine.calculateForecastRisks(forecasts).safetyScore;
        for (Map.Entry<String, SoilSuitabilityAdapter.SoilSuitabilityResult> entry : suitability.entrySet()) {
            SoilSuitabilityAdapter.SoilSuitabilityResult value = entry.getValue();
            if (value != null && value.hasData) {
                input.soilSuitabilityScores.put(entry.getKey(), value.score);
            }
        }
        applyQuality(input, "forecast", forecastResult);
        applyQuality(input, "seasonalTemperature", asosResult);
        applyQuality(input, "soilPh", soilChemistryResult);
        applyQuality(input, "soilSuitability", soilSuitabilityResult);

        CropScoringEngine.AnalysisOutput output = cropScoringEngine.analyze(input);
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
                providerSource("기상청", "ASOS 관측자료", "https://data.kma.go.kr", asosResult),
                providerSource("농촌진흥청", "농경지화학성 통계", "https://soil.rda.go.kr", soilChemistryResult),
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
                .safeWorkWindows(toSafeWorkWindows(output.decisionOutput.safeWorkWindows))
                .prioritizedActions(toPrioritizedActions(output.decisionOutput.prioritizedActions))
                .tips(buildOfficialTips())
                .sources(sources)
                .missingMetrics(missingMetrics)
                .analyzedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .dataMode("LIVE")
                .isCached(false)
                .isReplay(false)
                .build();
    }

    private void applyQuality(CropScoringEngine.AnalysisInput input, String key, ExternalResult<?> result) {
        input.dataQualityScores.put(key, result.isSuccess() ? 100.0 : result.isEmpty() ? 35.0 : 0.0);
    }

    private void appendProviderState(List<String> missingMetrics, String metric, ExternalResult<?> result) {
        if (result.isFailure()) {
            missingMetrics.add(metric + "_PROVIDER_FAILURE:" + result.errorCode());
        } else if (result.isEmpty()) {
            missingMetrics.add(metric + "_NO_RECORDS");
        }
    }

    private String providerFailureSummary(Collection<ExternalResult<?>> results) {
        return results.stream().map(ExternalResult::errorCode).filter(this::hasText).collect(Collectors.joining(", "));
    }

    private RegionAnalysisStatusDto saveAndReturn(RegionReportResponseDto report, Region region, String ownerEmail,
                                                   String idempotencyKey, String mode) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw RegionAnalysisException.reportPayloadUnavailable("리포트를 저장할 수 없습니다.");
        }
        RegionAnalysisEntity entity = RegionAnalysisEntity.builder()
                .id(report.getAnalysisId())
                .idempotencyKey(idempotencyKey)
                .ruleVersion(CropScoringEngine.RULE_VERSION)
                .userEmail(ownerEmail)
                .sidoCode(region.getSidoCode())
                .sidoName(region.getSidoName())
                .sigunguCode(region.getSigunguCode())
                .sigunguName(region.getSigunguName())
                .regionScore(report.getRegionScore())
                .grade(report.getGrade())
                .summary(report.getSummary())
                .confidenceGrade(report.getDataConfidence() != null ? report.getDataConfidence().getLevel() : null)
                .confidenceScore(report.getDataConfidence() != null ? report.getDataConfidence().getScore() : null)
                .confidenceMessage(report.getDataConfidence() != null ? report.getDataConfidence().getMessage() : null)
                .payloadJson(payload)
                .analyzedAt(LocalDateTime.now())
                .dataMode(mode)
                .reportStatus(report.getStatus())
                .build();
        analysisRepository.save(entity);
        return completedStatus(report.getAnalysisId(), report.getStatus(), false);
    }

    @Transactional(readOnly = true)
    public RegionAnalysisStatusDto getStatus(String ownerEmail, UUID analysisId) {
        RegionAnalysisEntity entity = findOwnedAnalysis(ownerEmail, analysisId);
        return completedStatus(entity.getId(), reportStatus(entity), false);
    }

    @Transactional(readOnly = true)
    public RegionReportResponseDto getReport(String ownerEmail, UUID analysisId) {
        RegionAnalysisEntity entity = findOwnedAnalysis(ownerEmail, analysisId);
        if (!hasText(entity.getPayloadJson())) {
            throw RegionAnalysisException.reportPayloadUnavailable("저장된 분석 스냅샷이 없습니다.");
        }
        try {
            return objectMapper.readValue(entity.getPayloadJson(), RegionReportResponseDto.class);
        } catch (Exception exception) {
            log.warn("Unable to deserialize region report {}", analysisId, exception);
            throw RegionAnalysisException.reportPayloadUnavailable("저장된 분석 스냅샷을 읽을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public HomeResponseDto getHome(String userEmail, String userDisplayName) {
        String displayName = hasText(userDisplayName) ? userDisplayName : "Farmflate 사용자";
        Optional<RegionAnalysisEntity> latest = analysisRepository.findFirstByUserEmailOrderByAnalyzedAtDesc(userEmail);
        if (latest.isEmpty()) {
            return HomeResponseDto.builder()
                    .user(HomeResponseDto.UserDto.builder().displayName(displayName).build())
                    .weather(HomeResponseDto.WeatherDto.builder().status("UNAVAILABLE").build())
                    .todayAction(null)
                    .latestRegionAnalysis(null)
                    .farms(Collections.emptyList())
                    .build();
        }
        RegionAnalysisEntity analysis = latest.get();
        RegionReportResponseDto report;
        try {
            report = getReport(userEmail, UUID.fromString(analysis.getId()));
        } catch (RuntimeException exception) {
            report = null;
        }
        HomeResponseDto.TopCropDto topCrop = null;
        HomeResponseDto.TodayActionDto todayAction = null;
        if (report != null && report.getRecommendedCrops() != null && !report.getRecommendedCrops().isEmpty()) {
            RegionReportResponseDto.RecommendedCropDto crop = report.getRecommendedCrops().get(0);
            topCrop = HomeResponseDto.TopCropDto.builder()
                    .cropCode(crop.getCropCode()).cropName(crop.getCropName()).score(crop.getScore())
                    .reason(firstOr(crop.getPositiveReasons(), "지역 분석 근거를 확인하세요."))
                    .build();
        }
        if (report != null && report.getTopRisks() != null && !report.getTopRisks().isEmpty()) {
            RegionReportResponseDto.RiskDto risk = report.getTopRisks().get(0);
            todayAction = HomeResponseDto.TodayActionDto.builder()
                    .title(firstOr(risk.getActions(), risk.getTitle()))
                    .reason(risk.getDescription()).riskCode(risk.getRiskCode()).build();
        }
        return HomeResponseDto.builder()
                .user(HomeResponseDto.UserDto.builder().displayName(displayName).build())
                // No live home-weather call is made here, so do not fabricate one from a region hash.
                .weather(HomeResponseDto.WeatherDto.builder().status("UNAVAILABLE").build())
                .todayAction(todayAction)
                .latestRegionAnalysis(HomeResponseDto.LatestRegionAnalysisDto.builder()
                        .analysisId(analysis.getId())
                        .regionName(analysis.getSidoName() + " " + analysis.getSigunguName())
                        .score(analysis.getRegionScore())
                        .topCrop(topCrop)
                        .analyzedAt(analysis.getAnalyzedAt() == null ? null : analysis.getAnalyzedAt().format(DateTimeFormatter.ISO_DATE_TIME))
                        .build())
                .farms(Collections.emptyList())
                .build();
    }

    private Optional<RegionAnalysisEntity> findRecentSuccessful(String ownerEmail, String sigunguCode) {
        LocalDateTime sixHoursAgo = LocalDateTime.now().minusHours(6);
        return analysisRepository.findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                        ownerEmail, sigunguCode, CropScoringEngine.RULE_VERSION, sixHoursAgo)
                .filter(entity -> "COMPLETED".equals(reportStatus(entity)));
    }

    private RegionAnalysisStatusDto completedStatus(String analysisId, String status, boolean reused) {
        String normalized = "PARTIAL".equalsIgnoreCase(status) ? "PARTIAL" : "COMPLETED";
        return RegionAnalysisStatusDto.builder()
                .analysisId(analysisId)
                .status(normalized)
                .completedSteps(COMPLETED_STEPS)
                .currentStep(normalized)
                .retryable(false)
                .reused(reused)
                .build();
    }

    private RegionAnalysisStatusDto failedStatus(String code, String message, boolean retryable) {
        return RegionAnalysisStatusDto.builder()
                .analysisId(UUID.randomUUID().toString())
                .status("FAILED")
                .completedSteps(List.of())
                .currentStep("ANALYSIS")
                .retryable(retryable)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private String reportStatus(RegionAnalysisEntity entity) {
        return hasText(entity.getReportStatus()) ? entity.getReportStatus() : "COMPLETED";
    }

    private RegionAnalysisEntity findOwnedAnalysis(String ownerEmail, UUID analysisId) {
        return analysisRepository.findByIdAndUserEmail(analysisId.toString(), ownerEmail)
                .orElseThrow(() -> RegionAnalysisException.analysisNotFound(analysisId));
    }

    private boolean hasExplicitLocation(RegionAnalysisRequestDto request) {
        return request.getLocation() != null && !request.getLocation().isRegionReference();
    }

    private String normalizedDataMode() {
        String candidate = hasText(dataMode) ? dataMode.trim().toUpperCase(Locale.ROOT) : "LIVE";
        return switch (candidate) {
            case "LIVE", "AUTO", "REPLAY" -> candidate;
            default -> "LIVE";
        };
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
                    .title(riskTitle(risk.code)).description(riskDescription(risk))
                    .period(periodFor(risk.evidenceRefs)).affectedCrops(copyOrEmpty(risk.affectedCrops))
                    .actions(List.of(actionTitle(risk.code))).causalChain(copyOrEmpty(risk.causalChain))
                    .criticalCap(risk.criticalCap).remainingRisk(risk.remainingRisk)
                    .evidenceRefs(evidence).source(evidence.isEmpty() ? null : evidence.get(0)).build());
        }
        return values;
    }

    private List<RegionReportResponseDto.SafeWorkWindowDto> toSafeWorkWindows(
            List<CropScoringEngine.SafeWorkWindow> windows) {
        if (windows == null) return List.of();
        return windows.stream().map(window -> RegionReportResponseDto.SafeWorkWindowDto.builder()
                .start(window.startDate).end(window.endDate)
                .label(window.durationDays + "일 작업 가능")
                .reason(window.rationale).confidence(80)
                .sourceRefs(sourceRefs(window.evidenceRefs)).build()).toList();
    }

    private List<RegionReportResponseDto.PrioritizedActionDto> toPrioritizedActions(
            List<CropScoringEngine.PrioritizedAction> actions) {
        if (actions == null) return List.of();
        return actions.stream().map(action -> RegionReportResponseDto.PrioritizedActionDto.builder()
                .rank(action.rank).title(action.title)
                .reason("기상 예보 기반 " + action.relatedRiskCode + " 대응 작업")
                .leadTime(action.leadTimeDays == 0 ? "즉시" : "D-" + action.leadTimeDays)
                .sourceRefs(sourceRefs(action.evidenceRefs)).build()).toList();
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
                .status(statusForScore(source.getScore())).description(componentDescription(source.getScore())).build();
    }

    private List<String> environmentFeatures(RegionReportResponseDto.ComponentsDto components,
                                             List<RegionReportResponseDto.RiskDto> risks,
                                             List<String> missingMetrics) {
        List<String> values = new ArrayList<>();
        if (components != null && components.getClimate() != null) values.add("기후 상태: " + components.getClimate().getStatus());
        if (components != null && components.getSoil() != null) values.add("토양 상태: " + components.getSoil().getStatus());
        if (components != null && components.getHazard() != null) values.add("자연재해 상태: " + components.getHazard().getStatus());
        if (components != null && components.getCultivation() != null) values.add("재배환경 상태: " + components.getCultivation().getStatus());
        if (!risks.isEmpty()) values.add("핵심 위험: " + risks.get(0).getTitle());
        if (!missingMetrics.isEmpty()) values.add("일부 공공 데이터 미확인");
        return values;
    }

    private List<RegionReportResponseDto.TipDto> buildOfficialTips() {
        RegionReportResponseDto.SourceDto nongsaro = RegionReportResponseDto.SourceDto.builder()
                .provider("농촌진흥청").service("농사로 영농기술").sourceUrl("https://www.nongsaro.go.kr")
                .dataDate(LocalDate.now().toString()).evidenceLevel("OFFICIAL_GUIDE").build();
        RegionReportResponseDto.SourceDto soil = RegionReportResponseDto.SourceDto.builder()
                .provider("농촌진흥청").service("흙토람 토양검정").sourceUrl("https://soil.rda.go.kr")
                .dataDate(LocalDate.now().toString()).evidenceLevel("OFFICIAL_GUIDE").build();
        return List.of(
                RegionReportResponseDto.TipDto.builder().rank(1).tipCode("DRAINAGE_BEFORE_RAIN")
                        .title("강수 전 배수로 확인").summary("작업 전 밭 주변 배수 경로와 막힌 구간을 점검하세요.")
                        .reason("공식 영농기술 자료 참고").sourceType("OFFICIAL_GUIDE").sourceName("농사로 공식자료")
                        .sourceUrl(nongsaro.getSourceUrl()).actionLabel("농사로 공식자료 보기")
                        .dataDate(nongsaro.getDataDate()).sourceRefs(List.of(nongsaro)).build(),
                RegionReportResponseDto.TipDto.builder().rank(2).tipCode("SOIL_TEST_GUIDE")
                        .title("토양검정 결과 확인").summary("필지별 pH와 비료 처방은 토양검정 결과로 확인하세요.")
                        .reason("공식 토양검정 안내 참고").sourceType("OFFICIAL_GUIDE").sourceName("농촌진흥청 흙토람")
                        .sourceUrl(soil.getSourceUrl()).actionLabel("흙토람 보기")
                        .dataDate(soil.getDataDate()).sourceRefs(List.of(soil)).build());
    }

    private RegionReportResponseDto.SourceDto providerSource(String provider, String service, String url,
                                                              ExternalResult<?> result) {
        String fallback = result.isFailure() ? result.errorCode() : result.isEmpty() ? "NO_RECORDS" : null;
        return RegionReportResponseDto.SourceDto.builder().provider(provider).service(service).sourceUrl(url)
                .dataDate(LocalDate.now().toString()).status(result.status().name())
                .evidenceLevel(result.isSuccess() ? "PROVIDER_NORMALIZED" : "UNAVAILABLE")
                .isFallback(false).fallbackReason(fallback).build();
    }

    private List<RegionReportResponseDto.SourceDto> sourceRefs(List<String> refs) {
        if (refs == null) return List.of();
        return refs.stream().filter(this::hasText).map(ref -> RegionReportResponseDto.SourceDto.builder()
                .provider("기상청").service("단기예보").sourceUrl("https://www.weather.go.kr")
                .dataDate(ref.startsWith("forecast:") ? ref.substring("forecast:".length()) : null)
                .sourceRecordId(ref).evidenceLevel("FORECAST_EVIDENCE").build()).toList();
    }

    private RegionReportResponseDto.PeriodDto periodFor(List<String> refs) {
        List<String> dates = refs == null ? List.of() : refs.stream().filter(ref -> ref.startsWith("forecast:"))
                .map(ref -> ref.substring("forecast:".length())).sorted().toList();
        if (dates.isEmpty()) return null;
        return RegionReportResponseDto.PeriodDto.builder().start(dates.get(0)).end(dates.get(dates.size() - 1)).build();
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

    private String riskDescription(CropScoringEngine.RiskEvent risk) {
        if (risk.causalChain == null || risk.causalChain.isEmpty()) return "예보 기반 위험 조건이 감지되었습니다.";
        return String.join(" → ", risk.causalChain);
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
