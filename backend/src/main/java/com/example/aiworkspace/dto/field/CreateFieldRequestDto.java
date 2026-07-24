package com.example.aiworkspace.dto.field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateFieldRequestDto {
    private String fieldName;
    private String cropCode;
    private String cropName;
    private String cultivationMethod;
    private LocalDate cultivationStartDate;
    private String stage;
    private String regionAnalysisId;
}
