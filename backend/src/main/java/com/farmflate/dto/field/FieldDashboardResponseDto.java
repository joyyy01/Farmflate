package com.farmflate.dto.field;

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
public class FieldDashboardResponseDto {
    private FieldSummaryDto field;
    private ReportSummaryDto report;
    private FieldWeatherDto weather;
    private SoilInfoDto soil;
    @Builder.Default
    private List<FieldTaskDto> tasks = List.of();
    @Builder.Default
    private List<FieldAlertDto> alerts = List.of();
    private ReasoningDto reasoning;
    @Builder.Default
    private List<FieldActivityLogDto> todayLogs = List.of();
    @Builder.Default
    private List<FieldHistoryItemDto> history = List.of();

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SoilInfoDto {
        private boolean available;
        private Double ph;
        private Double ec;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldSummaryDto {
        private String id;
        private String fieldName;
        private String cropCode;
        private String cropName;
        private String regionName;
        private String cultivationStartDate;
        private Integer cultivationDay;
        private String stage;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReportSummaryDto {
        private String id;
        private String reportDate;
        private String generatedAt;
        private String generationReason;
        private FieldDailyStatus status;
        private String headline;
        private String headlineDescription;
        private boolean historical;
        private int taskCountBeforeAcknowledgement;
        /**
         * Derived 0-100 종합 상태 점수 for the gauge display; null when weather data
         * was unavailable and a precise score would be false precision — see
         * FieldDashboardService#computeStatusScore.
         */
        private Integer statusScore;
        private String statusScoreZone;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReasoningDto {
        private String summary;
        @Builder.Default
        private List<String> points = List.of();
    }
}
