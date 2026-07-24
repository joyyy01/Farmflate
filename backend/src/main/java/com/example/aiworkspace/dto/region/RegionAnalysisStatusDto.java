package com.example.aiworkspace.dto.region;

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
    private List<String> completedSteps;
    private String currentStep;
    private Boolean retryable;
}
