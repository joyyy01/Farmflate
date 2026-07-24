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
public class FieldSuitabilityReportDto {
    private Integer suitabilityScore;
    private String grade;
    private String summary;
    /** Timestamp of the linked region snapshot, not a fabricated field measurement time. */
    private String analysisBasisDate;
    private String regionAnalysisId;
    private List<ConditionDto> conditions;
    private List<RiskDto> keyRisks;
    private List<String> prePlantChecklist;
    private List<String> currentManagementPoints;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConditionDto {
        private String key;
        private String label;
        private Integer score;
        private String status;
        private String description;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RiskDto {
        private String riskCode;
        private String severity;
        private String title;
        private String description;
        private List<String> actions;
    }
}
