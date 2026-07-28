package com.farmflate.service.analysis.rules;

import com.farmflate.dto.region.RegionReportResponseDto;
import com.farmflate.integration.ShortForecastAdapter;
import com.farmflate.service.analysis.CropScoringEngine.AnalysisInput;
import com.farmflate.service.analysis.CropScoringEngine.CropResult;
import com.farmflate.service.analysis.CropScoringEngine.ForecastDay;
import com.farmflate.service.analysis.CropScoringEngine.ForecastRiskResult;
import com.farmflate.service.analysis.CropScoringEngine.PrioritizedAction;
import com.farmflate.service.analysis.CropScoringEngine.RiskEvent;
import com.farmflate.service.analysis.CropScoringEngine.Severity;
import com.farmflate.service.analysis.GrowthStageResolver;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/** Evaluates forecast-derived risk rules and the mitigation actions they imply. */
public final class ForecastRiskEvaluator {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final CropRuleCatalog ruleCatalog;
    private final CropSuitabilityEvaluator suitabilityEvaluator;

    public ForecastRiskEvaluator(CropRuleCatalog ruleCatalog, CropSuitabilityEvaluator suitabilityEvaluator) {
        this.ruleCatalog = ruleCatalog;
        this.suitabilityEvaluator = suitabilityEvaluator;
    }

