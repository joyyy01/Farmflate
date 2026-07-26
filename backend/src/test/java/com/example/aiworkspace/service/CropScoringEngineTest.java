package com.example.aiworkspace.service;

import com.example.aiworkspace.service.analysis.CropScoringEngine;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CropScoringEngineTest {

    private final CropScoringEngine cropScoringEngine = new CropScoringEngine();

    @Test
    @DisplayName("고창군 수경/기후 데이터 입력 시 추천 작물이 적절하게 계산 및 순위 정렬된다")
    void analyze_Success() {
        // given
        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = 21.5;
        input.soilPh = 6.2;
        input.forecastRiskSafetyScore = 85;
        input.soilSuitabilityScores.put("POTATO", 95.0);
        input.soilSuitabilityScores.put("LETTUCE", 90.0);

        // when
        CropScoringEngine.AnalysisOutput output = cropScoringEngine.analyze(input);

        // then
        assertThat(output.topRecommended).isNotEmpty();
        assertThat(output.topRecommended.get(0).totalScore).isGreaterThan(70.0);
        assertThat(output.topRecommended.get(0).cropName).isNotNull();
    }

    @Test
    @DisplayName("최고 기온 및 집중 강우 발생 시 재해 위험 지표가 올바르게 차감된다")
    void calculateForecastRisks_HeavyRain_Success() {
        // given
        ShortForecastAdapter.DailyForecast forecast = new ShortForecastAdapter.DailyForecast();
        forecast.maxTemp = 34.0;
        forecast.pcpTotal = 120.0;

        // when
        CropScoringEngine.ForecastRiskResult result = cropScoringEngine.calculateForecastRisks(List.of(forecast));

        // then
        assertThat(result.safetyScore).isLessThan(100);
        assertThat(result.risks).isNotEmpty();
    }

    @Test
    @DisplayName("단기예보 목록의 null 항목은 안전하게 무시된다")
    void calculateForecastRisks_IgnoresNullForecastEntries() {
        List<ShortForecastAdapter.DailyForecast> forecasts = new ArrayList<>();
        forecasts.add(null);

        CropScoringEngine.ForecastRiskResult result = cropScoringEngine.calculateForecastRisks(forecasts);

        assertThat(result.safetyScore).isEqualTo(100);
        assertThat(result.risks).isEmpty();
    }

    @Test
    void normalized_live_metrics_produce_a_deterministic_bounded_top_three() {
        CropScoringEngine.AnalysisInput firstInput = completeNormalizedInput();
        CropScoringEngine.AnalysisInput secondInput = completeNormalizedInput();

        CropScoringEngine.AnalysisOutput first = cropScoringEngine.analyze(firstInput);
        CropScoringEngine.AnalysisOutput second = cropScoringEngine.analyze(secondInput);

        assertThat(first.allCropResults).hasSize(5).allSatisfy(crop -> {
            assertThat(crop.calculable).isTrue();
            assertThat(crop.soilSuitabilityStatScore).isEqualTo(firstInput.soilSuitabilityScores.get(crop.cropCode));
            assertThat(crop.totalScore).isBetween(0.0, 100.0);
        });
        assertThat(first.topRecommended).hasSizeLessThanOrEqualTo(3).allSatisfy(crop ->
                assertThat(crop.calculable).isTrue());
        assertThat(first.topRecommended).extracting(crop -> crop.cropCode)
                .containsExactlyElementsOf(second.topRecommended.stream().map(crop -> crop.cropCode).toList());
        assertThat(first.topRecommended).extracting(crop -> crop.totalScore)
                .containsExactlyElementsOf(second.topRecommended.stream().map(crop -> crop.totalScore).toList());
    }

    @Test
    void missing_required_soil_suitability_never_fabricates_recommendations_or_scores() {
        CropScoringEngine.AnalysisInput input = completeNormalizedInput();
        input.soilSuitabilityScores.clear();

        CropScoringEngine.AnalysisOutput output = cropScoringEngine.analyze(input);

        assertThat(output.topRecommended).isEmpty();
        assertThat(output.regionScoreCompatibility).isNull();
        assertThat(output.allCropResults).allSatisfy(crop -> {
            assertThat(crop.calculable).isFalse();
            assertThat(crop.totalScore).isZero();
            assertThat(crop.notCalculableReason).contains("필수 데이터");
        });
    }

    @Test
    void cucumber_remains_calculable_when_soil_suitability_and_temperature_exactly_meet_the_minimum_weight() {
        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = 21.0;
        input.soilSuitabilityScores.put("CUCUMBER", 47.0);
        input.dataQualityScores.put("soilSuitability", 100.0);
        input.dataQualityScores.put("seasonalTemperature", 100.0);

        CropScoringEngine.AnalysisOutput output = cropScoringEngine.analyze(input);
        CropScoringEngine.CropResult cucumber = output.allCropResults.stream()
                .filter(crop -> "CUCUMBER".equals(crop.cropCode))
                .findFirst()
                .orElseThrow();

        assertThat(cucumber.calculable).isTrue();
        assertThat(cucumber.totalScore).isGreaterThan(0.0);
    }

    @Test
    void lettuce_uses_partial_soil_data_when_suitability_and_temperature_are_available() {
        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = 18.0;
        input.soilSuitabilityScores.put("LETTUCE", 63.0);
        input.dataQualityScores.put("soilSuitability", 100.0);
        input.dataQualityScores.put("seasonalTemperature", 100.0);

        CropScoringEngine.AnalysisOutput output = cropScoringEngine.analyze(input);
        CropScoringEngine.CropResult lettuce = output.allCropResults.stream()
                .filter(crop -> "LETTUCE".equals(crop.cropCode))
                .findFirst()
                .orElseThrow();

        assertThat(lettuce.calculable).isTrue();
        assertThat(lettuce.totalScore).isGreaterThan(0.0);
    }

    private CropScoringEngine.AnalysisInput completeNormalizedInput() {
        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = 21.0;
        input.soilPh = 6.0;
        input.forecastRiskSafetyScore = 90;
        input.soilSuitabilityScores.put("APPLE", 72.0);
        input.soilSuitabilityScores.put("PEAR", 68.0);
        input.soilSuitabilityScores.put("CUCUMBER", 81.0);
        input.soilSuitabilityScores.put("POTATO", 94.0);
        input.soilSuitabilityScores.put("LETTUCE", 63.0);
        input.dataQualityScores.put("soilSuitability", 100.0);
        input.dataQualityScores.put("soilPh", 100.0);
        input.dataQualityScores.put("seasonalTemperature", 100.0);
        input.dataQualityScores.put("forecast", 100.0);
        return input;
    }
}
