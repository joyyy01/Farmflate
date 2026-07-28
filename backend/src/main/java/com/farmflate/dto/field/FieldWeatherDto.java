package com.farmflate.dto.field;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldWeatherDto {
    private FieldWeatherStatus status;
    private String unavailableReason;
    private Double currentTemperature;
    private Double minTemperature;
    private Double maxTemperature;
    private Integer precipitationProbability;
    private Double rainfallMm;
    private Double humidity;
    private Double windSpeed;
    private String condition;
    private Boolean isCached;

    public static FieldWeatherDto unavailable(String reason) {
        return FieldWeatherDto.builder()
                .status(FieldWeatherStatus.UNAVAILABLE)
                .unavailableReason(reason)
                .build();
    }
}