    public ForecastRiskResult calculateLegacyForecastRisks(List<ShortForecastAdapter.DailyForecast> forecasts) {
        ForecastRiskResult result = new ForecastRiskResult();
        if (forecasts == null || forecasts.isEmpty()) {
            result.safetyScore = 100;
            return result;
        }

        int penalty = 0;
        Set<String> seenRisks = new HashSet<>();
        for (ShortForecastAdapter.DailyForecast day : forecasts) {
            if (day == null) continue;
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

    public List<ForecastDay> collectForecastDays(AnalysisInput input) {
        Map<String, ForecastDay> byDate = new LinkedHashMap<>();
        int anonymous = 0;
        for (ShortForecastAdapter.DailyForecast forecast : input.shortForecasts) {
            if (forecast == null) continue;
            ForecastDay day = ForecastDay.fromShortForecast(forecast);
            String key = hasText(day.date) ? day.date : "short-" + (++anonymous);
            byDate.put(key, day);
        }
        for (ForecastDay day : input.midTermForecasts) {
            if (day == null) continue;
            String key = hasText(day.date) ? day.date : "mid-" + (++anonymous);
            byDate.putIfAbsent(key, day);
        }
        return byDate.values().stream()
                .sorted(Comparator.comparing(this::forecastDateSortKey))
                .limit(11)
                .collect(Collectors.toList());
    }

    public List<RiskEvent> evaluate(AnalysisInput input, List<ForecastDay> days) {
        if (days.isEmpty()) return List.of();
        List<RiskEvent> events = new ArrayList<>();
        double fieldVulnerability = unitOrDefault(input.fieldVulnerability, 1.0);
        double exposure = unitOrDefault(input.exposureModifier, 1.0);
        double confidence = suitabilityEvaluator.qualityFor(input, "forecast", 1.0);

        Double hottest = maximum(days, day -> day.maxTemp);
        int heatDays = count(days, day -> day.maxTemp != null && day.maxTemp >= 33);
        if (hottest != null && hottest >= 33) {
            addRisk(events, "HEAT", normalized(hottest, 32, 40), heatDays, 0.75,
                    fieldVulnerability, exposure, confidence, ruleCatalog.supportedCropCodes(),
                    evidenceFor(days, day -> day.maxTemp != null && day.maxTemp >= 33),
                    List.of("high maximum temperature", "heat stress exposure"));
        }

        Double coldest = minimum(days, day -> day.minTemp);
        int coldDays = count(days, day -> day.minTemp != null && day.minTemp <= 5);
        if (coldest != null && coldest <= 5) {
            addRisk(events, "COLD_FROST", normalized(6 - coldest, 0, 10), coldDays, 0.80,
                    fieldVulnerability, exposure, confidence, ruleCatalog.supportedCropCodes(),
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

    public int calculateSeasonReadiness(CropResult crop, List<RiskEvent> cropEvents, List<ForecastDay> days) {
        double base = crop.baseFitness == null ? 0 : crop.baseFitness;
        double weatherReadiness = forecastReadiness(days);
        double riskPenalty = cropEvents.stream()
                .mapToDouble(event -> event.remainingRisk * severityPenaltyWeight(event.severity))
                .sum();
        double raw = clamp(0.65 * base + 0.35 * weatherReadiness - Math.min(35, riskPenalty), 0, 100);
        int cap = cropEvents.stream().map(event -> event.criticalCap).min(Integer::compareTo).orElse(100);
        return (int) Math.round(Math.min(raw, cap));
    }

    public List<PrioritizedAction> buildPrioritizedActions(List<RiskEvent> riskEvents) {
        List<PrioritizedAction> actions = new ArrayList<>();
        for (RiskEvent risk : riskEvents) actions.add(actionFor(risk));
        actions.sort(Comparator.comparingInt((PrioritizedAction action) -> action.priority).reversed()
                .thenComparing(action -> action.code));
        for (int i = 0; i < actions.size(); i++) actions.get(i).rank = i + 1;
        return actions;
    }

    public int calculateLegacySafety(List<RiskEvent> events) {
        double penalty = events.stream().mapToDouble(event -> switch (event.severity) {
            case YELLOW -> 8;
            case ORANGE -> 16;
            case RED -> 25;
        }).sum();
        return clampInt((int) Math.round(100 - Math.min(60, penalty)), 0, 100);
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
        if (duration <= 0) return;
        double index = clamp(intensity, 0, 1)
                * Math.min(1.0, duration)
                * clamp(stageSensitivity, 0, 1)
                * clamp(fieldVulnerability, 0, 1)
                * clamp(exposure, 0, 1)
                * clamp(confidence, 0, 1);
        Severity severity = severityFor(index);
        if (severity == null) return;
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

    private RegionReportResponseDto.RiskDto legacyRisk(
            int rank, String code, boolean danger, String title, String description,
            List<String> affectedCrops, List<String> actions, String date) {
        return RegionReportResponseDto.RiskDto.builder()
                .rank(rank).riskCode(code).level(danger ? "DANGER" : "CAUTION")
                .title(title).description(description).affectedCrops(affectedCrops).actions(actions)
                .source(weatherSource(date)).build();
    }

    private Severity severityFor(double index) {
        if (index >= 0.70) return Severity.RED;
        if (index >= 0.40) return Severity.ORANGE;
        if (index >= 0.18) return Severity.YELLOW;
        return null;
    }

    private int capFor(Severity severity) {
        return switch (severity) {
            case YELLOW -> 84;
            case ORANGE -> 69;
            case RED -> 49;
        };
    }

    private double severityPenaltyWeight(Severity severity) {
        return switch (severity) {
            case YELLOW -> 0.05;
            case ORANGE -> 0.09;
            case RED -> 0.12;
        };
    }

    private double forecastReadiness(List<ForecastDay> days) {
        if (days.isEmpty()) return 70;
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

    private String actionTitle(String code) {
        return switch (code) {
            case "PEAR_BLOSSOM_FROST", "COLD_FROST", "CUCUMBER_POST_TRANSPLANT_NIGHT_COLD" ->
                    "보온 덮개와 야간 보온 준비";
            case "CONCENTRATED_RAIN", "WATERLOGGING", "POTATO_WATERLOGGING" ->
                    "배수로와 고인 물 배출 경로 점검";
            case "HEAT", "LETTUCE_HEAT_HUMIDITY" -> "차광과 환기·수분 관리 준비";
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

    public String stageFor(AnalysisInput input, String cropCode) {
        String requested = input.cropGrowthStages.get(cropCode);
        if (!hasText(requested) && cropCode.equalsIgnoreCase(input.selectedCropCode)) {
            requested = input.userEnteredGrowthStage;
        }
        GrowthStageResolver resolver = new GrowthStageResolver();
        return resolver.resolve(cropCode, requested, input.verifiedGddEvidence.get(cropCode)).stage;
    }

    private boolean isStage(String stage, String... candidates) {
        if (!hasText(stage)) return false;
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(stage)) return true;
        }
        return false;
    }

    private LocalDate forecastDateSortKey(ForecastDay day) {
        if (!hasText(day.date)) return LocalDate.MAX;
        try {
            return day.date.length() == 8 ? LocalDate.parse(day.date, BASIC_DATE) : LocalDate.parse(day.date);
        } catch (DateTimeParseException ignored) {
            return LocalDate.MAX;
        }
    }

    private List<String> evidenceFor(List<ForecastDay> days, ForecastMatcher matcher) {
        List<String> refs = new ArrayList<>();
        for (ForecastDay day : days) {
            if (matcher.matches(day)) refs.add("forecast:" + (hasText(day.date) ? day.date : "UNSPECIFIED"));
        }
        return refs;
    }

    private int count(List<ForecastDay> days, ForecastMatcher matcher) {
        int count = 0;
        for (ForecastDay day : days) if (matcher.matches(day)) count++;
        return count;
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

    private RegionReportResponseDto.SourceDto weatherSource(String date) {
        return RegionReportResponseDto.SourceDto.builder()
                .provider("기상청").service("단기예보 조회서비스").sourceUrl("https://www.data.go.kr/")
                .dataDate(date).build();
    }

    private double normalized(double value, double lower, double upper) {
        if (upper <= lower) return 0;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface ForecastMatcher {
        boolean matches(ForecastDay day);
    }

    @FunctionalInterface
    private interface ForecastValue {
        Double value(ForecastDay day);
    }
}
