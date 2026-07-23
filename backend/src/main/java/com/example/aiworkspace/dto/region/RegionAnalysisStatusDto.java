package com.example.aiworkspace.dto.region;

import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionAnalysisStatusDto {
    private String analysisId;
    private String status; // PROCESSING, COMPLETED, FAILED
    private List<String> completedSteps;
    private String currentStep;
    private Boolean retryable;
}
