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
                "POTATO", "감자", "GROWING", weather(31.0, 0.0, 3.0), List.of()));

        assertEquals(FieldDailyStatus.CAUTION, result.status());
    }

    private FieldWeatherDto weather(double maxTemperature, double rainfallMm, double windSpeed) {
        return FieldWeatherDto.builder()
                .status(FieldWeatherStatus.AVAILABLE)
                .minTemperature(21.0)
                .maxTemperature(maxTemperature)
                .rainfallMm(rainfallMm)
                .humidity(60.0)
                .windSpeed(windSpeed)
                .build();
    }
}
