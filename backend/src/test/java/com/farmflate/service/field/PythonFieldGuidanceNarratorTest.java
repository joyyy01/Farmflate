package com.farmflate.service.field;

import com.farmflate.dto.field.FieldAlertDto;
import com.farmflate.dto.field.FieldDailyStatus;
import com.farmflate.dto.field.FieldTaskBadge;
import com.farmflate.dto.field.FieldTaskDto;
import com.farmflate.dto.field.FieldWeatherDto;
import com.farmflate.dto.field.FieldWeatherStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

class PythonFieldGuidanceNarratorTest {

    @Test
    void configures_connect_and_read_timeouts_for_the_python_agent_call() {
        PythonFieldGuidanceNarrator narrator = new PythonFieldGuidanceNarrator(new ObjectMapper());
        ReflectionTestUtils.setField(narrator, "requestTimeoutMs", 2_500);

        ReflectionTestUtils.invokeMethod(narrator, "initRestTemplate");

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(narrator, "restTemplate");
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2_500);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(2_500);
    }

    @Test
    void builds_a_fact_package_with_weather_candidates_and_reasoning_for_the_ai_summary() {
        PythonFieldGuidanceNarrator narrator = new PythonFieldGuidanceNarrator(new ObjectMapper());
        FieldWeatherDto weather = FieldWeatherDto.builder()
                .status(FieldWeatherStatus.AVAILABLE)
                .minTemperature(21.0)
                .maxTemperature(31.0)
                .rainfallMm(0.0)
                .humidity(58.0)
                .windSpeed(1.2)
                .build();
        FieldGuidanceRuleEngine.FieldGuidanceResult guidance = new FieldGuidanceRuleEngine.FieldGuidanceResult(
                FieldDailyStatus.CAUTION,
                "오후 고온 주의",
                "고온 시간대에 잎이 처질 수 있어요.",
                List.of(FieldTaskDto.builder()
                        .key("CHECK_SHADE")
                        .title("차광과 통풍 확인")
                        .description("강한 햇빛을 줄이고 바람길을 확인하세요.")
                        .badge(FieldTaskBadge.CHECK_ANYTIME)
                        .build()),
                List.of(FieldAlertDto.builder()
                        .key("HIGH_TEMPERATURE")
                        .severity("MEDIUM")
                        .title("오후 고온 주의")
                        .description("오늘 최고기온이 31℃까지 오를 전망이에요.")
                        .build()),
                List.of("최고 기온이 상추 생육 적온보다 높습니다."));

        Map<String, Object> facts = narrator.buildFactPackage(
                "lettuce", "상추", "생장기", LocalDate.of(2026, 7, 26), weather, guidance);

        assertEquals("상추", facts.get("cropName"));
        assertEquals(31.0, ((Map<?, ?>) facts.get("weather")).get("maxTemperature"));
        assertEquals("차광과 통풍 확인", ((Map<?, ?>) ((List<?>) facts.get("tasks")).get(0)).get("title"));
        assertEquals("오후 고온 주의", ((Map<?, ?>) ((List<?>) facts.get("alerts")).get(0)).get("title"));
        assertTrue(((List<?>) facts.get("reasoningPoints")).contains("최고 기온이 상추 생육 적온보다 높습니다."));
    }
}
