package com.farmflate.controller;

import com.farmflate.dto.region.RegionWeatherForecastDto;
import com.farmflate.security.InternalApiAccessGuard;
import com.farmflate.service.region.RegionWeatherReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/weather")
@RequiredArgsConstructor
public class InternalWeatherApiController {

    private final InternalApiAccessGuard internalApiAccessGuard;
    private final RegionWeatherReadService regionWeatherReadService;

    @GetMapping("/regions/{sidoCode}/{sigunguCode}")
    public RegionWeatherForecastDto readRegionWeather(
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String internalToken,
            @PathVariable String sidoCode,
            @PathVariable String sigunguCode,
            @RequestParam(defaultValue = "1") int days
    ) {
        internalApiAccessGuard.requireValid(internalToken);
        return regionWeatherReadService.read(sidoCode, sigunguCode, days);
    }
}
