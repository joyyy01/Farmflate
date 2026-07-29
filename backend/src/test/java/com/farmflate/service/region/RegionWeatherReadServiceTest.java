package com.farmflate.service.region;

import com.farmflate.domain.region.Region;
import com.farmflate.domain.region.RegionRepository;
import com.farmflate.dto.region.RegionWeatherForecastDto;
import com.farmflate.integration.ExternalResult;
import com.farmflate.integration.ShortForecastAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionWeatherReadServiceTest {

    @Mock private RegionRepository regionRepository;
    @Mock private ShortForecastAdapter shortForecastAdapter;

    private RegionWeatherReadService service;

    @BeforeEach
    void setUp() {
        service = new RegionWeatherReadService(regionRepository, shortForecastAdapter);
    }

    @Test
    void preservesProviderUnavailableStatusForMappedRegion() {
        Region region = Region.builder()
                .sidoCode("41")
                .sidoName("경기도")
                .sigunguCode("41135")
                .sigunguName("성남시 분당구")
                .kmaNx(60)
                .kmaNy(127)
                .asosStationId("108")
                .enabled(true)
                .build();
        when(regionRepository.findBySidoCodeAndSigunguCode("41", "41135")).thenReturn(Optional.of(region));
        when(shortForecastAdapter.getForecast3Days(60, 127))
                .thenReturn(ExternalResult.failure("PROVIDER_UNAVAILABLE"));

        RegionWeatherForecastDto result = service.read("41", "41135", 1);

        assertThat(result.status()).isEqualTo("UNAVAILABLE");
        assertThat(result.unavailableReason()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.days()).isEmpty();
    }
}
