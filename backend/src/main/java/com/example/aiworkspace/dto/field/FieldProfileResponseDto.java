package com.example.aiworkspace.dto.field;

import com.example.aiworkspace.service.analysis.LocationResolution;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldProfileResponseDto {
    private String id;
    private String fieldName;
    private String cropCode;
    private String cropName;
    private LocationResolution location;
    private String cultivationMethod;
    private String cultivationStartDate;
    private String stage;
    private String linkedRegionAnalysisId;
    private Boolean active;
    private String createdAt;
    private String updatedAt;
    /** Returned on create and when a deterministic snapshot is available. */
    private FieldSuitabilityReportDto suitabilityReport;
    /** Optional: absent means no daily snapshot has been generated, not a synthetic report. */
    private FieldDailyReportDto latestReport;
}
