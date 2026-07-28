package com.farmflate.service.analysis;

import com.farmflate.dto.region.RegionReportResponseDto;
import com.farmflate.integration.ShortForecastAdapter;
import com.farmflate.service.analysis.rules.CropRuleCatalog;
import com.farmflate.service.analysis.rules.CropSuitabilityEvaluator;
import com.farmflate.service.analysis.rules.ForecastRiskEvaluator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Deterministic crop decision engine.
 *
 * <p>The legacy region score remains available for compatibility, but callers
 * should consume {@link DecisionOutput}: base cultivation fitness is separated
 * from time-bound season readiness so a good long-term profile cannot average
 * away a critical forecast hazard.</p>
 */
@Component
public class CropScoringEngine {

    public static final String RULE_VERSION = "decision-engine-v2";
    public static final String PROFILE_SOURCE = "decision-engine-v2:mvp-hypothesis";
    public static final String PROFILE_REVIEW_STATUS = "MVP_HYPOTHESIS_PENDING_OFFICIAL_REVIEW";

    private final CropRuleCatalog ruleCatalog = new CropRuleCatalog();
    private final CropSuitabilityEvaluator suitabilityEvaluator = new CropSuitabilityEvaluator(ruleCatalog);
    private final ForecastRiskEvaluator forecastRiskEvaluator = new ForecastRiskEvaluator(ruleCatalog, suitabilityEvaluator);

    /**
     * Versioned public crop profile retained for callers that build or inspect
     * the engine's existing typed inputs. The immutable values now live in
     * {@link CropRuleCatalog}.
     */
    public static class CropProfile {
        public final String cropCode;
        public final String displayName;
        public final Double tempOptimalMin;
        public final Double tempOptimalMax;
        public final double tempCautionMargin;
        public final Double tempTarget;
        public final double phOptimalMin;
        public final double phOptimalMax;
        public final double phCautionMargin;
        public final double ecOptimalMax;
        public final double ecCautionMargin;
        public final double wSoilSuitability;
        public final double wSoilPh;
        public final double wSeasonalTemp;
        public final double wSoilEc;
        public final double wForecastRisk;
        public final boolean tempVerified;
        public final String sourceRef;
        public final String reviewStatus;

        public CropProfile(String cropCode, String displayName,
                           Double tempOptMin, Double tempOptMax, double tempCautionMargin, Double tempTarget,
                           double phOptMin, double phOptMax, double phCautionMargin,
                           double ecOptMax, double ecCautionMargin,
                           double wSuit, double wPh, double wTemp, double wEc, double wRisk,
                           boolean tempVerified) {
            this(cropCode, displayName, tempOptMin, tempOptMax, tempCautionMargin, tempTarget,
                    phOptMin, phOptMax, phCautionMargin, ecOptMax, ecCautionMargin,
                    wSuit, wPh, wTemp, wEc, wRisk,
                    tempVerified, PROFILE_SOURCE, PROFILE_REVIEW_STATUS);
        }

        public CropProfile(String cropCode, String displayName,
                           Double tempOptMin, Double tempOptMax, double tempCautionMargin, Double tempTarget,
                           double phOptMin, double phOptMax, double phCautionMargin,
                           double ecOptMax, double ecCautionMargin,
                           double wSuit, double wPh, double wTemp, double wEc, double wRisk,
                           boolean tempVerified, String sourceRef, String reviewStatus) {
            this.cropCode = cropCode;
            this.displayName = displayName;
            this.tempOptimalMin = tempOptMin;
            this.tempOptimalMax = tempOptMax;
            this.tempCautionMargin = tempCautionMargin;
            this.tempTarget = tempTarget;
            this.phOptimalMin = phOptMin;
            this.phOptimalMax = phOptMax;
            this.phCautionMargin = phCautionMargin;
            this.ecOptimalMax = ecOptMax;
            this.ecCautionMargin = ecCautionMargin;
            this.wSoilSuitability = wSuit;
            this.wSoilPh = wPh;
            this.wSeasonalTemp = wTemp;
            this.wSoilEc = wEc;
            this.wForecastRisk = wRisk;
            this.tempVerified = tempVerified;
            this.sourceRef = sourceRef;
            this.reviewStatus = reviewStatus;
        }
    }

    // ─── Input ────────────────────────────────────────────────────────────

