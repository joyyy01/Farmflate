package com.example.aiworkspace.service;

import com.example.aiworkspace.service.analysis.CropScoringEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CropScoringEngineTest {

    private final CropScoringEngine cropScoringEngine = new CropScoringEngine();

    @Test
    @DisplayName("고창군 수경/기후 데이터 입력 시 추천 작물이 적절하게 계산 및 순위 정렬된다")
    void evaluateCrops_Success() {
        // given
        CropScoringEngine.EvaluationData data = new CropScoringEngine.EvaluationData();
        data.avgTemp = 21.5;
        data.maxTemp = 28.0;
        data.minTemp = 15.0;
        data.precip30d = 80.0;
        data.humidity = 68.0;
        data.soilPh = 6.2;
        data.cropSoilSuitability.put("POTATO", 95.0);
        data.cropSoilSuitability.put("LETTUCE", 90.0);

        // when
        List<CropScoringEngine.EvaluatedCrop> evaluatedCrops = cropScoringEngine.evaluateCrops(data, 85);

        // then
        assertThat(evaluatedCrops).isNotEmpty();
        assertThat(evaluatedCrops.get(0).totalScore).isGreaterThan(70.0);
        assertThat(evaluatedCrops.get(0).cropName).isNotNull();
    }

    @Test
    @DisplayName("최고 기온 및 집중 강우 발생 시 재해 위험 지표가 올바르게 차감된다")
    void calculateHazardRisks_HeavyRain_Success() {
        // given
        CropScoringEngine.EvaluationData data = new CropScoringEngine.EvaluationData();
        data.maxTemp = 34.0;
        data.precip30d = 120.0;

        // when
        Map<String, Object> result = cropScoringEngine.calculateHazardRisks(data);

        // then
        assertThat(result).containsKey("safetyScore");
        assertThat((Integer) result.get("safetyScore")).isLessThan(100);
    }
}
