package com.farmflate.dto.field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Persisted as field_daily_reports.payload_json. Older REGISTRATION/DAILY_0600
 * rows only populated the suitability-report-era fields below; newer
 * DAILY_0630 rows populate the dashboard-era fields (status/headline/weather/
 * tasks/alerts/reasoning) instead. Both shapes deserialize through this one
 * DTO so historical rows keep reading correctly.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDailyReportDto {
    private String id;
    private String fieldId;
    private String reportDate;
    private String generatedAt;
    private String generationReason;

    // Suitability-report era (REGISTRATION / DAILY_0600)
    private Integer suitabilityScore;
    private String summary;
    private List<String> prioritizedActions;
    private List<FieldSuitabilityReportDto.RiskDto> keyRisks;
    private List<FieldSuitabilityReportDto.ConditionDto> conditions;

    // Dashboard-era (DAILY_0630)
    private String cropCode;
    private String cropName;
    private String stage;
    private FieldDailyStatus status;
    private String headline;
    private String headlineDescription;
    private FieldWeatherDto weather;
    private List<FieldTaskDto> tasks;
    private List<FieldAlertDto> alerts;
    private FieldDashboardResponseDto.ReasoningDto reasoning;
}
