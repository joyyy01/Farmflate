package com.farmflate.service.analysis.rules;

import com.farmflate.service.analysis.CropScoringEngine.AnalysisInput;
import com.farmflate.service.analysis.CropScoringEngine.CropProfile;
import com.farmflate.service.analysis.CropScoringEngine.CropResult;
import com.farmflate.service.analysis.CropScoringEngine.MetricContribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Calculates crop-level base fitness from the immutable crop rule catalog. */
public final class CropSuitabilityEvaluator {

    private final CropRuleCatalog ruleCatalog;

    public CropSuitabilityEvaluator(CropRuleCatalog ruleCatalog) {
        this.ruleCatalog = ruleCatalog;
    }

    public CropResult evaluate(CropProfile profile, AnalysisInput input) {
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

    public int compare(CropResult first, CropResult second) {
        int score = Double.compare(second.totalScore, first.totalScore);
        if (score != 0) return score;
        score = Double.compare(
                second.soilSuitabilityStatScore != null ? second.soilSuitabilityStatScore : 0,
                first.soilSuitabilityStatScore != null ? first.soilSuitabilityStatScore : 0);
        if (score != 0) return score;
        score = Double.compare(
                second.seasonalTemperatureScore != null ? second.seasonalTemperatureScore : 0,
                first.seasonalTemperatureScore != null ? first.seasonalTemperatureScore : 0);
        if (score != 0) return score;
        return Integer.compare(ruleCatalog.tieBreakIndex(first.cropCode), ruleCatalog.tieBreakIndex(second.cropCode));
    }

    public double qualityFor(AnalysisInput input, String key, double defaultQuality) {
        Double configured = input.dataQualityScores.get(key);
        if (configured == null || !Double.isFinite(configured)) return defaultQuality;
        double quality = configured <= 1 ? configured : configured / 100.0;
        return clamp(quality, 0, 1);
    }

    public Double sanitizePh(Double value) {
        return value != null && Double.isFinite(value) && value >= 3.0 && value <= 10.0 ? value : null;
    }

    public Double sanitizeEc(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 20.0 ? value : null;
    }

    private void addContribution(List<MetricContribution> contributions, String metric,
                                 Double score, double weight, double quality) {
        if (score == null || weight <= 0) return;
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

    private Double validScore(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 100 ? value : null;
    }

    private Double scoreRange(Double value, double optimumMin, double optimumMax, double cautionMargin) {
        if (value == null) return null;
        if (value >= optimumMin && value <= optimumMax) return 100.0;
        double distance = value < optimumMin ? optimumMin - value : value - optimumMax;
        return decayByMargin(distance, cautionMargin);
    }

    private Double scoreTarget(Double value, double target, double innerMargin, double outerMargin) {
        if (value == null) return null;
        double difference = Math.abs(value - target);
        if (difference <= innerMargin) return 100.0;
        return decayByMargin(difference - innerMargin, outerMargin);
    }

    private Double scoreUpperBound(Double value, double optimumMax, double cautionMargin) {
        if (value == null) return null;
        if (value <= optimumMax) return 100.0;
        return decayByMargin(value - optimumMax, cautionMargin);
    }

    private double decayByMargin(double distance, double cautionMargin) {
        double marginUnits = cautionMargin <= 0 ? distance : distance / cautionMargin;
        return clamp(100.0 - 30.0 * marginUnits, 5.0, 100.0);
    }

    private void generateReasons(CropResult crop, List<MetricContribution> contributions) {
        List<MetricContribution> positive = contributions.stream()
                .filter(contribution -> contribution.score != null && contribution.score >= 70)
                .sorted(Comparator.comparingDouble((MetricContribution contribution) -> contribution.contribution).reversed())
                .limit(2)
                .toList();
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

    private double clamp(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private int clampInt(int value, int lower, int upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
