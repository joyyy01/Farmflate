package com.example.aiworkspace.dto.region;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionReportResponseDto {
    private String analysisId;
    private RegionDto region;
    private Integer regionScore;
    private String grade;
    private String summary;
    private ConfidenceDto confidence;
    private ComponentsDto components;
    private List<RecommendedCropDto> recommendedCrops;
    private List<RiskDto> topRisks;
    private List<TipDto> tips;
    private List<SourceDto> sources;
    private String analyzedAt;
    private String dataMode;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConfidenceDto {
        private String grade;
        private Integer score;
        private String message;
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
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HazardComponentDetailDto {
        private Integer safetyScore;
        private String grade;
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
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RiskDto {
        private Integer rank;
        private String riskCode;
        private String level;
        private String title;
        private String description;
        private PeriodDto period;
        private List<String> affectedCrops;
        private List<String> actions;
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
        private String sourceType;
        private String sourceName;
        private String sourceUrl;
        private String actionLabel;
        private String dataDate;
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
        private String dataDate;
    }
}
