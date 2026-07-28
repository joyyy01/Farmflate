package com.farmflate.service.field;

import com.farmflate.dto.field.FieldTaskDto;
import com.farmflate.dto.field.FieldWeatherDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Turns the rule engine's already-decided facts into beginner-friendly
 * Korean copy. Never allowed to invent a task, a number, or a risk that the
 * rule engine did not already validate — see PythonFieldGuidanceNarrator for
 * the enforcement of that boundary.
 */
public interface FieldGuidanceNarrator {

    NarratedGuidance narrate(
            String cropCode,
            String cropName,
            String stage,
            LocalDate reportDate,
            FieldWeatherDto weather,
            FieldGuidanceRuleEngine.FieldGuidanceResult validated);

    record NarratedGuidance(
            String headline,
            String headlineDescription,
            List<FieldTaskDto> tasks,
            String reasoningSummary) {
    }
}
