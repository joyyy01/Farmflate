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
}
