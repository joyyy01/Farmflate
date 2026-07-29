package com.farmflate.service.region;

import com.farmflate.domain.region.Region;
import com.farmflate.domain.region.RegionRepository;
import com.farmflate.dto.region.RegionWeatherForecastDto;
import com.farmflate.exception.ApiException;
import com.farmflate.integration.ShortForecastAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionWeatherReadService {

    private final RegionRepository regionRepository;
    private final ShortForecastAdapter shortForecastAdapter;

    public RegionWeatherForecastDto read(String sidoCode, String sigunguCode, int days) {
        if (days < 1 || days > 3) {
            throw ApiException.badRequest("INVALID_FORECAST_DAYS", "예보 일수는 1일부터 3일까지여야 합니다.");
        }

        Region region = regionRepository.findBySidoCodeAndSigunguCode(sidoCode, sigunguCode)
                .filter(Region::isEnabled)
                .orElseThrow(() -> ApiException.notFound("REGION_NOT_FOUND", "지역 정보를 찾을 수 없습니다."));
        if (!region.hasRegionalWeatherMapping()) {
            return RegionWeatherForecastDto.unavailable("REGION_WEATHER_NOT_MAPPED");
        }

        return RegionWeatherForecastDto.from(
                shortForecastAdapter.getForecast3Days(region.getKmaNx(), region.getKmaNy()),
                days
        );
    }
}
