package com.example.aiworkspace.dto.region;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionAnalysisRequestDto {
    private String sidoCode;
    private String sigunguCode;
    @Builder.Default
    private Boolean forceRefresh = false;
    private String idempotencyKey;
}
