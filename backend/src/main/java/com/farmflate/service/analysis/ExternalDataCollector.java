package com.farmflate.service.analysis;

import com.farmflate.domain.region.Region;
import com.farmflate.integration.AsosAdapter;
import com.farmflate.integration.ExternalResult;
import com.farmflate.integration.MidTermForecastAdapter;
import com.farmflate.integration.ShortForecastAdapter;
import com.farmflate.integration.SoilChemistryAdapter;
import com.farmflate.integration.SoilSuitabilityAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Collects independent provider snapshots without letting one provider failure
 * discard usable siblings. The executor is bounded separately from analysis
 * job workers so a worker never waits for tasks queued behind itself.
 */
@Slf4j
@Service
public class ExternalDataCollector {

    private final AsosAdapter asosAdapter;
    private final ShortForecastAdapter shortForecastAdapter;
    private final MidTermForecastAdapter midTermForecastAdapter;
    private final SoilChemistryAdapter soilChemistryAdapter;
    private final SoilSuitabilityAdapter soilSuitabilityAdapter;
    private final Executor providerExecutor;

    public ExternalDataCollector(
            AsosAdapter asosAdapter,
            ShortForecastAdapter shortForecastAdapter,
            MidTermForecastAdapter midTermForecastAdapter,
            SoilChemistryAdapter soilChemistryAdapter,
            SoilSuitabilityAdapter soilSuitabilityAdapter,
            @Qualifier("regionProviderExecutor") Executor providerExecutor) {
        this.asosAdapter = asosAdapter;
        this.shortForecastAdapter = shortForecastAdapter;
        this.midTermForecastAdapter = midTermForecastAdapter;
        this.soilChemistryAdapter = soilChemistryAdapter;
        this.soilSuitabilityAdapter = soilSuitabilityAdapter;
        this.providerExecutor = providerExecutor;
    }

    public CollectedProviderData collect(Region region, LocationResolution location) {
        CompletableFuture<ExternalResult<AsosAdapter.Asos30DaySummary>> asos = submit(
                "ASOS", () -> asosAdapter.get30DaySummary(location.asosStationId()));
        CompletableFuture<ExternalResult<List<ShortForecastAdapter.DailyForecast>>> shortForecast = submit(
                "FORECAST", () -> shortForecastAdapter.getForecast3Days(location.kmaNx(), location.kmaNy()));
        CompletableFuture<ExternalResult<List<MidTermForecastAdapter.DailyForecast>>> midTermForecast = submit(
                "MIDTERM_FORECAST", () -> midTermForecastAdapter.getForecast4To10Days(region.getSidoName(), region.getSigunguCode()));
        CompletableFuture<ExternalResult<SoilChemistryAdapter.SoilChemistryResult>> soilChemistry = submit(
                "SOIL_CHEMISTRY", () -> soilChemistryAdapter.getSoilChemistry(
                        region.getSigunguCode(), region.getSidoName(), region.getSigunguName()));
        CompletableFuture<ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>>> soilSuitability = submit(
                "SOIL_SUITABILITY", () -> soilSuitabilityAdapter.getSoilSuitability(
                        region.getSigunguCode(), region.getSidoName(), region.getSigunguName()));

        CompletableFuture.allOf(asos, shortForecast, midTermForecast, soilChemistry, soilSuitability).join();
        return new CollectedProviderData(asos.join(), shortForecast.join(), midTermForecast.join(),
                soilChemistry.join(), soilSuitability.join());
    }

    private <T> CompletableFuture<ExternalResult<T>> submit(String provider, Supplier<ExternalResult<T>> request) {
        try {
            return CompletableFuture.supplyAsync(() -> invoke(provider, request), providerExecutor);
        } catch (RuntimeException exception) {
            log.warn("provider collection rejected provider={} error={}", provider, exception.getMessage());
            return CompletableFuture.completedFuture(ExternalResult.failure(provider + "_COLLECTION_REJECTED"));
        }
    }

    private <T> ExternalResult<T> invoke(String provider, Supplier<ExternalResult<T>> request) {
        try {
            ExternalResult<T> result = request.get();
            return result == null ? ExternalResult.failure(provider + "_EMPTY_RESULT") : result;
        } catch (RuntimeException exception) {
            log.warn("provider collection failed provider={} error={}", provider, exception.getMessage());
            return ExternalResult.failure(provider + "_COLLECTION_FAILED");
        }
    }

    public record CollectedProviderData(
            ExternalResult<AsosAdapter.Asos30DaySummary> asos,
            ExternalResult<List<ShortForecastAdapter.DailyForecast>> shortForecast,
            ExternalResult<List<MidTermForecastAdapter.DailyForecast>> midTermForecast,
            ExternalResult<SoilChemistryAdapter.SoilChemistryResult> soilChemistry,
            ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> soilSuitability) {
    }
}
