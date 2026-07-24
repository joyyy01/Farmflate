package com.example.aiworkspace.service.external;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private final CropCodeAdapter cropCodes = new CropCodeAdapter(90, "LIVE");

    @Test
    void fixture_normalizers_always_return_non_null_typed_results() {
        ExternalResult<List<ShortForecastAdapter.DailyForecast>> forecastResult =
                forecast.parse(fixture("gochang-normal.json"), "application/json");
        ExternalResult<AsosAdapter.Asos30DaySummary> asosResult =
                asos.parse(fixture("asos-empty.json"), "application/json");
        ExternalResult<Double> soilResult = soil.parse(fixture("soil-ph-empty.json"), "application/json");

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
    void provider_soil_fit_catalog_uses_verified_soil_fit_codes_not_crop_info_numeric_codes() {
        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> result =
                new CropCodeAdapter(90, "LIVE").getCropCodeMappings();

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value().get("APPLE").apiCropCode).isEqualTo("CR005");
        assertThat(result.value().get("PEAR").apiCropCode).isEqualTo("CR006");
        assertThat(result.value().get("CUCUMBER").apiCropCode).isEqualTo("CR017");
        assertThat(result.value().get("POTATO").apiCropCode).isEqualTo("CR032");
        assertThat(result.value().get("LETTUCE").resolved).isFalse();
        assertThat(result.metrics()).anySatisfy(metric ->
                assertThat(metric.validationFlags()).contains("UNSUPPORTED_BY_SOIL_FIT_CATALOG"));
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
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(legalDistrictJson(sidoName, sigunguName, regionCode));

        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> result =
                new LegalDistrictAdapter(restTemplate, "fixture-key", 30, 0, "LIVE")
                        .getDistrictCodes(sidoName, sigunguName);

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        org.mockito.ArgumentCaptor<URI> url = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(url.capture(), eq(String.class));
        assertThat(URLDecoder.decode(url.getValue().toString(), StandardCharsets.UTF_8))
                .contains("locatadd_nm=" + sidoName + " " + sigunguName);
        assertThat(url.getValue().getRawQuery()).doesNotContain("%25");
    }

    @Test
    void stan_regin_cd_head_then_row_wrapper_is_normalized_when_the_envelope_is_an_object() {
        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> result = legalDistrict.parse(
                legalDistrictHeadThenRowObjectJson("강원특별자치도", "강릉시", "5115035000"),
                "text/html;charset=UTF-8");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value()).extracting(district -> district.regionCd).containsExactly("5115035000");
    }

    @Test
    void detailed_soil_exam_record_reads_observed_acid_as_ph() {
        ExternalResult<Double> result = soil.parse(soilExamPayload("6.2"), "application/xml");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value()).isEqualTo(6.2);
    }

    @Test
    void soil_chemistry_uses_authoritative_legal_dong_and_detailed_soil_exam_contract() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        LegalDistrictAdapter.LegalDistrict district = new LegalDistrictAdapter.LegalDistrict();
        district.regionCd = "4111710600";
        when(legalDistrict.getDistrictCodes("경기도", "수원시")).thenReturn(ExternalResult.success(List.of(district)));
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(soilExamPayload("6.2"));

        ExternalResult<SoilChemistryAdapter.SoilChemistryResult> result =
                new SoilChemistryAdapter(restTemplate, "fixture-key", legalDistrict, 30, 0, "LIVE")
                        .getSoilChemistry("41110", "경기도", "수원시");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value().ph).isEqualTo(6.2);
        org.mockito.ArgumentCaptor<URI> urls = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, times(1)).getForObject(urls.capture(), eq(String.class));
        assertThat(urls.getAllValues()).allSatisfy(uri -> assertThat(uri.toString())
                .contains("SoilExam/V2/getSoilExamList", "STDG_CD=4111710600", "Page_Size=1", "Page_No=1"));
    }

    @Test
    void soil_chemistry_keeps_real_records_when_a_bounded_legal_dong_sample_contains_official_no_data() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        when(legalDistrict.getDistrictCodes("강원특별자치도", "강릉시"))
                .thenReturn(ExternalResult.success(List.of(legalDistrict("5115011900"), legalDistrict("5115012800"))));
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(soilExamPayload("5.6"), soilExamNoDataXml());

        ExternalResult<SoilChemistryAdapter.SoilChemistryResult> result =
                new SoilChemistryAdapter(restTemplate, "fixture-key", legalDistrict, 30, 0, "LIVE")
                        .getSoilChemistry("42150", "강원특별자치도", "강릉시");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value().ph).isEqualTo(5.6);
        assertThat(result.value().coveredDongs).isEqualTo(1);
        assertThat(result.value().totalDongs).isEqualTo(2);
    }

    @Test
    void official_soil_fit_no_data_is_empty_after_legal_dong_resolution() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        CropCodeAdapter cropCodeAdapter = mock(CropCodeAdapter.class);
        LegalDistrictAdapter.LegalDistrict district = new LegalDistrictAdapter.LegalDistrict();
        district.regionCd = "4111710600";
        CropCodeAdapter.CropCodeMapping mapping = cropMapping("POTATO", "CR032", "감자");
        when(cropCodeAdapter.getCropCodeMappings()).thenReturn(ExternalResult.success(Map.of("POTATO", mapping)));
        when(legalDistrict.getDistrictCodes("경기도", "수원시")).thenReturn(ExternalResult.success(List.of(district)));
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(soilFitNoDataXml());

        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> result =
                new SoilSuitabilityAdapter(restTemplate, "fixture-key", legalDistrict, cropCodeAdapter, 90, 0, "LIVE")
                        .getSoilSuitability("41110", "경기도", "수원시");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.EMPTY);
        org.mockito.ArgumentCaptor<URI> urls = org.mockito.ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, times(1)).getForObject(urls.capture(), eq(String.class));
        assertThat(urls.getAllValues()).allSatisfy(uri -> assertThat(uri.toString()).contains("STDG_CD=4111710600"));
    }

    @Test
    void soil_fit_v2_area_band_fields_are_normalized_into_scored_canonical_grades() {
        CropCodeAdapter cropCodeAdapter = mock(CropCodeAdapter.class);
        SoilSuitabilityAdapter adapter = new SoilSuitabilityAdapter(
                new RestTemplate(), "fixture-key", mock(LegalDistrictAdapter.class), cropCodeAdapter, 90, 0, "LIVE");

        ExternalResult<Map<String, Double>> result = adapter.parse(soilFitAreaBandsXml(), "application/xml");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value()).containsEntry("매우적합", 10.0)
                .containsEntry("적합", 20.0)
                .containsEntry("가능", 30.0)
                .containsEntry("낮음", 40.0)
                .containsEntry("기타", 50.0);
        assertThat(adapter.scoreForGrade("기타")).isNotNull();
    }

    @Test
    void soil_fit_v2_response_name_mismatch_is_never_scored_as_the_requested_crop() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CropCodeAdapter cropCodeAdapter = mock(CropCodeAdapter.class);
        CropCodeAdapter.CropCodeMapping mapping = cropMapping("POTATO", "CR032", "감자");
        when(cropCodeAdapter.getCropCodeMappings()).thenReturn(ExternalResult.success(Map.of("POTATO", mapping)));
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(soilFitAreaBandsXml("복숭아"));

        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> result =
                new SoilSuitabilityAdapter(restTemplate, "fixture-key", mock(LegalDistrictAdapter.class), cropCodeAdapter, 90, 0, "LIVE")
                        .getSoilSuitability("5115035000", "강원특별자치도", "강릉시");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.FAILURE);
        assertThat(result.errorCode()).isEqualTo("SOIL_SUITABILITY_CROP_NAME_MISMATCH");
        assertThat(result.value().get("POTATO").hasData).isFalse();
        assertThat(result.value().get("POTATO").gradeAreas).isEmpty();
    }

    @Test
    void soil_fit_v2_response_name_matching_the_catalog_crop_can_be_scored() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        CropCodeAdapter cropCodeAdapter = mock(CropCodeAdapter.class);
        CropCodeAdapter.CropCodeMapping mapping = cropMapping("POTATO", "CR032", "감자");
        when(cropCodeAdapter.getCropCodeMappings()).thenReturn(ExternalResult.success(Map.of("POTATO", mapping)));
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(soilFitAreaBandsXml("감자"));

        ExternalResult<Map<String, SoilSuitabilityAdapter.SoilSuitabilityResult>> result =
                new SoilSuitabilityAdapter(restTemplate, "fixture-key", mock(LegalDistrictAdapter.class), cropCodeAdapter, 90, 0, "LIVE")
                        .getSoilSuitability("5115035000", "강원특별자치도", "강릉시");

        assertThat(result.status()).isEqualTo(ExternalResult.Status.SUCCESS);
        assertThat(result.value().get("POTATO").hasData).isTrue();
        assertThat(result.value().get("POTATO").score).isGreaterThan(0);
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

    @Test
    void provider_paced_request_retries_a_rate_limit_without_treating_it_as_no_data() {
        AtomicInteger attempts = new AtomicInteger();

        ExternalResult<String> result = ExternalAdapterSupport.executePacedRequest(
                "fixture-rda-soil-rate-limit", 0, 1, "REQUEST_FAILED", () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
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
                Arguments.of("soil-chemistry", "SOIL_CHEMISTRY_PROVIDER_FAILURE",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedSoilChemistryFailure),
                Arguments.of("soil-suitability", "SOIL_SUITABILITY_PROVIDER_FAILURE",
                        (Supplier<ExternalResult<?>>) ExternalAdaptersContractTest::mixedSoilSuitabilityFailure));
    }

    private static ExternalResult<?> mixedSoilChemistryFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 1) {
                throw new ResourceAccessException("fixture timeout");
            }
            return soilPayload("6.0");
        });

        LegalDistrictAdapter legalDistrict = mock(LegalDistrictAdapter.class);
        when(legalDistrict.getDistrictCodes("전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(List.of(legalDistrict("5279031000"), legalDistrict("5279032000"))));
        return new SoilChemistryAdapter(restTemplate, "fixture-key", legalDistrict, 30, 0, "LIVE")
                .getSoilChemistry("52180", "전북특별자치도", "고창군");
    }

    private static ExternalResult<?> mixedSoilSuitabilityFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 1) {
                throw new ResourceAccessException("fixture timeout");
            }
            return suitabilityPayload("사과", "적합", "10");
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
        return soilExamPayload(value);
    }

    private static String soilExamPayload(String acid) {
        return "<response><header><Result_Code>200</Result_Code></header><body><items><item>"
                + "<ACID>" + acid + "</ACID><OM>24.0</OM><VLDPHA>180.0</VLDPHA>"
                + "<POSIFERT_K>0.42</POSIFERT_K><POSIFERT_CA>5.6</POSIFERT_CA>"
                + "<POSIFERT_MG>1.4</POSIFERT_MG></item></items></body></response>";
    }

    private static String soilExamNoDataXml() {
        return "<response><header><Result_Code>301</Result_Code><Result_Msg>요청 데이터 없음</Result_Msg></header></response>";
    }

    private static String suitabilityPayload(String cropName, String grade, String area) {
        return "<response><header><result_Code>200</result_Code></header><body><items><item>"
                + "<soil_Crop_Nm>" + cropName + "</soil_Crop_Nm>"
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
                {"StanReginCd":[
                  {"head":[{"totalCount":1},{"result":{"code":"INFO-000"}}]},
                  {"row":[{"region_cd":"%s","locatadd_nm":"%s %s","locallow_nm":"fixture","ri_cd":"00","use_yn":"Y"}]}
                ]}
                """.formatted(regionCode, sidoName, sigunguName);
    }

    private static String legalDistrictHeadThenRowObjectJson(String sidoName, String sigunguName, String regionCode) {
        return """
                {"StanReginCd":{"head":[{"totalCount":2},{"result":{"code":"INFO-0"}}],"row":[
                  {"region_cd":"%s","locatadd_nm":"%s %s","locallow_nm":"fixture","ri_cd":"00"},
                  {"region_cd":"5115034030","locatadd_nm":"%s %s 강동면 산성우리","locallow_nm":"산성우리","ri_cd":"30"}
                ]}}
                """.formatted(regionCode, sidoName, sigunguName, sidoName, sigunguName);
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

    private static String soilFitAreaBandsXml() {
        return soilFitAreaBandsXml("감자");
    }

    private static String soilFitAreaBandsXml(String cropName) {
        return """
                <response>
                  <header><Result_Code>200</Result_Code></header>
                  <body><items><item>
                    <soil_Crop_Nm>%s</soil_Crop_Nm>
                    <high_Suit_Area>10</high_Suit_Area><suit_Area>20</suit_Area>
                    <poss_Area>30</poss_Area><low_Suit_Area>40</low_Suit_Area><etc_Area>50</etc_Area>
                  </item></items></body>
                </response>
                """.formatted(cropName);
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
