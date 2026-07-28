package com.farmflate.service.analysis;

import com.farmflate.domain.region.Region;
import com.farmflate.dto.region.RegionDto;
import com.farmflate.dto.region.RegionReportResponseDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Assembles response-only DTOs from already collected and scored analysis data. */
final class RegionAnalysisResponseMapper {

    RegionDto toRegionDto(Region region) {
        return RegionDto.builder().sidoCode(region.getSidoCode()).sidoName(region.getSidoName())
                .sigunguCode(region.getSigunguCode()).sigunguName(region.getSigunguName()).build();
    }

    RegionReportResponseDto assemble(Region region, LocationResolution location, CropScoringEngine.AnalysisOutput output,
                                     List<String> missingMetrics, List<RegionReportResponseDto.SourceDto> sources) {
        List<String> reportMissingMetrics = new ArrayList<>(missingMetrics == null ? List.of() : missingMetrics);
        List<RegionReportResponseDto.RiskDto> risks = toRiskDtos(output.decisionOutput.riskEvents);
        List<RegionReportResponseDto.RecommendedCropDto> recommended = toRecommendedCrops(output.topRecommended);
        List<RegionReportResponseDto.CropDecisionDto> cropResults = toCropDecisions(output.allCropResults);
        RegionReportResponseDto.ComponentsDto components = enrichComponents(output.components);
        RegionReportResponseDto.ConfidenceDto dataConfidence = toDataConfidence(output.decisionOutput.dataConfidence);
        boolean partial = !reportMissingMetrics.isEmpty() || recommended.isEmpty();
        if (recommended.isEmpty() && reportMissingMetrics.isEmpty()) {
            reportMissingMetrics.add("INSUFFICIENT_CALCULABLE_INPUTS");
            partial = true;
        }

        List<String> features = environmentFeatures(components, risks, reportMissingMetrics);
        int regionScore = output.regionScoreCompatibility == null ? 0 : output.regionScoreCompatibility;
        return RegionReportResponseDto.builder()
                .analysisId(UUID.randomUUID().toString())
                .status(partial ? "PARTIAL" : "COMPLETED")
                .region(toRegionDto(region))
                .location(location)
                .regionScore(output.regionScoreCompatibility)
                .grade(output.regionGrade)
                .summary(buildSummary(region.getSigunguName(), regionScore, risks, reportMissingMetrics))
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
                .tips(buildOfficialTips(reportMissingMetrics))
                .sources(sources)
                .missingMetrics(reportMissingMetrics)
                .analyzedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME))
                .isCached(false)
                .build();
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
            // Invalid evidence dates do not make a completed provider result invalid.
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
