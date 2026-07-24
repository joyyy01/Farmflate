package com.example.aiworkspace.dto.region;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionReportResponseDtoContractTest {

    @Test
    void exposes_the_decision_and_screen_state_contract() throws Exception {
        RegionReportResponseDto report = RegionReportResponseDto.builder()
                .analysisId("a0f0b559-117e-4dc5-a1fd-66af73cb8de1")
                .status("PARTIAL")
                .region(RegionDto.builder().sidoCode("52").sigunguCode("52180").build())
                .baseFitness(82.5)
                .seasonReadiness(49)
                .dataConfidence(RegionReportResponseDto.ConfidenceDto.builder()
                        .level("LOW")
                        .score(58)
                        .range(RegionReportResponseDto.ScoreRangeDto.builder().min(48).max(68).build())
                        .build())
                .safeWorkWindows(List.of(RegionReportResponseDto.SafeWorkWindowDto.builder()
                        .start("2026-07-24").end("2026-07-25").reason("작업 가능 예보 구간").build()))
                .prioritizedActions(List.of(RegionReportResponseDto.PrioritizedActionDto.builder()
                        .rank(1).title("배수로 점검").reason("집중 강수 위험").build()))
                .missingMetrics(List.of("FORECAST_PROVIDER_FAILURE"))
                .build();

        String json = new ObjectMapper().writeValueAsString(report);

        assertThat(json)
                .contains("\"status\":\"PARTIAL\"")
                .contains("\"baseFitness\":82.5")
                .contains("\"seasonReadiness\":49")
                .contains("\"dataConfidence\"")
                .contains("\"safeWorkWindows\"")
                .contains("\"prioritizedActions\"")
                .contains("\"missingMetrics\"");
    }
}
