package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
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

    private static final List<String> TIEBREAK_ORDER =
            List.of("APPLE", "PEAR", "CUCUMBER", "POTATO", "LETTUCE");
    private static final List<String> SUPPORTED_CROPS =
            List.of("APPLE", "PEAR", "CUCUMBER", "POTATO", "LETTUCE");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * Versioned crop profile.  Coefficients are intentionally labelled as MVP
     * hypotheses until an official crop-standard citation is attached.
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
        /** Upper bound of non-saline soil EC(dS/m); crops differ in salinity tolerance. */
        public final double ecOptimalMax;
        public final double ecCautionMargin;
        public final double wSoilSuitability;
        public final double wSoilPh;
        public final double wSeasonalTemp;
        public final double wSoilEc;
        /** Retained for the legacy score surface; base fitness does not use it. */
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

    /**
     * Per-crop weights are intentionally NOT uniform: tree fruit (사과/배) leans on
     * soil suitability and seasonal temperature (frost/chilling risk over a long
     * root life), while shallow-rooted leafy/vine crops (상추/오이) are weighted
     * more heavily on soil EC(염류 농도) and temperature since they show salinity
     * and heat/cold stress far faster. 감자 leans on soil suitability (tuber
     * formation needs loose, well-drained soil) and tolerates EC/pH swings more
     * than the others. These relative weightings are MVP hypotheses (see
     * {@link #PROFILE_REVIEW_STATUS}), not an official agronomic citation.
     */
    private static final List<CropProfile> PROFILES = List.of(
            new CropProfile("APPLE", "사과", 18.0, 24.0, 5.0, null,
                    5.8, 6.3, 0.5, 1.5, 0.5, 0.45, 0.20, 0.25, 0.10, 0.10, true),
            new CropProfile("PEAR", "배", null, null, 5.0, 20.0,
                    5.5, 6.5, 0.5, 1.5, 0.5, 0.45, 0.20, 0.25, 0.10, 0.10, true),
            new CropProfile("CUCUMBER", "오이", 20.0, 25.0, 5.0, null,
                    5.5, 6.8, 0.5, 1.2, 0.4, 0.35, 0.15, 0.30, 0.20, 0.10, true),
            // 생육적온 15~21°C: 농촌진흥청 감자 표준영농교본 기준(MVP 가설, 공식 인용 검증 대기).
            new CropProfile("POTATO", "감자", 15.0, 21.0, 5.0, null,
                    5.0, 6.0, 0.5, 1.8, 0.5, 0.50, 0.15, 0.20, 0.15, 0.10, true),
            new CropProfile("LETTUCE", "상추", 15.0, 20.0, 5.0, null,
                    6.6, 7.2, 0.5, 1.0, 0.3, 0.30, 0.20, 0.25, 0.25, 0.10, true)
    );

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
        ForecastRiskResult result = new ForecastRiskResult();
        if (forecasts == null || forecasts.isEmpty()) {
            result.safetyScore = 100;
            return result;
        }

        int penalty = 0;
        Set<String> seenRisks = new HashSet<>();
        for (ShortForecastAdapter.DailyForecast day : forecasts) {
            if (day == null) {
                continue;
            }
            String date = hasText(day.date) ? day.date : "UNSPECIFIED";
            if (day.maxTemp != null && day.maxTemp >= 33 && seenRisks.add("HEAT")) {
                boolean danger = day.maxTemp >= 35;
                penalty += danger ? 20 : 10;
                result.risks.add(legacyRisk(result.risks.size() + 1, "HEAT", danger,
                        danger ? "폭염 위험" : "폭염 주의",
                        "최고기온이 " + Math.round(day.maxTemp) + "°C로 예보되어 차광 및 수분 관리가 필요해요.",
                        List.of("LETTUCE", "CUCUMBER", "POTATO"),
                        List.of("차광막을 설치하고 수분을 충분히 공급하세요."), date));
            }
            if (day.minTemp != null && day.minTemp <= 5 && seenRisks.add("COLD")) {
                boolean danger = day.minTemp <= 0;
                penalty += danger ? 20 : 10;
                result.risks.add(legacyRisk(result.risks.size() + 1, "COLD", danger,
                        danger ? "저온 위험" : "저온 주의",
                        "최저기온이 " + Math.round(day.minTemp) + "°C로 예보되어 보온 관리가 필요해요.",
                        List.of("CUCUMBER", "LETTUCE", "POTATO"),
                        List.of("보온 덮개와 보온재를 점검하세요."), date));
            }
            if (day.pcpTotal != null && day.pcpTotal >= 30 && seenRisks.add("HEAVY_RAIN")) {
                boolean danger = day.pcpTotal >= 50;
                penalty += danger ? 20 : 10;
                result.risks.add(legacyRisk(result.risks.size() + 1, "HEAVY_RAIN", danger,
                        danger ? "집중호우 위험" : "호우 주의",
                        "24시간 강수량 " + Math.round(day.pcpTotal) + "mm가 예상되어 배수 확인이 필요해요.",
                        List.of("POTATO", "LETTUCE", "CUCUMBER"),
                        List.of("밭 주변 배수로가 막히지 않았는지 확인하세요."), date));
            }
            if (day.wsdMax != null && day.wsdMax >= 9 && seenRisks.add("WIND")) {
                boolean danger = day.wsdMax >= 14;
                penalty += danger ? 20 : 10;
                result.risks.add(legacyRisk(result.risks.size() + 1, "WIND", danger,
                        danger ? "강풍 위험" : "강풍 주의",
                        "최대풍속 " + String.format(Locale.ROOT, "%.1f", day.wsdMax) + "m/s가 예상되어 지지대 점검이 필요해요.",
                        List.of("CUCUMBER", "APPLE", "PEAR"),
                        List.of("작물 지지대와 시설을 점검하세요."), date));
            }
        }
        result.totalPenalty = Math.min(40, penalty);
        result.safetyScore = Math.max(0, 100 - result.totalPenalty);
        result.risks = result.risks.stream().limit(3).collect(Collectors.toList());
        for (int i = 0; i < result.risks.size(); i++) {
            RegionReportResponseDto.RiskDto risk = result.risks.get(i);
            result.risks.set(i, RegionReportResponseDto.RiskDto.builder()
                    .rank(i + 1)
                    .riskCode(risk.getRiskCode())
                    .level(risk.getLevel())
                    .title(risk.getTitle())
                    .description(risk.getDescription())
                    .affectedCrops(risk.getAffectedCrops())
                    .actions(risk.getActions())
                    .source(risk.getSource())
                    .build());
        }
        return result;
    }

    private RegionReportResponseDto.RiskDto legacyRisk(
            int rank, String code, boolean danger, String title, String description,
            List<String> affectedCrops, List<String> actions, String date) {
        return RegionReportResponseDto.RiskDto.builder()
                .rank(rank)
                .riskCode(code)
                .level(danger ? "DANGER" : "CAUTION")
                .title(title)
                .description(description)
                .affectedCrops(affectedCrops)
                .actions(actions)
                .source(weatherSource(date))
                .build();
    }

    // ─── Deterministic decision engine ─────────────────────────────────────

    public AnalysisOutput analyze(AnalysisInput requestedInput) {
        AnalysisInput input = requestedInput == null ? new AnalysisInput() : requestedInput;
        ensureCollections(input);

        DataConfidence confidence = calculateDataConfidence(input);
        List<CropResult> allResults = new ArrayList<>();
        for (CropProfile profile : PROFILES) {
            allResults.add(scoreCrop(profile, input));
        }

        List<ForecastDay> forecastDays = collectForecastDays(input);
        List<RiskEvent> riskEvents = evaluateRiskEvents(input, forecastDays);
        for (CropResult crop : allResults) {
            crop.riskEvents = riskEvents.stream()
                    .filter(event -> event.affectedCrops.contains(crop.cropCode))
                    .collect(Collectors.toList());
            crop.criticalRiskCap = crop.riskEvents.stream()
                    .map(event -> event.criticalCap)
                    .min(Integer::compareTo)
                    .orElse(null);
            crop.seasonReadiness = crop.calculable
                    ? calculateSeasonReadiness(crop, crop.riskEvents, forecastDays)
                    : null;
        }

        List<CropResult> topRecommended = allResults.stream()
                .filter(crop -> crop.calculable)
                .sorted(this::compareCropResults)
                .limit(3)
                .collect(Collectors.toList());

        DecisionOutput decision = new DecisionOutput();
        decision.ruleVersion = RULE_VERSION;
        decision.cropResults = allResults;
        decision.riskEvents = riskEvents;
        decision.dataConfidence = confidence;
        decision.prioritizedActions = buildPrioritizedActions(riskEvents);
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
                : calculateLegacySafety(riskEvents);
        output.components = buildComponents(topRecommended, legacyForecastSafety, input.soilPh, input.soilEc);
        output.confidence = toLegacyConfidence(confidence);
        return output;
    }

    /**
     * Base cultivation fitness is a quality-weighted geometric mean of valid,
     * available variables.  Missing values are excluded; they never become a
     * zero score.  Explicit critical environmental conditions cap the result
     * after the mean is calculated.
     */
    private CropResult scoreCrop(CropProfile profile, AnalysisInput input) {
        CropResult crop = new CropResult();
        crop.cropCode = profile.cropCode;
        crop.cropName = profile.displayName;
        crop.forecastRiskSafetyScore = clampInt(input.forecastRiskSafetyScore, 0, 100);

        Double suitability = validScore(input.soilSuitabilityScores.get(profile.cropCode));
        Double sanitizedPh = sanitizePh(input.soilPh);
        Double sanitizedEc = sanitizeEc(input.soilEc);
        Double ph = scoreRange(sanitizedPh, profile.phOptimalMin, profile.phOptimalMax, profile.phCautionMargin);
        Double temperature = temperatureScore(profile, input.meanTemperature30d);
        Double ec = scoreUpperBound(sanitizedEc, profile.ecOptimalMax, profile.ecCautionMargin);
        crop.soilSuitabilityStatScore = suitability;
        crop.soilPhScore = ph;
        crop.seasonalTemperatureScore = temperature;

        List<MetricContribution> contributions = new ArrayList<>();
        addContribution(contributions, "soilSuitabilityStat", suitability, profile.wSoilSuitability,
                qualityFor(input, "soilSuitability", 0.80));
        addContribution(contributions, "soilPh", ph, profile.wSoilPh,
                qualityFor(input, "soilPh", 0.80));
        addContribution(contributions, "seasonalTemperature", temperature, profile.wSeasonalTemp,
                qualityFor(input, "seasonalTemperature", 0.90));
        addContribution(contributions, "soilEc", ec, profile.wSoilEc,
                qualityFor(input, "soilEc", 0.75));
        crop.contributions = contributions;

        boolean hasSuitability = suitability != null;
        boolean hasTemperatureOrPh = temperature != null || ph != null;
        if (!hasSuitability || !hasTemperatureOrPh) {
            crop.calculable = false;
            crop.totalScore = 0;
            crop.notCalculableReason = "필수 데이터(토양적성 통계 및 기온 또는 pH)가 부족합니다.";
            return crop;
        }

        double effectiveWeight = contributions.stream().mapToDouble(contribution -> contribution.effectiveWeight).sum();
        double logarithmicSum = contributions.stream()
                .mapToDouble(contribution -> contribution.effectiveWeight
                        * Math.log(Math.max(0.01, contribution.score) / 100.0))
                .sum();
        double baseFitness = effectiveWeight == 0 ? 0 : 100.0 * Math.exp(logarithmicSum / effectiveWeight);
        crop.baseCriticalCap = criticalBaseCap(profile, input);
        if (crop.baseCriticalCap != null) {
            baseFitness = Math.min(baseFitness, crop.baseCriticalCap);
        }
        crop.baseFitness = round2(clamp(baseFitness, 0, 100));
        crop.totalScore = crop.baseFitness;
        crop.calculable = true;
        generateReasons(crop, contributions);
        return crop;
    }

    private void addContribution(List<MetricContribution> contributions, String metric,
                                 Double score, double weight, double quality) {
        if (score == null || weight <= 0) {
            return;
        }
        MetricContribution contribution = new MetricContribution();
        contribution.metric = metric;
        contribution.score = score;
        contribution.weight = weight;
        contribution.quality = quality;
        contribution.effectiveWeight = weight * Math.max(0.05, quality);
        contribution.contribution = weight * score;
        contributions.add(contribution);
    }

    private Double temperatureScore(CropProfile profile, Double temperature) {
        if (profile.tempTarget != null) {
            return scoreTarget(temperature, profile.tempTarget, 2.0, profile.tempCautionMargin);
        }
        if (profile.tempVerified && profile.tempOptimalMin != null && profile.tempOptimalMax != null) {
            return scoreRange(temperature, profile.tempOptimalMin, profile.tempOptimalMax, profile.tempCautionMargin);
        }
        return null;
    }

    private Integer criticalBaseCap(CropProfile profile, AnalysisInput input) {
        Double ph = sanitizePh(input.soilPh);
        Double ec = sanitizeEc(input.soilEc);
        boolean criticalPh = ph != null
                && (ph < profile.phOptimalMin - 1.5 || ph > profile.phOptimalMax + 1.5);
        boolean criticalEc = ec != null && ec > profile.ecOptimalMax + profile.ecCautionMargin * 3;
        boolean criticalTemperature = false;
        if (input.meanTemperature30d != null && profile.tempOptimalMin != null && profile.tempOptimalMax != null) {
            criticalTemperature = input.meanTemperature30d < profile.tempOptimalMin - 8.0
                    || input.meanTemperature30d > profile.tempOptimalMax + 8.0;
        }
        if (input.meanTemperature30d != null && profile.tempTarget != null) {
            criticalTemperature = Math.abs(input.meanTemperature30d - profile.tempTarget) > 10.0;
        }
        return criticalPh || criticalEc || criticalTemperature ? 49 : null;
    }

    private int compareCropResults(CropResult first, CropResult second) {
        int score = Double.compare(second.totalScore, first.totalScore);
        if (score != 0) {
            return score;
        }
        score = Double.compare(
                second.soilSuitabilityStatScore != null ? second.soilSuitabilityStatScore : 0,
                first.soilSuitabilityStatScore != null ? first.soilSuitabilityStatScore : 0);
        if (score != 0) {
            return score;
        }
        score = Double.compare(
                second.seasonalTemperatureScore != null ? second.seasonalTemperatureScore : 0,
                first.seasonalTemperatureScore != null ? first.seasonalTemperatureScore : 0);
        if (score != 0) {
            return score;
        }
        return Integer.compare(TIEBREAK_ORDER.indexOf(first.cropCode), TIEBREAK_ORDER.indexOf(second.cropCode));
    }

    // ─── Risk rules ────────────────────────────────────────────────────────

    private List<RiskEvent> evaluateRiskEvents(AnalysisInput input, List<ForecastDay> days) {
        if (days.isEmpty()) {
            return List.of();
        }
        List<RiskEvent> events = new ArrayList<>();
        double fieldVulnerability = unitOrDefault(input.fieldVulnerability, 1.0);
        double exposure = unitOrDefault(input.exposureModifier, 1.0);
        double confidence = qualityFor(input, "forecast", 1.0);

        Double hottest = maximum(days, day -> day.maxTemp);
        int heatDays = count(days, day -> day.maxTemp != null && day.maxTemp >= 33);
        if (hottest != null && hottest >= 33) {
            addRisk(events, "HEAT", normalized(hottest, 32, 40), heatDays, 0.75,
                    fieldVulnerability, exposure, confidence, SUPPORTED_CROPS,
                    evidenceFor(days, day -> day.maxTemp != null && day.maxTemp >= 33),
                    List.of("high maximum temperature", "heat stress exposure"));
        }

        Double coldest = minimum(days, day -> day.minTemp);
        int coldDays = count(days, day -> day.minTemp != null && day.minTemp <= 5);
        if (coldest != null && coldest <= 5) {
            addRisk(events, "COLD_FROST", normalized(6 - coldest, 0, 10), coldDays, 0.80,
                    fieldVulnerability, exposure, confidence, SUPPORTED_CROPS,
                    evidenceFor(days, day -> day.minTemp != null && day.minTemp <= 5),
                    List.of("low minimum temperature", "cold or frost exposure"));
        }

        Double peakRain = maximum(days, day -> day.precipitation);
        int heavyRainDays = count(days, day -> day.precipitation != null && day.precipitation >= 30);
        if (peakRain != null && peakRain >= 30) {
            addRisk(events, "CONCENTRATED_RAIN", normalized(peakRain, 20, 100), heavyRainDays, 0.75,
                    fieldVulnerability, exposure, confidence,
                    List.of("POTATO", "LETTUCE", "CUCUMBER", "PEAR"),
                    evidenceFor(days, day -> day.precipitation != null && day.precipitation >= 30),
                    List.of("concentrated rainfall", "surface drainage load"));
        }

        double accumulatedRain = sum(days, day -> day.precipitation);
        if (accumulatedRain >= 60) {
            addRisk(events, "WATERLOGGING", normalized(accumulatedRain, 40, 140),
                    count(days, day -> day.precipitation != null && day.precipitation >= 20), 0.85,
                    fieldVulnerability, exposure, confidence,
                    List.of("POTATO", "LETTUCE", "CUCUMBER"),
                    evidenceFor(days, day -> day.precipitation != null && day.precipitation >= 20),
                    List.of("accumulated rainfall", "field drainage vulnerability", "waterlogging exposure"));
        }

        Double highestWind = maximum(days, day -> day.windSpeed);
        int windDays = count(days, day -> day.windSpeed != null && day.windSpeed >= 9);
        if (highestWind != null && highestWind >= 9) {
            addRisk(events, "WIND", normalized(highestWind, 7, 19), windDays, 0.70,
                    fieldVulnerability, exposure, confidence,
                    List.of("CUCUMBER", "APPLE", "PEAR"),
                    evidenceFor(days, day -> day.windSpeed != null && day.windSpeed >= 9),
                    List.of("strong wind", "crop and structure exposure"));
        }

        int dryDays = count(days, day -> day.maxTemp != null && day.maxTemp >= 28
                && day.precipitation != null && day.precipitation <= 1);
        if (dryDays >= 3) {
            addRisk(events, "DROUGHT", normalized(dryDays, 2, 6), dryDays, 0.70,
                    fieldVulnerability, exposure, confidence,
                    List.of("POTATO", "LETTUCE", "CUCUMBER"),
                    evidenceFor(days, day -> day.maxTemp != null && day.maxTemp >= 28
                            && day.precipitation != null && day.precipitation <= 1),
                    List.of("warm dry sequence", "soil moisture depletion exposure"));
        }

        Double averageHumidity = average(days, day -> day.humidity);
        int humidDays = count(days, day -> day.humidity != null && day.humidity >= 85);
        if (averageHumidity != null && averageHumidity >= 85 && humidDays > 0) {
            addRisk(events, "HIGH_HUMIDITY", normalized(averageHumidity, 80, 100), humidDays, 0.70,
                    fieldVulnerability, exposure, confidence,
                    List.of("LETTUCE", "CUCUMBER", "PEAR"),
                    evidenceFor(days, day -> day.humidity != null && day.humidity >= 85),
                    List.of("high relative humidity", "reduced evaporation and disease-pressure exposure"));
        }

        addCompoundRisks(events, input, days, fieldVulnerability, exposure, confidence,
                coldest, peakRain, hottest, averageHumidity);
        return events.stream()
                .sorted(Comparator.comparing((RiskEvent event) -> event.severity).reversed()
                        .thenComparing((RiskEvent event) -> event.remainingRisk, Comparator.reverseOrder())
                        .thenComparing(event -> event.code))
                .collect(Collectors.toList());
    }

    private void addCompoundRisks(List<RiskEvent> events, AnalysisInput input, List<ForecastDay> days,
                                  double fieldVulnerability, double exposure, double confidence,
                                  Double coldest, Double peakRain, Double hottest, Double averageHumidity) {
        String pearStage = stageFor(input, "PEAR");
        if (isStage(pearStage, "BLOSSOM", "FLOWERING") && coldest != null && coldest <= 2) {
            addRisk(events, "PEAR_BLOSSOM_FROST", normalized(2 - coldest, 0, 4),
                    count(days, day -> day.minTemp != null && day.minTemp <= 2), 1.0,
                    fieldVulnerability, exposure, confidence, List.of("PEAR"),
                    evidenceFor(days, day -> day.minTemp != null && day.minTemp <= 2),
                    List.of("pear blossom stage", "sub-2°C forecast minimum", "blossom frost hazard"));
        }

        if (peakRain != null && peakRain >= 50) {
            addRisk(events, "POTATO_WATERLOGGING", normalized(peakRain, 40, 100),
                    count(days, day -> day.precipitation != null && day.precipitation >= 30), 1.0,
                    fieldVulnerability, exposure, confidence, List.of("POTATO"),
                    evidenceFor(days, day -> day.precipitation != null && day.precipitation >= 30),
                    List.of("potato field", "concentrated rainfall", "waterlogging susceptibility"));
        }

        String cucumberStage = stageFor(input, "CUCUMBER");
        if (isStage(cucumberStage, "POST_TRANSPLANT", "TRANSPLANT", "POST-TRANSPLANT")
                && coldest != null && coldest <= 10) {
            addRisk(events, "CUCUMBER_POST_TRANSPLANT_NIGHT_COLD", normalized(12 - coldest, 0, 8),
                    count(days, day -> day.minTemp != null && day.minTemp <= 10), 1.0,
                    fieldVulnerability, exposure, confidence, List.of("CUCUMBER"),
                    evidenceFor(days, day -> day.minTemp != null && day.minTemp <= 10),
                    List.of("cucumber post-transplant stage", "cold night forecast", "transplant establishment stress"));
        }

        if (hottest != null && hottest >= 30 && averageHumidity != null && averageHumidity >= 85) {
            addRisk(events, "LETTUCE_HEAT_HUMIDITY",
                    Math.min(normalized(hottest, 28, 36), normalized(averageHumidity, 80, 95)),
                    count(days, day -> day.maxTemp != null && day.maxTemp >= 30
                            && day.humidity != null && day.humidity >= 85), 1.0,
                    fieldVulnerability, exposure, confidence, List.of("LETTUCE"),
                    evidenceFor(days, day -> day.maxTemp != null && day.maxTemp >= 30
                            && day.humidity != null && day.humidity >= 85),
                    List.of("lettuce heat exposure", "high humidity", "combined heat-humidity stress"));
        }
    }

    private void addRisk(List<RiskEvent> events, String code, double intensity, int duration,
                         double stageSensitivity, double fieldVulnerability, double exposure,
                         double confidence, List<String> crops, List<String> evidence,
                         List<String> causalChain) {
        if (duration <= 0) {
            return;
        }
        double index = clamp(intensity, 0, 1)
                * Math.min(1.0, duration)
                * clamp(stageSensitivity, 0, 1)
                * clamp(fieldVulnerability, 0, 1)
                * clamp(exposure, 0, 1)
                * clamp(confidence, 0, 1);
        Severity severity = severityFor(index);
        if (severity == null) {
            return;
        }
        RiskEvent event = new RiskEvent();
        event.code = code;
        event.severity = severity;
        event.intensity = round2(clamp(intensity, 0, 1));
        event.duration = duration;
        event.stageSensitivity = round2(stageSensitivity);
        event.fieldVulnerability = round2(fieldVulnerability);
        event.exposureModifier = round2(exposure);
        event.confidenceModifier = round2(confidence);
        event.evidenceRefs = evidence;
        event.causalChain = causalChain;
        event.criticalCap = capFor(severity);
        event.remainingRisk = round2(index * 100);
        event.affectedCrops = new ArrayList<>(crops);
        events.add(event);
    }

    private Severity severityFor(double index) {
        if (index >= 0.70) {
            return Severity.RED;
        }
        if (index >= 0.40) {
            return Severity.ORANGE;
        }
        if (index >= 0.18) {
            return Severity.YELLOW;
        }
        return null;
    }

    private int capFor(Severity severity) {
        return switch (severity) {
            case YELLOW -> 84;
            case ORANGE -> 69;
            case RED -> 49;
        };
    }

    private int calculateSeasonReadiness(CropResult crop, List<RiskEvent> cropEvents, List<ForecastDay> days) {
        double base = crop.baseFitness == null ? 0 : crop.baseFitness;
        double weatherReadiness = forecastReadiness(days);
        double riskPenalty = cropEvents.stream()
                .mapToDouble(event -> event.remainingRisk * severityPenaltyWeight(event.severity))
                .sum();
        double raw = clamp(0.65 * base + 0.35 * weatherReadiness - Math.min(35, riskPenalty), 0, 100);
        int cap = cropEvents.stream().map(event -> event.criticalCap).min(Integer::compareTo).orElse(100);
        return (int) Math.round(Math.min(raw, cap));
    }

    private double severityPenaltyWeight(Severity severity) {
        return switch (severity) {
            case YELLOW -> 0.05;
            case ORANGE -> 0.09;
            case RED -> 0.12;
        };
    }

    private double forecastReadiness(List<ForecastDay> days) {
        if (days.isEmpty()) {
            return 70;
        }
        double total = 0;
        int knownDays = 0;
        for (ForecastDay day : days) {
            double score = 100;
            boolean hasWeather = false;
            if (day.maxTemp != null) {
                hasWeather = true;
                if (day.maxTemp >= 35) score -= 25;
                else if (day.maxTemp >= 33) score -= 12;
            }
            if (day.minTemp != null) {
                hasWeather = true;
                if (day.minTemp <= 0) score -= 35;
                else if (day.minTemp <= 5) score -= 18;
            }
            if (day.precipitation != null) {
                hasWeather = true;
                if (day.precipitation >= 50) score -= 25;
                else if (day.precipitation >= 30) score -= 12;
            }
            if (day.windSpeed != null) {
                hasWeather = true;
                if (day.windSpeed >= 14) score -= 20;
                else if (day.windSpeed >= 9) score -= 10;
            }
            if (day.humidity != null && day.humidity >= 85) {
                hasWeather = true;
                score -= 8;
            }
            if (hasWeather) {
                total += clamp(score, 0, 100);
                knownDays++;
            }
        }
        return knownDays == 0 ? 70 : total / knownDays;
    }

    private List<PrioritizedAction> buildPrioritizedActions(List<RiskEvent> riskEvents) {
        List<PrioritizedAction> actions = new ArrayList<>();
        for (RiskEvent risk : riskEvents) {
            PrioritizedAction action = actionFor(risk);
            actions.add(action);
        }
        actions.sort(Comparator.comparingInt((PrioritizedAction action) -> action.priority).reversed()
                .thenComparing(action -> action.code));
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).rank = i + 1;
        }
        return actions;
    }

    private PrioritizedAction actionFor(RiskEvent risk) {
        PrioritizedAction action = new PrioritizedAction();
        action.relatedRiskCode = risk.code;
        action.code = "MITIGATE_" + risk.code;
        action.title = actionTitle(risk.code);
        action.riskReduction = risk.severity == Severity.RED ? 95 : risk.severity == Severity.ORANGE ? 75 : 55;
        action.evidenceStrength = risk.evidenceRefs.isEmpty() ? 50 : 90;
        action.urgency = risk.severity == Severity.RED ? 100 : risk.severity == Severity.ORANGE ? 75 : 50;
        action.reversibility = 80;
        action.effort = effortFor(risk.code);
        action.cost = costFor(risk.code);
        action.leadTimeDays = risk.severity == Severity.RED ? 0 : 1;
        action.evidenceRefs = new ArrayList<>(risk.evidenceRefs);
        action.priority = (int) Math.round(
                0.25 * action.riskReduction
                        + 0.15 * action.evidenceStrength
                        + 0.20 * action.urgency
                        + 0.10 * action.reversibility
                        + 0.10 * (100 - action.effort)
                        + 0.10 * (100 - action.cost)
                        + 0.10 * (action.leadTimeDays == 0 ? 100 : Math.max(0, 80 - 20 * action.leadTimeDays)));
        return action;
    }

    private String actionTitle(String code) {
        return switch (code) {
            case "PEAR_BLOSSOM_FROST", "COLD_FROST", "CUCUMBER_POST_TRANSPLANT_NIGHT_COLD" ->
                    "보온 덮개와 야간 보온 준비";
            case "CONCENTRATED_RAIN", "WATERLOGGING", "POTATO_WATERLOGGING" ->
                    "배수로와 고인 물 배출 경로 점검";
            case "HEAT", "LETTUCE_HEAT_HUMIDITY" ->
                    "차광과 환기·수분 관리 준비";
            case "WIND" -> "지지대와 시설 고정 상태 점검";
            case "DROUGHT" -> "관수 가능량과 토양 수분 점검";
            case "HIGH_HUMIDITY" -> "환기와 밀식 구간 점검";
            default -> "위험 요인 확인 및 현장 점검";
        };
    }

    private int effortFor(String code) {
        return switch (code) {
            case "WIND", "DROUGHT" -> 35;
            case "HEAT", "COLD_FROST", "HIGH_HUMIDITY" -> 45;
            default -> 55;
        };
    }

    private int costFor(String code) {
        return switch (code) {
            case "WIND", "HIGH_HUMIDITY" -> 25;
            case "DROUGHT" -> 45;
            default -> 55;
        };
    }

    // ─── Confidence and evidence ───────────────────────────────────────────

    private DataConfidence calculateDataConfidence(AnalysisInput input) {
        List<ConfidenceMetric> metrics = List.of(
                new ConfidenceMetric("soilSuitability", 0.45,
                        input.soilSuitabilityScores.values().stream().anyMatch(Objects::nonNull), 0.80),
                new ConfidenceMetric("soilPh", 0.15, sanitizePh(input.soilPh) != null, 0.80),
                new ConfidenceMetric("seasonalTemperature", 0.20, input.meanTemperature30d != null, 0.90),
                new ConfidenceMetric("soilEc", 0.10, sanitizeEc(input.soilEc) != null, 0.75),
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
                qualityWeight += metric.weight * qualityFor(input, metric.key, metric.defaultQuality);
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
        receipt.profileSources = PROFILES.stream()
                .map(profile -> profile.cropCode + ":" + profile.sourceRef + ":" + profile.reviewStatus)
                .collect(Collectors.toList());
        receipt.evidenceRefs = days.stream()
                .filter(day -> hasText(day.date))
                .map(day -> "forecast:" + day.date)
                .collect(Collectors.toList());
        for (String cropCode : SUPPORTED_CROPS) {
            String stage = stageFor(input, cropCode);
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

    // ─── Supporting helpers ─────────────────────────────────────────────────

    private List<ForecastDay> collectForecastDays(AnalysisInput input) {
        Map<String, ForecastDay> byDate = new LinkedHashMap<>();
        int anonymous = 0;
        for (ShortForecastAdapter.DailyForecast forecast : input.shortForecasts) {
            if (forecast == null) {
                continue;
            }
            ForecastDay day = ForecastDay.fromShortForecast(forecast);
            String key = hasText(day.date) ? day.date : "short-" + (++anonymous);
            byDate.put(key, day);
        }
        for (ForecastDay day : input.midTermForecasts) {
            if (day == null) {
                continue;
            }
            String key = hasText(day.date) ? day.date : "mid-" + (++anonymous);
            byDate.putIfAbsent(key, day);
        }
        return byDate.values().stream()
                .sorted(Comparator.comparing(this::forecastDateSortKey))
                .limit(11)
                .collect(Collectors.toList());
    }

    private LocalDate forecastDateSortKey(ForecastDay day) {
        if (!hasText(day.date)) {
            return LocalDate.MAX;
        }
        try {
            return day.date.length() == 8 ? LocalDate.parse(day.date, BASIC_DATE) : LocalDate.parse(day.date);
        } catch (DateTimeParseException ignored) {
            return LocalDate.MAX;
        }
    }

    private String stageFor(AnalysisInput input, String cropCode) {
        String requested = input.cropGrowthStages.get(cropCode);
        if (!hasText(requested) && cropCode.equalsIgnoreCase(input.selectedCropCode)) {
            requested = input.userEnteredGrowthStage;
        }
        GrowthStageResolver resolver = new GrowthStageResolver();
        GrowthStageResolver.ResolvedGrowthStage resolved = resolver.resolve(
                cropCode, requested, input.verifiedGddEvidence.get(cropCode));
        return resolved.stage;
    }

    private boolean isStage(String stage, String... candidates) {
        if (!hasText(stage)) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(stage)) {
                return true;
            }
        }
        return false;
    }

    private List<String> evidenceFor(List<ForecastDay> days, ForecastMatcher matcher) {
        List<String> refs = new ArrayList<>();
        for (ForecastDay day : days) {
            if (matcher.matches(day)) {
                refs.add("forecast:" + (hasText(day.date) ? day.date : "UNSPECIFIED"));
            }
        }
        return refs;
    }

    @FunctionalInterface
    private interface ForecastMatcher {
        boolean matches(ForecastDay day);
    }

    private int count(List<ForecastDay> days, ForecastMatcher matcher) {
        int count = 0;
        for (ForecastDay day : days) {
            if (matcher.matches(day)) {
                count++;
            }
        }
        return count;
    }

    @FunctionalInterface
    private interface ForecastValue {
        Double value(ForecastDay day);
    }

    private Double maximum(List<ForecastDay> days, ForecastValue value) {
        return days.stream().map(value::value).filter(Objects::nonNull).max(Double::compareTo).orElse(null);
    }

    private Double minimum(List<ForecastDay> days, ForecastValue value) {
        return days.stream().map(value::value).filter(Objects::nonNull).min(Double::compareTo).orElse(null);
    }

    private Double average(List<ForecastDay> days, ForecastValue value) {
        OptionalDouble average = days.stream().map(value::value).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private double sum(List<ForecastDay> days, ForecastValue value) {
        return days.stream().map(value::value).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Graduated deviation scoring: 100 inside the optimal range, then decaying
     * continuously with distance measured in caution-margin units (1 margin
     * beyond the range ≈ 70, 2 margins ≈ 40 — matching the old step function
     * at those two points) down to a 5-point floor. This distinguishes a
     * slightly-out-of-range reading from a badly-out-of-range one instead of
     * flattening every out-of-range value to the same score.
     */
    private Double scoreRange(Double value, double optimumMin, double optimumMax, double cautionMargin) {
        if (value == null) {
            return null;
        }
        if (value >= optimumMin && value <= optimumMax) {
            return 100.0;
        }
        double distance = value < optimumMin ? optimumMin - value : value - optimumMax;
        return decayByMargin(distance, cautionMargin);
    }

    private Double scoreTarget(Double value, double target, double innerMargin, double outerMargin) {
        if (value == null) {
            return null;
        }
        double difference = Math.abs(value - target);
        if (difference <= innerMargin) {
            return 100.0;
        }
        return decayByMargin(difference - innerMargin, outerMargin);
    }

    /** One-sided version of {@link #scoreRange} for metrics with only an upper safety bound (e.g. soil EC). */
    private Double scoreUpperBound(Double value, double optimumMax, double cautionMargin) {
        if (value == null) {
            return null;
        }
        if (value <= optimumMax) {
            return 100.0;
        }
        return decayByMargin(value - optimumMax, cautionMargin);
    }

    private double decayByMargin(double distance, double cautionMargin) {
        double marginUnits = cautionMargin <= 0 ? distance : distance / cautionMargin;
        return clamp(100.0 - 30.0 * marginUnits, 5.0, 100.0);
    }

    /** Soil pH outside a physically plausible range indicates a bad reading; treat it as missing rather than scoring it. */
    private Double sanitizePh(Double value) {
        return value != null && Double.isFinite(value) && value >= 3.0 && value <= 10.0 ? value : null;
    }

    /** Negative or implausibly large EC(dS/m) indicates a bad reading; treat it as missing rather than scoring it. */
    private Double sanitizeEc(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 20.0 ? value : null;
    }

    private void generateReasons(CropResult crop, List<MetricContribution> contributions) {
        List<MetricContribution> positive = contributions.stream()
                .filter(contribution -> contribution.score != null && contribution.score >= 70)
                .sorted(Comparator.comparingDouble((MetricContribution contribution) -> contribution.contribution).reversed())
                .limit(2)
                .collect(Collectors.toList());
        for (MetricContribution contribution : positive) {
            crop.positiveReasons.add(reasonText(contribution.metric, true));
        }
        Optional<MetricContribution> lowest = contributions.stream()
                .filter(contribution -> contribution.score != null && contribution.score < 70)
                .min(Comparator.comparingDouble(contribution -> contribution.score));
        lowest.ifPresent(contribution -> crop.cautionReason = reasonText(contribution.metric, false));
    }

    private String reasonText(String metric, boolean positive) {
        return switch (metric) {
            case "soilSuitabilityStat" -> positive
                    ? "지역 토양 적성 통계가 양호합니다."
                    : "지역 토양 적성 통계가 낮아 세부 필지 확인이 필요해요.";
            case "soilPh" -> positive
                    ? "지역 대표 토양 pH가 권장 범위에 부합해요."
                    : "지역 대표 토양 pH가 권장 범위를 벗어나 토양 관리가 필요해요.";
            case "seasonalTemperature" -> positive
                    ? "최근 30일 평균 기온이 생육 적온에 적합해요."
                    : "최근 30일 평균 기온이 생육 적온을 벗어나 주의가 필요해요.";
            case "soilEc" -> positive
                    ? "토양 염류 농도(EC)가 안정적인 수준이에요."
                    : "토양 염류 농도(EC)가 다소 높아 시비량 조절이 필요해요.";
            default -> positive ? "환경 조건이 양호합니다." : "추가 확인이 필요해요.";
        };
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

    private int calculateLegacySafety(List<RiskEvent> events) {
        double penalty = events.stream().mapToDouble(event -> switch (event.severity) {
            case YELLOW -> 8;
            case ORANGE -> 16;
            case RED -> 25;
        }).sum();
        return clampInt((int) Math.round(100 - Math.min(60, penalty)), 0, 100);
    }

    private double qualityFor(AnalysisInput input, String key, double defaultQuality) {
        Double configured = input.dataQualityScores.get(key);
        if (configured == null || !Double.isFinite(configured)) {
            return defaultQuality;
        }
        double quality = configured <= 1 ? configured : configured / 100.0;
        return clamp(quality, 0, 1);
    }

    private Double validScore(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 100 ? value : null;
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

    private double normalized(double value, double lower, double upper) {
        if (upper <= lower) {
            return 0;
        }
        return clamp((value - lower) / (upper - lower), 0, 1);
    }

    private double unitOrDefault(double value, double fallback) {
        return value > 0 && Double.isFinite(value) ? clamp(value, 0, 1) : fallback;
    }

    private double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private int clampInt(int value, int lower, int upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public String gradeFromScore(int score) {
        return RegionScoreBand.classify(score).grade();
    }

    private String regionSummaryText(int score) {
        return RegionScoreBand.classify(score).summary();
    }

    private RegionReportResponseDto.SourceDto weatherSource(String date) {
        return RegionReportResponseDto.SourceDto.builder()
                .provider("기상청")
                .service("단기예보 조회서비스")
                .sourceUrl("https://www.data.go.kr/")
                .dataDate(date)
                .build();
    }
}
