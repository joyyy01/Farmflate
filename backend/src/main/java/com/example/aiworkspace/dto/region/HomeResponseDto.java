package com.example.aiworkspace.dto.region;

import lombok.*;

import java.util.Collections;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeResponseDto {
    private UserDto user;
    private WeatherDto weather;
    private TodayActionDto todayAction;
    private LatestRegionAnalysisDto latestRegionAnalysis;
    @Builder.Default
    private List<Object> farms = Collections.emptyList();

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserDto {
        private String displayName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WeatherDto {
        private String status; // AVAILABLE, UNAVAILABLE
        private Double temperature;
        private Double minTemperature;
        private Double maxTemperature;
        private Integer precipitationProbability;
        private String condition; // SUNNY, RAIN, CLOUDY, SNOW
        private String observedOrForecastAt;
        private Boolean isCached;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TodayActionDto {
        private String title;
        private String reason;
        private String riskCode;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LatestRegionAnalysisDto {
        private String analysisId;
        private String regionName;
        private Integer score;
        private TopCropDto topCrop;
        private String analyzedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopCropDto {
        private String cropCode;
        private String cropName;
        private Integer score;
        private String reason;
    }
}
