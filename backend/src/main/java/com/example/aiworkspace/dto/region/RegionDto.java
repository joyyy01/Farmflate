package com.example.aiworkspace.dto.region;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegionDto {
    private String sidoCode;
    private String sidoName;
    private String sigunguCode;
    private String sigunguName;
}
