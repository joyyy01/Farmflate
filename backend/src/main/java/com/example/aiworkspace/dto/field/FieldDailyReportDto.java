package com.example.aiworkspace.dto.field;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldDailyReportDto {
    private String id;
    private String fieldId;
    private String reportDate;
    private String generatedAt;
    private String generationReason;
    private Integer suitabilityScore;
    private String summary;
    private List<String> prioritizedActions;
    private List<FieldSuitabilityReportDto.RiskDto> keyRisks;
    private List<FieldSuitabilityReportDto.ConditionDto> conditions;
}
