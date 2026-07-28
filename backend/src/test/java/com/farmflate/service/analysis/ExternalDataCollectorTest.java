package com.farmflate.service.analysis;

import com.farmflate.domain.region.Region;
import com.farmflate.integration.AsosAdapter;
import com.farmflate.integration.ExternalResult;
import com.farmflate.integration.MidTermForecastAdapter;
import com.farmflate.integration.ShortForecastAdapter;
import com.farmflate.integration.SoilChemistryAdapter;
import com.farmflate.integration.SoilSuitabilityAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalDataCollectorTest {

    @Test
    void preserves_success_empty_and_failed_provider_results_when_one_collection_task_throws() {
        AsosAdapter asos = mock(AsosAdapter.class);
        ShortForecastAdapter shortForecast = mock(ShortForecastAdapter.class);
        MidTermForecastAdapter midTermForecast = mock(MidTermForecastAdapter.class);
        SoilChemistryAdapter soilChemistry = mock(SoilChemistryAdapter.class);
        SoilSuitabilityAdapter soilSuitability = mock(SoilSuitabilityAdapter.class);
        Executor directExecutor = Runnable::run;
        ExternalDataCollector collector = new ExternalDataCollector(
                asos, shortForecast, midTermForecast, soilChemistry, soilSuitability, directExecutor);
        Region region = Region.builder().sidoName("전북특별자치도").sigunguName("고창군").sigunguCode("52180").build();
        LocationResolution location = new LocationResolution(
                "고창군", null, null, null, 52, 77, "172", "REGION", "REGION", "B", List.of(), List.of(), List.of(), null);

        when(asos.get30DaySummary("172")).thenReturn(ExternalResult.failure("ASOS_DOWN"));
        when(shortForecast.getForecast3Days(52, 77)).thenReturn(ExternalResult.success(List.of()));
        when(midTermForecast.getForecast4To10Days("전북특별자치도", "52180"))
                .thenThrow(new IllegalStateException("timeout"));
        when(soilChemistry.getSoilChemistry("52180", "전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.empty());
        when(soilSuitability.getSoilSuitability("52180", "전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(Map.of()));

        ExternalDataCollector.CollectedProviderData results = collector.collect(region, location);

        assertThat(results.asos()).matches(result -> result.isFailure() && "ASOS_DOWN".equals(result.errorCode()));
        assertThat(results.shortForecast()).matches(ExternalResult::isSuccess);
        assertThat(results.midTermForecast()).matches(result -> result.isFailure()
                && "MIDTERM_FORECAST_COLLECTION_FAILED".equals(result.errorCode()));
        assertThat(results.soilChemistry()).matches(ExternalResult::isEmpty);
        assertThat(results.soilSuitability()).matches(ExternalResult::isSuccess);
    }
}
