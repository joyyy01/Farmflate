package com.example.aiworkspace.dto.region;

import com.example.aiworkspace.service.analysis.LocationResolution;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Persisted, screen-facing region decision snapshot.
 *
 * <p>The legacy score fields are kept for compatibility.  New clients should
 * render the separated base fitness, season readiness, data confidence, and
 * typed environmental/risk surfaces rather than treating one score as a
 * complete decision.</p>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionReportResponseDto {
    private String analysisId;
    /** COMPLETED only when every required provider returned usable data; otherwise PARTIAL. */
    private String status;
    /** OWNER or PUBLIC; public reports contain no user identity. */
    private String analysisScope;
    private RegionDto region;
    private LocationResolution location;

    /** Compatibility-only overall environmental score. */
    private Integer regionScore;
    private String grade;
    private String summary;
    private ConfidenceDto confidence;

    private Double baseFitness;
    private Integer seasonReadiness;
    private ConfidenceDto dataConfidence;
    private ComponentsDto components;
    private EnvironmentSummaryDto environment;
    private List<String> environmentFeatures;

    private List<RecommendedCropDto> recommendedCrops;
    /** All supported crops, retained so direct field registration can score a non-TOP3 crop truthfully. */
    private List<CropDecisionDto> cropResults;
    private List<RiskDto> topRisks;
    private List<TipDto> tips;
    private List<SourceDto> sources;
    private List<String> missingMetrics;

    private String analyzedAt;
    private Boolean isCached;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConfidenceDto {
        private String grade;
        private String level;
        private Integer score;
        private String message;
        private ScoreRangeDto range;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ScoreRangeDto {
        private Integer min;
        private Integer max;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentsDto {
        private ComponentDetailDto climate;
        private ComponentDetailDto soil;
        private HazardComponentDetailDto hazard;
        private ComponentDetailDto cultivation;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ComponentDetailDto {
        private Integer score;
        private String grade;
        private String status;
        private String description;
        /** Raw 토양 pH / EC(전기전도도) readings; only populated on the soil component. */
        private Double soilPh;
        private Double soilEc;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HazardComponentDetailDto {
        private Integer safetyScore;
        private String grade;
        private String status;
        private String description;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EnvironmentSummaryDto {
        private Integer score;
        private String grade;
        private String status;
        private List<String> features;
        private ComponentsDto conditions;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecommendedCropDto {
        private String cropCode;
        private String cropName;
        private Integer score;
        private Integer rank;
        private List<String> positiveReasons;
        private String cautionReason;
        private String category;
        private String iconUrl;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CropDecisionDto {
        private String cropCode;
        private String cropName;
        private Integer score;
        private Double baseFitness;
        private Integer seasonReadiness;
        private Integer baseCriticalCap;
        private Integer criticalRiskCap;
        private Integer soilSuitabilityScore;
        private Integer soilPhScore;
        private Integer seasonalTemperatureScore;
        private Boolean calculable;
        private String notCalculableReason;
        private List<String> positiveReasons;
        private String cautionReason;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RiskDto {
        private Integer rank;
        private String riskCode;
        private String severity;
        private String level;
        private String title;
        private String description;
        private PeriodDto period;
        private List<String> affectedCrops;
        private List<String> actions;
        private List<String> causalChain;
        private Integer criticalCap;
        private Double remainingRisk;
        private List<SourceDto> evidenceRefs;
        private SourceDto source;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PeriodDto {
        private String start;
        private String end;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TipDto {
        private Integer rank;
        private String tipCode;
        private String title;
        private String summary;
        private String reason;
        private String sourceType;
        private String sourceName;
        private String sourceUrl;
        private String actionLabel;
        private String dataDate;
        private List<SourceDto> sourceRefs;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SourceDto {
        private String provider;
        private String service;
        private String sourceUrl;
        private String sourceRecordId;
        private String dataDate;
        private String measurementOrIssueAt;
        private String spatialLevel;
        private String precisionBadge;
        private String evidenceLevel;
        private Boolean isCached;
        private Boolean isReplay;
        private Boolean isFallback;
        private String fallbackReason;
        private String status;
        private List<String> transformations;
    }
}