    public static class AnalysisInput {
        public Double meanTemperature30d;
        public Double soilPh;
        /** Raw EC(전기전도도) reading; not used in scoring, only surfaced for display. */
        public Double soilEc;
        /** Compatibility input from the existing short-forecast adapter. */
        public int forecastRiskSafetyScore = 100;
        public Map<String, Double> soilSuitabilityScores = new HashMap<>();

        /** Near-term provider-normalized weather. */
        public List<ShortForecastAdapter.DailyForecast> shortForecasts = new ArrayList<>();
        /** Optional mid-term weather mapped by an adapter into a stable engine type. */
        public List<ForecastDay> midTermForecasts = new ArrayList<>();

        public String selectedCropCode;
        public String userEnteredGrowthStage;
        public Map<String, String> cropGrowthStages = new HashMap<>();
        public Map<String, GrowthStageResolver.GddEvidence> verifiedGddEvidence = new HashMap<>();

        /** 0..1 field/plot vulnerability; omitted values safely default to 1. */
        public double fieldVulnerability = 1.0;
        /** 0..1 exposure modifier; omitted values safely default to 1. */
        public double exposureModifier = 1.0;

        /**
         * Optional input quality by metric (0..1 or 0..100). Supported keys:
         * soilSuitability, soilPh, seasonalTemperature, forecast.
         */
        public Map<String, Double> dataQualityScores = new HashMap<>();
    }

    public static class ForecastDay {
        public String date;
        public Double minTemp;
        public Double maxTemp;
        public Double precipitation;
        public Double humidity;
        public Double windSpeed;

        public static ForecastDay fromShortForecast(ShortForecastAdapter.DailyForecast forecast) {
            ForecastDay day = new ForecastDay();
            day.date = forecast.date;
            day.minTemp = forecast.minTemp;
            day.maxTemp = forecast.maxTemp;
            day.precipitation = forecast.pcpTotal;
            day.humidity = forecast.rehAvg;
            day.windSpeed = forecast.wsdMax;
            return day;
        }
    }

    // ─── Typed canonical output ───────────────────────────────────────────

    public static class DataConfidence {
        public enum Level {
            HIGH,
            NORMAL,
            LOW,
            DECISION_LIMITED
        }

        public Level level;
        /**
         * Null for DECISION_LIMITED so downstream serialization cannot present
         * a falsely precise score.
         */
        public Integer score;
        public ScoreRange scoreRange;
        public int coverage;
        public int dataQuality;
        public List<String> missingInputs = new ArrayList<>();
        public String message;
    }

    public static class ScoreRange {
        public int min;
        public int max;

        public ScoreRange() {
        }

        public ScoreRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    public enum Severity {
        YELLOW,
        ORANGE,
        RED
    }

    public static class RiskEvent {
        public String code;
        public Severity severity;
        public double intensity;
        public int duration;
        public double stageSensitivity;
        public double fieldVulnerability;
        public double exposureModifier;
        public double confidenceModifier;
        public List<String> evidenceRefs = new ArrayList<>();
        public List<String> causalChain = new ArrayList<>();
        public int criticalCap;
        public double remainingRisk;
        public List<String> affectedCrops = new ArrayList<>();
    }

    public static class PrioritizedAction {
        public int rank;
        public String code;
        public String title;
        public String relatedRiskCode;
        public int priority;
        public int riskReduction;
        public int evidenceStrength;
        public int urgency;
        public int reversibility;
        public int effort;
        public int cost;
        public int leadTimeDays;
        public List<String> evidenceRefs = new ArrayList<>();
    }

    public static class EvidenceReceipt {
        public String ruleVersion;
        public List<String> profileSources = new ArrayList<>();
        public List<String> evidenceRefs = new ArrayList<>();
        public List<String> assumptions = new ArrayList<>();
    }

    public static class DecisionOutput {
        public Double baseFitness;
        public Integer seasonReadiness;
        public DataConfidence dataConfidence;
        public List<CropResult> cropResults = new ArrayList<>();
        public List<RiskEvent> riskEvents = new ArrayList<>();
        public List<PrioritizedAction> prioritizedActions = new ArrayList<>();
        public EvidenceReceipt evidenceReceipt;
        public String ruleVersion;
    }

    // ─── Legacy-compatible output ─────────────────────────────────────────

