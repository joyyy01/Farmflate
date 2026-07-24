package com.example.aiworkspace.service.external;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalAdaptersContractTest {

    private final ShortForecastAdapter forecast = new ShortForecastAdapter(new RestTemplate(), "fixture-key", 30, "LIVE");
    private final AsosAdapter asos = new AsosAdapter(new RestTemplate(), "fixture-key", 24, "LIVE");
    private final LegalDistrictAdapter legalDistrict = new LegalDistrictAdapter(new RestTemplate(), "fixture-key", 30, "LIVE");
    private final SoilChemistryAdapter soil = new SoilChemistryAdapter(
            new RestTemplate(), "fixture-key", legalDistrict, 30, "LIVE");
    private final CropCodeAdapter cropCodes = new CropCodeAdapter(new RestTemplate(), "fixture-key", 90, "LIVE");

    @Test
    void fixture_normalizers_always_return_non_null_typed_results() {
        ExternalResult<List<ShortForecastAdapter.DailyForecast>> forecastResult =
                forecast.parse(fixture("gochang-normal.json"), "application/json");
        ExternalResult<AsosAdapter.Asos30DaySummary> asosResult =
                asos.parse(fixture("asos-empty.json"), "application/json");
        ExternalResult<Double> soilResult = soil.parse(fixture("soil-ph-empty.json"), "application/json");
        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> cropResult =
                cropCodes.parse(fixture("crop-code-one-unresolved.json"), "application/json");

        assertThat(forecastResult).isNotNull();
        assertThat(forecastResult.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(forecastResult.value()).isNotEmpty();
        assertThat(forecastResult.metrics()).isNotEmpty();

        assertThat(asosResult).isNotNull();
        assertThat(asosResult.status()).isEqualTo(ExternalResult.Status.EMPTY);
        assertThat(asosResult.metrics()).isNotNull();

        assertThat(soilResult).isNotNull();
        assertThat(soilResult.status()).isEqualTo(ExternalResult.Status.EMPTY);
        assertThat(soilResult.metrics()).isNotNull();

        assertThat(cropResult).isNotNull();
        assertThat(cropResult.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(cropResult.value().get("LETTUCE").resolved).isFalse();
        assertThat(cropResult.metrics()).anySatisfy(metric ->
                assertThat(metric.validationFlags()).contains("UNRESOLVED"));
    }

    @ParameterizedTest
    @MethodSource("badBodies")
    void provider_failure_is_not_normalized_as_empty(String body, String contentType) {
        ExternalResult<List<ShortForecastAdapter.DailyForecast>> result = forecast.parse(body, contentType);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isNotBlank();
    }

    @Test
    void pcp_ranges_and_unknown_values_normalize_deterministically() {
        assertThat(forecast.parsePcp("30.0~50.0mm")).isEqualTo(40.0);
        assertThat(forecast.parsePcp("알 수 없음")).isNull();
    }

    private static Stream<Arguments> badBodies() {
        return Stream.of(
                Arguments.of(fixture("forecast-timeout.html"), "text/html"),
                Arguments.of("not-json", "application/json"),
                Arguments.of("{\"response\":{\"header\":{\"resultCode\":\"30\"}}}", "application/json"));
    }

    private static String fixture(String name) {
        String path = "fixtures/regions/" + name;
        try (InputStream input = ExternalAdaptersContractTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing fixture: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read fixture: " + path, exception);
        }
    }
}
