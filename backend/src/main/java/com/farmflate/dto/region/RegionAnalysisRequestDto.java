package com.farmflate.dto.region;

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

    /** "PRIMARY" (default, the user's representative region) or "FIELD_LINKED"
     * (this analysis only backs one field's suitability scoring and must never
     * become the user's displayed "latest region"). Unrecognized/blank values
     * fall back to PRIMARY in the service layer. */
    private String purpose;
}