    public static class CropResult {
        public String cropCode;
        public String cropName;
        /** Legacy presentation score; now the deterministic base fitness. */
        public double totalScore;
        public Double baseFitness;
        public Integer seasonReadiness;
        public Integer baseCriticalCap;
        public Integer criticalRiskCap;
        public Double soilSuitabilityStatScore;
        public Double soilPhScore;
        public Double seasonalTemperatureScore;
        public int forecastRiskSafetyScore;
        public boolean calculable;
        public String notCalculableReason;
        public List<MetricContribution> contributions = new ArrayList<>();
        public List<RiskEvent> riskEvents = new ArrayList<>();
        public List<String> positiveReasons = new ArrayList<>();
        public String cautionReason;
    }

    public static class MetricContribution {
        public String metric;
        public Double score;
        public double weight;
        public double quality;
        public double effectiveWeight;
        /** Retained for existing clients that display a weighted contribution. */
        public double contribution;
    }

    public static class AnalysisOutput {
        public List<CropResult> allCropResults;
        public List<CropResult> topRecommended;
        /** Deprecated compatibility surface. */
        public int regionScore;
        /** Nullable compatibility field for consumers migrating off regionScore. */
        public Integer regionScoreCompatibility;
        public String regionGrade;
        public String regionSummary;
        public RegionReportResponseDto.ComponentsDto components;
        public RegionReportResponseDto.ConfidenceDto confidence;
        public DecisionOutput decisionOutput;
    }

    public static class ForecastRiskResult {
        public int safetyScore;
        public List<RegionReportResponseDto.RiskDto> risks = new ArrayList<>();
        public int totalPenalty;
    }

    // ─── Legacy forecast risk adapter ──────────────────────────────────────

    /**
     * Retains the current RegionAnalysisService boundary.  The richer,
     * explainable risk events are produced by {@link #analyze(AnalysisInput)}.
     */
    public ForecastRiskResult calculateForecastRisks(List<ShortForecastAdapter.DailyForecast> forecasts) {
        return forecastRiskEvaluator.calculateLegacyForecastRisks(forecasts);
    }

    // ─── Deterministic decision engine ─────────────────────────────────────    // ─── Deterministic decision engine ─────────────────────────────────────

    public AnalysisOutput analyze(AnalysisInput requestedInput) {
        AnalysisInput input = requestedInput == null ? new AnalysisInput() : requestedInput;
        ensureCollections(input);

        DataConfidence confidence = calculateDataConfidence(input);
        List<CropResult> allResults = new ArrayList<>();
        for (CropProfile profile : ruleCatalog.profiles()) {
            allResults.add(suitabilityEvaluator.evaluate(profile, input));
        }

        List<ForecastDay> forecastDays = forecastRiskEvaluator.collectForecastDays(input);
        List<RiskEvent> riskEvents = forecastRiskEvaluator.evaluate(input, forecastDays);
        for (CropResult crop : allResults) {
            crop.riskEvents = riskEvents.stream()
                    .filter(event -> event.affectedCrops.contains(crop.cropCode))
                    .collect(Collectors.toList());
            crop.criticalRiskCap = crop.riskEvents.stream()
                    .map(event -> event.criticalCap)
                    .min(Integer::compareTo)
                    .orElse(null);
            crop.seasonReadiness = crop.calculable
                    ? forecastRiskEvaluator.calculateSeasonReadiness(crop, crop.riskEvents, forecastDays)
                    : null;
        }

        List<CropResult> topRecommended = allResults.stream()
                .filter(crop -> crop.calculable)
                .sorted(suitabilityEvaluator::compare)
                .limit(3)
                .collect(Collectors.toList());

        DecisionOutput decision = new DecisionOutput();
        decision.ruleVersion = RULE_VERSION;
        decision.cropResults = allResults;
        decision.riskEvents = riskEvents;
        decision.dataConfidence = confidence;
        decision.prioritizedActions = forecastRiskEvaluator.buildPrioritizedActions(riskEvents);
        decision.evidenceReceipt = buildEvidenceReceipt(input, forecastDays, riskEvents);
        if (!topRecommended.isEmpty()) {
            CropResult canonicalCrop = topRecommended.get(0);
            decision.baseFitness = canonicalCrop.baseFitness;
            decision.seasonReadiness = canonicalCrop.seasonReadiness;
        }

        AnalysisOutput output = new AnalysisOutput();
        output.allCropResults = allResults;
        output.topRecommended = topRecommended;
        output.decisionOutput = decision;
        if (!topRecommended.isEmpty()) {
            output.regionScore = (int) Math.round(topRecommended.stream()
                    .mapToDouble(crop -> crop.totalScore)
                    .average()
                    .orElse(0));
            output.regionScoreCompatibility = output.regionScore;
        } else {
            output.regionScore = 0;
            output.regionScoreCompatibility = null;
        }
        output.regionGrade = gradeFromScore(output.regionScore);
        output.regionSummary = regionSummaryText(output.regionScore);
        int legacyForecastSafety = forecastDays.isEmpty()
                ? clampInt(input.forecastRiskSafetyScore, 0, 100)
                : forecastRiskEvaluator.calculateLegacySafety(riskEvents);
        output.components = buildComponents(topRecommended, legacyForecastSafety, input.soilPh, input.soilEc);
        output.confidence = toLegacyConfidence(confidence);
        return output;
    }

