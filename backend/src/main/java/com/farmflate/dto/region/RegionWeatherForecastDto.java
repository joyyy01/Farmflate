package com.farmflate.dto.region;

import com.farmflate.integration.ExternalResult;
import com.farmflate.integration.ShortForecastAdapter;

import java.util.List;

public record RegionWeatherForecastDto(
        String status,
        String unavailableReason,
        List<Day> days
) {
    public record Day(
            String date,
            Double minTemperature,
            Double maxTemperature,
            Integer precipitationProbability,
            Double rainfallMm,
            Double humidity,
            Double windSpeed
    ) {
    }

    public static RegionWeatherForecastDto unavailable(String reason) {
        return new RegionWeatherForecastDto("UNAVAILABLE", reason, List.of());
    }

    public static RegionWeatherForecastDto from(
            ExternalResult<List<ShortForecastAdapter.DailyForecast>> result,
            int days
    ) {
        if (!result.isSuccess()) {
            return unavailable(result.isEmpty() ? "FORECAST_EMPTY" : result.errorCode());
        }

        List<Day> forecastDays = result.value().stream()
                .limit(days)
                .map(day -> new Day(
                        day.date,
                        day.minTemp,
                        day.maxTemp,
                        day.popMax,
                        day.pcpTotal,
                        day.rehAvg,
                        day.wsdMax
                ))
                .toList();
        if (forecastDays.isEmpty()) {
            return unavailable("FORECAST_EMPTY");
        }
        return new RegionWeatherForecastDto("AVAILABLE", null, forecastDays);
    }
}
