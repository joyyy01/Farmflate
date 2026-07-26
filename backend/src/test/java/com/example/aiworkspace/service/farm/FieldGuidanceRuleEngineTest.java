package com.example.aiworkspace.service.farm;

import com.example.aiworkspace.dto.field.FieldDailyStatus;
import com.example.aiworkspace.dto.field.FieldWeatherDto;
import com.example.aiworkspace.dto.field.FieldWeatherStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldGuidanceRuleEngineTest {

    private final FieldGuidanceRuleEngine engine = new FieldGuidanceRuleEngine();

    @Test
    void classifies_a_high_severity_forecast_as_danger() {
        FieldGuidanceRuleEngine.FieldGuidanceResult result = engine.evaluate(new FieldGuidanceRuleEngine.FieldGuidanceInput(
                "POTATO", "감자", "GROWING", weather(31.0, 42.0, 3.0), List.of()));

        assertEquals(FieldDailyStatus.DANGER, result.status());
        assertTrue(result.alerts().stream().anyMatch(alert -> "HIGH".equals(alert.getSeverity())));
    }

    @Test
    void keeps_a_medium_weather_alert_in_caution() {
        FieldGuidanceRuleEngine.FieldGuidanceResult result = engine.evaluate(new FieldGuidanceRuleEngine.FieldGuidanceInput(
                "POTATO", "감자", "GROWING", weather(31.0, 2.0, 3.0), List.of()));

        assertEquals(FieldDailyStatus.CAUTION, result.status());
    }

    @Test
    void surfaces_dry_weather_as_a_farmer_facing_alert() {
        FieldGuidanceRuleEngine.FieldGuidanceResult result = engine.evaluate(new FieldGuidanceRuleEngine.FieldGuidanceInput(
                "POTATO", "감자", "GROWING", weather(26.0, 0.0, 3.0), List.of()));

        assertEquals(FieldDailyStatus.CAUTION, result.status());
        assertTrue(result.alerts().stream().anyMatch(alert -> "DRY_CONDITION".equals(alert.getKey())
                && "건조 가능성".equals(alert.getTitle())));
    }

    @Test
    void surfaces_high_humidity_as_a_disease_check_alert() {
        FieldGuidanceRuleEngine.FieldGuidanceResult result = engine.evaluate(new FieldGuidanceRuleEngine.FieldGuidanceInput(
                "POTATO", "감자", "GROWING", weather(26.0, 2.0, 3.0, 86.0), List.of()));

        assertEquals(FieldDailyStatus.CAUTION, result.status());
        assertTrue(result.alerts().stream().anyMatch(alert -> "HIGH_HUMIDITY".equals(alert.getKey())
                && "높은 습도로 병해충 확인 필요".equals(alert.getTitle())));
    }

    @Test
    void keeps_strong_wind_in_the_alert_list() {
        FieldGuidanceRuleEngine.FieldGuidanceResult result = engine.evaluate(new FieldGuidanceRuleEngine.FieldGuidanceInput(
                "POTATO", "감자", "GROWING", weather(26.0, 2.0, 10.0), List.of()));

        assertEquals(FieldDailyStatus.CAUTION, result.status());
        assertTrue(result.alerts().stream().anyMatch(alert -> "STRONG_WIND".equals(alert.getKey())
                && "강풍 주의".equals(alert.getTitle())));
    }

    private FieldWeatherDto weather(double maxTemperature, double rainfallMm, double windSpeed) {
        return weather(maxTemperature, rainfallMm, windSpeed, 60.0);
    }

    private FieldWeatherDto weather(double maxTemperature, double rainfallMm, double windSpeed, double humidity) {
        return FieldWeatherDto.builder()
                .status(FieldWeatherStatus.AVAILABLE)
                .minTemperature(21.0)
                .maxTemperature(maxTemperature)
                .rainfallMm(rainfallMm)
                .humidity(humidity)
                .windSpeed(windSpeed)
                .build();
    }
}