    // Crop-level base fitness is evaluated by CropSuitabilityEvaluator.

    // Forecast risk, seasonal readiness, and mitigation action rules are evaluated by ForecastRiskEvaluator.

    // ─── Confidence and evidence ───────────────────────────────────────────

    private DataConfidence calculateDataConfidence(AnalysisInput input) {
        List<ConfidenceMetric> metrics = List.of(
                new ConfidenceMetric("soilSuitability", 0.45,
                        input.soilSuitabilityScores.values().stream().anyMatch(Objects::nonNull), 0.80),
                new ConfidenceMetric("soilPh", 0.15, suitabilityEvaluator.sanitizePh(input.soilPh) != null, 0.80),
                new ConfidenceMetric("seasonalTemperature", 0.20, input.meanTemperature30d != null, 0.90),
                new ConfidenceMetric("soilEc", 0.10, suitabilityEvaluator.sanitizeEc(input.soilEc) != null, 0.75),
                new ConfidenceMetric("forecast", 0.10,
                        !(input.shortForecasts.isEmpty() && input.midTermForecasts.isEmpty()), 1.00)
        );

        double coverageWeight = 0;
        double qualityWeight = 0;
        double availableWeight = 0;
        DataConfidence confidence = new DataConfidence();
        for (ConfidenceMetric metric : metrics) {
            if (metric.available) {
                coverageWeight += metric.weight;
                availableWeight += metric.weight;
                qualityWeight += metric.weight * suitabilityEvaluator.qualityFor(input, metric.key, metric.defaultQuality);
            } else {
                confidence.missingInputs.add(metric.key);
            }
        }
        confidence.coverage = (int) Math.round(coverageWeight * 100);
        confidence.dataQuality = availableWeight == 0 ? 0 : (int) Math.round(qualityWeight / availableWeight * 100);
        int estimated = (int) Math.round((confidence.coverage + confidence.dataQuality) / 2.0);
        if (estimated >= 85) {
            confidence.level = DataConfidence.Level.HIGH;
            confidence.score = estimated;
            confidence.message = "입력 범위와 자료 품질이 충분하여 판단 근거가 양호합니다.";
        } else if (estimated >= 65) {
            confidence.level = DataConfidence.Level.NORMAL;
            confidence.score = estimated;
            confidence.message = "일부 자료 품질 가정이 포함된 일반 수준의 판단 근거입니다.";
        } else if (estimated >= 50) {
            confidence.level = DataConfidence.Level.LOW;
            confidence.score = estimated;
            confidence.message = "입력 범위 또는 자료 품질이 제한되어 현장 확인이 필요합니다.";
        } else {
            confidence.level = DataConfidence.Level.DECISION_LIMITED;
            confidence.score = null;
            confidence.scoreRange = new ScoreRange(Math.max(0, estimated - 10), Math.min(49, estimated + 10));
            confidence.message = "자료 범위와 품질이 제한되어 단일 신뢰도 점수를 제시하지 않습니다.";
        }
        return confidence;
    }

    private RegionReportResponseDto.ConfidenceDto toLegacyConfidence(DataConfidence confidence) {
        String message = confidence.message;
        if (confidence.scoreRange != null) {
            message += " 신뢰도 범위: " + confidence.scoreRange.min + "–" + confidence.scoreRange.max + ".";
        }
        return RegionReportResponseDto.ConfidenceDto.builder()
                .score(confidence.score)
                .grade(confidence.level.name())
                .message(message)
                .build();
    }

