package com.farmflate.dto.field;

import com.farmflate.service.analysis.LocationResolution;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
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

    private Integer cultivationDay;
    private FieldDailyStatus dailyStatus;
    private String dailyStatusLabel;
    private String dailyHeadline;
    private String dailyReportDate;
    /** Today's dashboard alerts (오늘의 주의·위험), shown inline on the MyFarm list card. */
    @Builder.Default
    private List<FieldAlertDto> dailyAlerts = List.of();
}
