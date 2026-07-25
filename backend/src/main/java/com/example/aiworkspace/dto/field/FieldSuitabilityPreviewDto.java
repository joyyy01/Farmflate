package com.example.aiworkspace.dto.field;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only suitability preview returned by POST /api/fields/preview.
 * It intentionally carries no persisted entity id — the field is not saved.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldSuitabilityPreviewDto {
    private String fieldName;
    private String cropCode;
    private String cropName;
    private String cultivationMethod;
    private String cultivationStartDate;
    private String stage;
    private String regionAnalysisId;
    private FieldSuitabilityReportDto suitabilityReport;
}
