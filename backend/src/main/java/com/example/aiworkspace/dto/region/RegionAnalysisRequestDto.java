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

    /** Optional precision location proof; canonical region identity remains required above. */
    private LocationRequestDto location;

    private String idempotencyKey;

    private Boolean forceRefresh;
}
