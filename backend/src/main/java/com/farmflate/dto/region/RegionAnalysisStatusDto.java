package com.farmflate.dto.region;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionAnalysisStatusDto {
    private String analysisId;
    private String status; // PROCESSING, COMPLETED, FAILED
    /** OWNER for an authenticated private snapshot, PUBLIC for anonymous regional exploration. */
    private String analysisScope;
    private List<String> completedSteps;
    private String currentStep;
    /** Stable step codes (REGION, RECENT_WEATHER, FORECAST, SOIL, CROP, REPORT). */
    private List<String> completedStepCodes;
    private String currentStepCode;
    private Boolean retryable;
    private Boolean reused;
    private String errorCode;
    private String errorMessage;
}
