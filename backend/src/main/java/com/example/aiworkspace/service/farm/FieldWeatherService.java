package com.example.aiworkspace.service.farm;

import com.example.aiworkspace.domain.farm.FarmEntity;
import com.example.aiworkspace.domain.region.Region;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.domain.region.RegionRepository;
import com.example.aiworkspace.dto.field.FieldWeatherDto;
import com.example.aiworkspace.dto.field.FieldWeatherStatus;
import com.example.aiworkspace.service.analysis.LocationResolution;
import com.example.aiworkspace.service.external.ExternalResult;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves a field's weather strictly from the region/coordinates the field
 * is actually linked to — never the caller's globally-latest region, and
 * never fabricated from the fieldId. A provider failure or missing
 * coordinate mapping produces an explicit UNAVAILABLE snapshot instead of
 * zeros.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FieldWeatherService {

    private final RegionAnalysisRepository regionAnalysisRepository;
    private final RegionRepository regionRepository;
    private final ShortForecastAdapter shortForecastAdapter;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FieldWeatherDto load(FarmEntity field) {
        RegionAnalysisEntity analysis = regionAnalysisRepository
                .findByIdAndUserEmail(field.getRegionAnalysisId(), field.getUserEmail())
                .orElse(null);
        if (analysis == null) {
            return FieldWeatherDto.unavailable("FIELD_REGION_ANALYSIS_NOT_FOUND");
        }

        LocationResolution location = readLocation(field.getLocationJson());
        Integer nx = location != null ? location.kmaNx() : null;
        Integer ny = location != null ? location.kmaNy() : null;

        if (nx == null || ny == null) {
            Region region = regionRepository
                    .findBySidoCodeAndSigunguCode(analysis.getSidoCode(), analysis.getSigunguCode())
                    .orElse(null);
            nx = region != null ? region.getKmaNx() : null;
            ny = region != null ? region.getKmaNy() : null;
        }

        if (nx == null || ny == null) {
            return FieldWeatherDto.unavailable("LOCATION_NOT_MAPPED");
        }

        ExternalResult<List<ShortForecastAdapter.DailyForecast>> result;
        try {
            result = shortForecastAdapter.getForecast3Days(nx, ny);
        } catch (Exception exception) {
            log.warn("Field weather provider call failed for field {}: {}", field.getId(), exception.getMessage());
            return FieldWeatherDto.unavailable("EXTERNAL_WEATHER_UNAVAILABLE");
        }

        if (result == null || !result.isSuccess() || result.value() == null || result.value().isEmpty()) {
            String reason = result == null || result.errorCode() == null ? "EXTERNAL_WEATHER_UNAVAILABLE" : result.errorCode();
            return FieldWeatherDto.unavailable(reason);
        }

        return toDto(result.value().get(0), Boolean.TRUE.equals(hasCachedMetric(result)));
    }

    private boolean hasCachedMetric(ExternalResult<List<ShortForecastAdapter.DailyForecast>> result) {
        return result.metrics() != null && result.metrics().stream().anyMatch(metric -> metric != null && metric.isCached());
    }

    private FieldWeatherDto toDto(ShortForecastAdapter.DailyForecast today, boolean isCached) {
        Double currentTemp = (today.tmpValues != null && !today.tmpValues.isEmpty())
                ? today.tmpValues.get(0)
                : (today.minTemp != null && today.maxTemp != null ? (today.minTemp + today.maxTemp) / 2.0 : null);

        Integer pop = today.popMax;
        Double pcp = today.pcpTotal;
        String condition = null;
        if (pcp != null && pcp > 5.0) condition = "RAIN";
        else if (pop != null && pop >= 60) condition = "RAIN";
        else if (pop != null && pop >= 30) condition = "CLOUDY";
        else if (pop != null) condition = "SUNNY";

        return FieldWeatherDto.builder()
                .status(FieldWeatherStatus.AVAILABLE)
                .currentTemperature(round(currentTemp))
                .minTemperature(round(today.minTemp))
                .maxTemperature(round(today.maxTemp))
                .precipitationProbability(pop)
                .rainfallMm(round(pcp))
                .humidity(round(today.rehAvg))
                .windSpeed(round(today.wsdMax))
                .condition(condition)
                .isCached(isCached)
                .build();
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    private LocationResolution readLocation(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, LocationResolution.class);
        } catch (Exception exception) {
            return null;
        }
    }
}
