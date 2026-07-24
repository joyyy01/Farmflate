package com.example.aiworkspace.service.external;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalAdaptersContractTest {

    private final ShortForecastAdapter forecast = new ShortForecastAdapter(new RestTemplate(), "fixture-key", 30, 1, "LIVE");
    private final AsosAdapter asos = new AsosAdapter(new RestTemplate(), "fixture-key", 24, 1, "LIVE");
    private final LegalDistrictAdapter legalDistrict = new LegalDistrictAdapter(new RestTemplate(), "fixture-key", 30, 1, "LIVE");
    private final SoilChemistryAdapter soil = new SoilChemistryAdapter(
            new RestTemplate(), "fixture-key", legalDistrict, 30, 1, "LIVE");
    private final CropCodeAdapter cropCodes = new CropCodeAdapter(new RestTemplate(), "fixture-key", 90, 1, "LIVE");

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("mixedSiblingFailures")
    void mixed_sibling_provider_failures_are_failure_with_partial_value(
            String provider, String expectedErrorCode, Supplier<ExternalResult<?>> invocation) {
        ExternalResult<?> result = invocation.get();

        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo(expectedErrorCode);
        assertThat(result.value()).as(provider + " partial value").isNotNull();
        assertThat(result.metrics()).isNotNull();
    }

    @Test
    void non_empty_unusable_soil_rows_are_failure_not_empty() {
        String unusable = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[{"vl":"not-a-number"}]}}}}
                """;

        ExternalResult<Double> result = soil.parse(unusable, "application/json");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo("SOIL_CHEMISTRY_UNUSABLE_RECORDS");
    }

    @Test
    void retry_count_one_retries_a_network_exception_exactly_once() {
        AtomicInteger attempts = new AtomicInteger();

        ExternalResult<String> result = ExternalAdapterSupport.executeRequest(1, "REQUEST_FAILED", () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new ResourceAccessException("fixture timeout");
            }
            return "recovered";
        });

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value()).isEqualTo("recovered");
        assertThat(attempts).hasValue(2);
    }

    private static Stream<Arguments> badBodies() {
        return Stream.of(
                Arguments.of(fixture("forecast-timeout.html"), "text/html"),
                Arguments.of("not-json", "application/json"),
                Arguments.of("{\"response\":{\"header\":{\"resultCode\":\"30\"}}}", "application/json"));
    }

    private static Stream<Arguments> mixedSiblingFailures() {
        return Stream.of(
                Arguments.of("crop-code", "CROP_CODE_PROVIDER_FAILURE",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedCropCodeFailure),
                Arguments.of("soil-chemistry", "SOIL_CHEMISTRY_PROVIDER_FAILURE",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedSoilChemistryFailure),
                Arguments.of("soil-suitability", "SOIL_SUITABILITY_PROVIDER_FAILURE",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedSoilSuitabilityFailure));
    }

    private static ExternalResult<?> mixedCropCodeFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenAnswer(invocation -> switch (calls.getAndIncrement()) {
            case 0 -> cropPayload("사과", "1001");
            case 1 -> throw new ResourceAccessException("fixture timeout");
            case 2 -> cropPayload("오이", "1003");
            case 3 -> cropPayload("감자", "1004");
            default -> cropPayload("상추", "1005");
        });

        return new CropCodeAdapter(restTemplate, "fixture-key", 90, 0, "LIVE").getCropCodeMappings();
    }

    private static ExternalResult<?> mixedSoilChemistryFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 1) {
                throw new ResourceAccessException("fixture timeout");
            }
            return soilPayload("6.0");
        });

        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        return new SoilChemistryAdapter(restTemplate, "fixture-key", legalDistrict, 30, 0, "LIVE")
                .getSoilChemistry("52180", "전북특별자치도", "고창군");
    }

    private static ExternalResult<?> mixedSoilSuitabilityFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 1) {
                throw new ResourceAccessException("fixture timeout");
            }
            return suitabilityPayload("적합", "10");
        });

        CropCodeAdapter cropCodeAdapter = mock(CropCodeAdapter.class);
        Map<String, CropCodeAdapter.CropCodeMapping> mappings = new LinkedHashMap<>();
        mappings.put("APPLE", cropMapping("APPLE", "1001", "사과"));
        mappings.put("PEAR", cropMapping("PEAR", "1002", "배"));
        when(cropCodeAdapter.getCropCodeMappings()).thenReturn(ExternalResult.success(mappings));

        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        return new SoilSuitabilityAdapter(restTemplate, "fixture-key", legalDistrict, cropCodeAdapter, 90, 0, "LIVE")
                .getSoilSuitability("52180", "전북특별자치도", "고창군");
    }

    private static CropCodeAdapter.CropCodeMapping cropMapping(String internalCode, String apiCode, String name) {
        CropCodeAdapter.CropCodeMapping mapping = new CropCodeAdapter.CropCodeMapping();
        mapping.internalCode = internalCode;
        mapping.apiCropCode = apiCode;
        mapping.cropName = name;
        mapping.resolved = true;
        return mapping;
    }

    private static Map<String, Object> cropPayload(String cropName, String cropCode) {
        return responseWithItems(Map.of("crop_Nm", cropName, "soil_Crop_CD", cropCode));
    }

    private static Map<String, Object> soilPayload(String value) {
        return responseWithItems(Map.of("vl", value));
    }

    private static Map<String, Object> suitabilityPayload(String grade, String area) {
        return responseWithItems(Map.of("soil_Grd_Nm", grade, "soil_Grd_Area", area));
    }

    private static Map<String, Object> responseWithItems(Map<String, Object> item) {
        return Map.of("response", Map.of(
                "header", Map.of("resultCode", "00"),
                "body", Map.of("items", Map.of("item", List.of(item)))));
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
