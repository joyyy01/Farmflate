package com.example.aiworkspace.dto.region;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionAnalysisRequestDto {
    private String sidoCode;

    private String sidoName;

    private String sigunguCode;

    private String sigunguName;

    private String idempotencyKey;

    private Boolean forceRefresh;
}
