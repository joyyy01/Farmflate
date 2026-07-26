package com.example.aiworkspace.dto.field;

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
public class FieldHistoryItemDto {
    private String date;
    private FieldDailyStatus status;
    private String statusLabel;
    @Builder.Default
    private List<String> logLabels = List.of();
    private boolean reportAvailable;
    /** Short, human-readable key metric for that day (e.g. "최고 32℃", "강수 35mm"); null when no weather was recorded. */
    private String keyMetric;
    /** AI-generated management summary (headline) for that day; null when no report was generated. */
    private String managementSummary;
}