    private EvidenceReceipt buildEvidenceReceipt(AnalysisInput input, List<ForecastDay> days,
                                                 List<RiskEvent> riskEvents) {
        EvidenceReceipt receipt = new EvidenceReceipt();
        receipt.ruleVersion = RULE_VERSION;
        receipt.profileSources = ruleCatalog.profiles().stream()
                .map(profile -> profile.cropCode + ":" + profile.sourceRef + ":" + profile.reviewStatus)
                .collect(Collectors.toList());
        receipt.evidenceRefs = days.stream()
                .filter(day -> hasText(day.date))
                .map(day -> "forecast:" + day.date)
                .collect(Collectors.toList());
        for (String cropCode : ruleCatalog.supportedCropCodes()) {
            String stage = forecastRiskEvaluator.stageFor(input, cropCode);
            if (!"UNSPECIFIED".equals(stage)) {
                receipt.evidenceRefs.add("growth-stage:" + cropCode + ":" + stage);
            }
        }
        if (riskEvents.isEmpty()) {
            receipt.assumptions.add("No risk event was inferred when a required forecast variable was absent.");
        }
        receipt.assumptions.add("Crop coefficients are versioned MVP hypotheses pending official-source review.");
        return receipt;
    }

    private record ConfidenceMetric(String key, double weight, boolean available, double defaultQuality) {
    }

    private void ensureCollections(AnalysisInput input) {
        if (input.soilSuitabilityScores == null) input.soilSuitabilityScores = new HashMap<>();
        if (input.shortForecasts == null) input.shortForecasts = new ArrayList<>();
        if (input.midTermForecasts == null) input.midTermForecasts = new ArrayList<>();
        if (input.cropGrowthStages == null) input.cropGrowthStages = new HashMap<>();
        if (input.verifiedGddEvidence == null) input.verifiedGddEvidence = new HashMap<>();
        if (input.dataQualityScores == null) input.dataQualityScores = new HashMap<>();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int clampInt(int value, int lower, int upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private RegionReportResponseDto.ComponentsDto buildComponents(List<CropResult> topCrops, int forecastSafety,
                                                                    Double soilPh, Double soilEc) {
        if (topCrops.isEmpty()) {
            return null;
        }
        OptionalDouble climateAverage = topCrops.stream()
                .filter(crop -> crop.seasonalTemperatureScore != null)
                .mapToDouble(crop -> crop.seasonalTemperatureScore)
                .average();
        List<Double> soilScores = new ArrayList<>();
        for (CropResult crop : topCrops) {
            double suitability = crop.soilSuitabilityStatScore == null ? 0 : crop.soilSuitabilityStatScore;
            soilScores.add(crop.soilPhScore == null ? suitability : 0.70 * suitability + 0.30 * crop.soilPhScore);
        }
        double soilAverage = soilScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double cultivationAverage = topCrops.stream()
                .filter(crop -> crop.soilSuitabilityStatScore != null)
                .mapToDouble(crop -> crop.soilSuitabilityStatScore)
                .average().orElse(0);
        return RegionReportResponseDto.ComponentsDto.builder()
                .climate(RegionReportResponseDto.ComponentDetailDto.builder()
                        .score(climateAverage.isPresent() ? (int) Math.round(climateAverage.getAsDouble()) : null)
                        .grade(climateAverage.isPresent() ? gradeFromScore((int) Math.round(climateAverage.getAsDouble())) : null)
                        .build())
                .soil(RegionReportResponseDto.ComponentDetailDto.builder()
                        .score((int) Math.round(soilAverage))
                        .grade(gradeFromScore((int) Math.round(soilAverage)))
                        .soilPh(soilPh)
                        .soilEc(soilEc)
                        .build())
                .hazard(RegionReportResponseDto.HazardComponentDetailDto.builder()
                        .safetyScore(forecastSafety)
                        .grade(forecastSafety >= 80 ? "GOOD" : forecastSafety >= 60 ? "CAUTION" : "DANGER")
                        .build())
                .cultivation(RegionReportResponseDto.ComponentDetailDto.builder()
                        .score((int) Math.round(cultivationAverage))
                        .grade(gradeFromScore((int) Math.round(cultivationAverage)))
                        .build())
                .build();
    }

    public String gradeFromScore(int score) {
        return RegionScoreBand.classify(score).grade();
    }

    private String regionSummaryText(int score) {
        return RegionScoreBand.classify(score).summary();
    }

}
