package com.example.aiworkspace.service;

import com.example.aiworkspace.service.analysis.CropScoringEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CropScoringEngineTest {

    private final CropScoringEngine cropScoringEngine = new CropScoringEngine();

    @Test
    @DisplayName("고창군 수경/토양 지표 입력 시 감자 및 상추 적합도 점수가 고득점으로 계산된다")
    void calculateScores_Gochang_Success() {
        // given
        String sidoCode = "45";
        String sigunguCode = "45790"; // 전북 고창군

        // when
        CropScoringEngine.ScoringResult result = cropScoringEngine.calculateScores(sidoCode, sigunguCode);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRegionScore()).isGreaterThanOrEqualTo(70);
        assertThat(result.getRecommendedCrops()).isNotEmpty();
        assertThat(result.getRecommendedCrops().get(0).getCropName()).isNotNull();
    }
}
