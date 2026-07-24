package com.example.aiworkspace.dto.field;

import com.example.aiworkspace.dto.region.LocationRequestDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFieldRequestDto {
    private String fieldName;
    private String cropCode;
    private String cropName;
    /** Optional.  The linked owned region analysis remains the decision source. */
    private LocationRequestDto location;
    private String cultivationMethod;
    private LocalDate cultivationStartDate;
    private String stage;
    private String regionAnalysisId;
}
