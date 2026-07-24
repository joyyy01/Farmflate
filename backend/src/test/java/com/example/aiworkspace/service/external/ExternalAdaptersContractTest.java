package com.example.aiworkspace.service.external;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void valid_xml_crop_variants_are_not_misclassified_as_a_provider_failure() {
        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> result = cropCodes.parse(cropVariantsXml(), "application/xml");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.EMPTY);
        assertThat(result.errorCode()).isNull();
    }

    @Test
    void crop_code_parser_reads_the_provider_crop_cd_field_when_an_exact_crop_exists() {
        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> result = cropCodes.parse("""
                <response><header><result_Code>200</result_Code></header>
                <body><items><item><crop_Cd>00017</crop_Cd><crop_Nm>감자</crop_Nm></item></items></body>
                </response>
                """, "application/xml");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value().get("POTATO").apiCropCode).isEqualTo("00017");
        assertThat(result.value().get("POTATO").resolved).isTrue();
    }

    @Test
    void valid_crop_xml_transport_with_only_variants_is_empty_not_a_provider_failure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(cropVariantsXml());

        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> result =
                new CropCodeAdapter(restTemplate, "fixture-key", 90, 0, "LIVE").getCropCodeMappings();

        assertThat(result.status()).isEqualTo(ExternalResult.Status.EMPTY);
        assertThat(result.errorCode()).isNull();
        verify(restTemplate, times(5)).getForObject(anyString(), eq(String.class));
    }

    @Test
    void provider_200_with_an_error_code_remains_a_failure() {
        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> result = cropCodes.parse("""
                <response><header><result_Code>201</result_Code><result_Msg>요청변수 형식이 일치하지 않은 경우</result_Msg></header></response>
                """, "application/xml");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo("CROP_CODE_PROVIDER_201");
    }

    @Test
    void malformed_xml_is_not_normalized_as_empty() {
        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> result = cropCodes.parse("<response>", "application/xml");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo("MALFORMED_PROVIDER_RESPONSE");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("legalDistrictPayloads")
    void mislabeled_json_legal_district_response_is_parsed_by_its_payload_not_its_content_type(
            String sidoName, String sigunguName, String regionCode) {
        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> result = legalDistrict.parse(
                legalDistrictJson(sidoName, sigunguName, regionCode), "text/html;charset=UTF-8");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value()).extracting(district -> district.regionCd).containsExactly(regionCode);
    }

    @ParameterizedTest(name = "encoded {0} {1}")
    @MethodSource("legalDistrictPayloads")
    void legal_district_request_reads_text_and_preserves_special_region_names(
            String sidoName, String sigunguName, String regionCode) {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(legalDistrictJson(sidoName, sigunguName, regionCode));

        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> result =
                new LegalDistrictAdapter(restTemplate, "fixture-key", 30, 0, "LIVE")
                        .getDistrictCodes(sidoName, sigunguName);

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        org.mockito.ArgumentCaptor<String> url = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate).getForObject(url.capture(), eq(String.class));
        assertThat(URLDecoder.decode(url.getValue(), StandardCharsets.UTF_8))
                .contains("locatadd_nm=" + sidoName + " " + sigunguName);
    }

    @Test
    void area_only_soil_statistics_are_explicitly_unsupported_for_ph() {
        ExternalResult<Double> result = soil.parse(areaOnlySoilXml(), "application/xml");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH");
    }

    @Test
    void soil_chemistry_uses_authoritative_legal_dong_instead_of_invalid_sigungu_input() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        LegalDistrictAdapter.LegalDistrict district = new LegalDistrictAdapter.LegalDistrict();
        district.regionCd = "4111710600";
        when(legalDistrict.getDistrictCodes("경기도", "수원시")).thenReturn(ExternalResult.success(List.of(district)));
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(areaOnlySoilXml());

        ExternalResult<SoilChemistryAdapter.SoilChemistryResult> result =
                new SoilChemistryAdapter(restTemplate, "fixture-key", legalDistrict, 30, 0, "LIVE")
                        .getSoilChemistry("41110", "경기도", "수원시");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH");
        org.mockito.ArgumentCaptor<String> urls = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1)).getForObject(urls.capture(), eq(String.class));
        assertThat(urls.getAllValues()).allSatisfy(url -> assertThat(url).contains("STDG_CD=4111710600"));
    }

    @Test
    void official_soil_fit_no_data_is_empty_after_legal_dong_resolution() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        CropCodeAdapter cropCodeAdapter = mock(CropCodeAdapter.class);
        LegalDistrictAdapter.LegalDistrict district = new LegalDistrictAdapter.LegalDistrict();
        district.regionCd = "4111710600";
        CropCodeAdapter.CropCodeMapping mapping = cropMapping("POTATO", "00017", "감자(남부,봄재배)");
        when(cropCodeAdapter.getCropCodeMappings()).thenReturn(ExternalResult.success(Map.of("POTATO", mapping)));
        when(legalDistrict.getDistrictCodes("경기도", "수원시")).thenReturn(ExternalResult.success(List.of(district)));
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(soilFitNoDataXml());

        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> result =
                new SoilSuitabilityAdapter(restTemplate, "fixture-key", legalDistrict, cropCodeAdapter, 90, 0, "LIVE")
                        .getSoilSuitability("41110", "경기도", "수원시");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.EMPTY);
        org.mockito.ArgumentCaptor<String> urls = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1)).getForObject(urls.capture(), eq(String.class));
        assertThat(urls.getAllValues()).allSatisfy(url -> assertThat(url).contains("STDG_CD=4111710600"));
    }

    @Test
    void absent_authoritative_legal_dong_is_a_location_limitation_not_a_provider_parse_failure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        when(legalDistrict.getDistrictCodes("경기도", "수원시")).thenReturn(ExternalResult.empty());

        ExternalResult<SoilChemistryAdapter.SoilChemistryResult> chemistry =
                new SoilChemistryAdapter(restTemplate, "fixture-key", legalDistrict, 30, 0, "LIVE")
                        .getSoilChemistry("41110", "경기도", "수원시");

        CropCodeAdapter cropCodeAdapter = mock(CropCodeAdapter.class);
        when(cropCodeAdapter.getCropCodeMappings()).thenReturn(ExternalResult.success(
                Map.of("POTATO", cropMapping("POTATO", "00017", "감자(남부,봄재배)"))));
        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> suitability =
                new SoilSuitabilityAdapter(restTemplate, "fixture-key", legalDistrict, cropCodeAdapter, 90, 0, "LIVE")
                        .getSoilSuitability("41110", "경기도", "수원시");

        assertThat(chemistry.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(chemistry.errorCode()).isEqualTo("SOIL_CHEMISTRY_LOCATION_NOT_RESOLVED");
        assertThat(suitability.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(suitability.errorCode()).isEqualTo("SOIL_SUITABILITY_LOCATION_NOT_RESOLVED");
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

    private static Stream<Arguments> legalDistrictPayloads() {
        return Stream.of(
                Arguments.of("경기도", "수원시", "4111710600"),
                Arguments.of("강원특별자치도", "강릉시", "4215010100"),
                Arguments.of("제주특별자치도", "제주시", "5011010100"));
    }

    private static Stream<Arguments> mixedSiblingFailures() {
        return Stream.of(
                Arguments.of("crop-code", "CROP_CODE_REQUEST_FAILED",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedCropCodeFailure),
                Arguments.of("soil-chemistry", "SOIL_CHEMISTRY_PROVIDER_FAILURE",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedSoilChemistryFailure),
                Arguments.of("soil-suitability", "SOIL_SUITABILITY_PROVIDER_FAILURE",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedSoilSuitabilityFailure));
    }

    private static ExternalResult<?> mixedCropCodeFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenAnswer(invocation -> switch (calls.getAndIncrement()) {
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
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 1) {
                throw new ResourceAccessException("fixture timeout");
            }
            return soilPayload("6.0");
        });

        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        when(legalDistrict.getDistrictCodes("전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(List.of(legalDistrict("5279031000"))));
        return new SoilChemistryAdapter(restTemplate, "fixture-key", legalDistrict, 30, 0, "LIVE")
                .getSoilChemistry("52180", "전북특별자치도", "고창군");
    }

    private static ExternalResult<?> mixedSoilSuitabilityFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenAnswer(invocation -> {
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
        when(legalDistrict.getDistrictCodes("전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(List.of(legalDistrict("5279031000"))));
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

    private static LegalDistrictAdapter.LegalDistrict legalDistrict(String regionCode) {
        LegalDistrictAdapter.LegalDistrict district = new LegalDistrictAdapter.LegalDistrict();
        district.regionCd = regionCode;
        return district;
    }

    private static String cropPayload(String cropName, String cropCode) {
        return "<response><header><result_Code>200</result_Code></header><body><items><item>"
                + "<crop_Nm>" + cropName + "</crop_Nm><crop_Cd>" + cropCode + "</crop_Cd>"
                + "</item></items></body></response>";
    }

    private static String soilPayload(String value) {
        return "<response><header><result_Code>200</result_Code></header><body><items><item>"
                + "<vl>" + value + "</vl></item></items></body></response>";
    }

    private static String suitabilityPayload(String grade, String area) {
        return "<response><header><result_Code>200</result_Code></header><body><items><item>"
                + "<soil_Grd_Nm>" + grade + "</soil_Grd_Nm><soil_Grd_Area>" + area + "</soil_Grd_Area>"
                + "</item></items></body></response>";
    }

    private static String cropVariantsXml() {
        return """
                <response>
                  <header><result_Code>200</result_Code><result_Msg>정상</result_Msg></header>
                  <body><items><item><crop_Cd>00061</crop_Cd><crop_Nm>사과(1-4년생)</crop_Nm></item></items></body>
                </response>
                """;
    }

    private static String legalDistrictJson(String sidoName, String sigunguName, String regionCode) {
        return """
                {"StanReginCd":[{"row":[{"region_cd":"%s","locatadd_nm":"%s %s","locallow_nm":"fixture","ri_cd":"00","use_yn":"Y"}]}]}
                """.formatted(regionCode, sidoName, sigunguName);
    }

    private static String areaOnlySoilXml() {
        return """
                <response>
                  <header><result_Code>200</result_Code><result_Msg>정상</result_Msg></header>
                  <body><items><item><stdg_Cd>4111710600</stdg_Cd><acid_Rfld1_Area>1</acid_Rfld1_Area><acid_Rfld2_Area>0</acid_Rfld2_Area></item></items></body>
                </response>
                """;
    }

    private static String soilFitNoDataXml() {
        return """
                <response>
                  <header><result_Code>301</result_Code><result_Msg>요청 데이터 없음</result_Msg></header>
                  <body><items/></body>
                </response>
                """;
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
