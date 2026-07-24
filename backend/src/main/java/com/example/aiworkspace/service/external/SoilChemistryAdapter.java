package com.example.aiworkspace.service.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** RDA farmland chemistry normalizer with legal-dong aggregation fallback. */
@Slf4j
@Component
public class SoilChemistryAdapter {

    private static final String BASE_URL = "http://apis.data.go.kr/1390802/SoilEnviron/SoilExamStat/V2";
    private static final String PROVIDER = "RDA";
    private static final String SERVICE = "SoilExamStat/V2";
    private static final List<String> OPERATIONS = List.of(
            "getFarmExamPhInfo", "getFarmExamOmInfo", "getFarmExamApInfo",
            "getFarmExamKalInfo", "getFarmExamCalInfo", "getFarmExamMgInfo", "getFarmExamSaInfo");
    private static final List<String> METRICS = List.of(
            "soil.ph", "soil.organic_matter", "soil.available_phosphate", "soil.potassium",
            "soil.calcium", "soil.magnesium", "soil.ec");
    private static final List<String> UNITS = List.of("pH", "g/kg", "mg/kg", "cmol+/kg", "cmol+/kg", "cmol+/kg", "dS/m");

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final LegalDistrictAdapter legalDistrictAdapter;
    private final int cacheDays;
    private final int retryCount;
    private final boolean replay;
    private final Map<String, CachedSoil> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ExternalResult<SoilChemistryResult>>> inFlight = new ConcurrentHashMap<>();

    public SoilChemistryAdapter(
            @Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.data-go-kr.service-key}") String serviceKey,
            LegalDistrictAdapter legalDistrictAdapter,
            @Value("${app.cache.soil-chemistry-days:30}") int cacheDays,
            @Value("${app.external-api.retry-count:1}") int retryCount,
            @Value("${app.data-mode:LIVE}") String dataMode) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.legalDistrictAdapter = legalDistrictAdapter;
        this.cacheDays = cacheDays;
        this.retryCount = retryCount;
        this.replay = "REPLAY".equalsIgnoreCase(dataMode);
    }

    public static class SoilChemistryResult {
        public Double ph;
        public Double organicMatter;
        public Double availablePhosphate;
        public Double potassium;
        public Double calcium;
        public Double magnesium;
        public Double ec;
        public String spatialLevel = "SIGUNGU_AGGREGATE";
        public int coveredDongs;
        public int totalDongs;
        public int outliersExcluded;
        public boolean partial;
    }

    private record CachedSoil(ExternalResult<SoilChemistryResult> result, Instant cachedAt) {
    }

    public ExternalResult<SoilChemistryResult> getSoilChemistry(
            String sigunguCode, String sidoName, String sigunguName) {
        String cacheKey = "soil_chem_" + sigunguCode;
        return ExternalAdapterSupport.executeOnce(inFlight, cacheKey,
                () -> loadSoilChemistry(sigunguCode, sidoName, sigunguName, cacheKey));
    }

    /** Testable raw-payload boundary for fixture-backed contract tests. */
    public ExternalResult<Double> parse(String body, String contentType) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseJsonObject(body, contentType);
        if (parsed.isFailure()) {
            return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        }
        return extractSoilValue(parsed.value());
    }

    private ExternalResult<SoilChemistryResult> loadSoilChemistry(
            String sigunguCode, String sidoName, String sigunguName, String cacheKey) {
        CachedSoil cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).toDays() < cacheDays) {
            return cached.result().asCached();
        }

        ExternalResult<SoilChemistryResult> direct = fetchDirect(sigunguCode);
        if (direct.isFailure()) {
            if (direct.value() != null) {
                direct.value().spatialLevel = "SIGUNGU_AGGREGATE";
                return ExternalResult.failure(direct.errorCode(), direct.value(),
                        metricsFor(direct.value(), sigunguCode, false));
            }
            return direct;
        }
        if (direct.isSuccess() && direct.value().ph != null) {
            SoilChemistryResult value = direct.value();
            value.spatialLevel = "SIGUNGU_AGGREGATE";
            ExternalResult<SoilChemistryResult> result = ExternalResult.success(
                    value, metricsFor(value, sigunguCode, false));
            cache.put(cacheKey, new CachedSoil(result, Instant.now()));
            return result;
        }

        ExternalResult<SoilChemistryResult> fallback = fetchLegalDongAggregate(sidoName, sigunguName);
        if (fallback.isFailure()) {
            if (fallback.value() != null) {
                fallback.value().spatialLevel = "LEGAL_DONG_AGGREGATE";
                return ExternalResult.failure(fallback.errorCode(), fallback.value(),
                        metricsFor(fallback.value(), sigunguCode, true));
            }
            return fallback;
        }
        if (fallback.isEmpty()) {
            if (direct.isSuccess() && direct.value() != null) {
                SoilChemistryResult value = direct.value();
                value.spatialLevel = "SIGUNGU_AGGREGATE";
                value.partial = true;
                ExternalResult<SoilChemistryResult> result = ExternalResult.success(
                        value, metricsFor(value, sigunguCode, false));
                cache.put(cacheKey, new CachedSoil(result, Instant.now()));
                return result;
            }
            return fallback;
        }
        SoilChemistryResult value = fallback.value();
        value.spatialLevel = "LEGAL_DONG_AGGREGATE";
        ExternalResult<SoilChemistryResult> result = ExternalResult.success(
                value, metricsFor(value, sigunguCode, true));
        cache.put(cacheKey, new CachedSoil(result, Instant.now()));
        return result;
    }

    private ExternalResult<SoilChemistryResult> fetchDirect(String regionCode) {
        SoilChemistryResult result = new SoilChemistryResult();
        int failures = 0;
        int values = 0;
        int unusable = 0;
        for (int index = 0; index < OPERATIONS.size(); index++) {
            ExternalResult<Double> value = callSoilApi(OPERATIONS.get(index), regionCode);
            if (value.isFailure()) {
                failures++;
                continue;
            }
            if (value.isSuccess()) {
                if (setMetricValue(result, index, value.value())) {
                    values++;
                } else {
                    unusable++;
                }
            }
        }
        if (failures > 0) {
            result.partial = true;
            return ExternalResult.failure("SOIL_CHEMISTRY_PROVIDER_FAILURE", result, List.of());
        }
        if (values > 0) {
            return ExternalResult.success(result);
        }
        return unusable > 0
                ? ExternalResult.failure("SOIL_CHEMISTRY_UNUSABLE_RECORDS", result, List.of())
                : ExternalResult.empty();
    }

    private ExternalResult<SoilChemistryResult> fetchLegalDongAggregate(String sidoName, String sigunguName) {
        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> legal = legalDistrictAdapter.getDistrictCodes(sidoName, sigunguName);
        if (legal.isFailure()) {
            return ExternalResult.failure(legal.errorCode(), legal.metrics());
        }
        if (legal.isEmpty()) {
            return ExternalResult.empty(legal.metrics());
        }

        SoilChemistryResult result = new SoilChemistryResult();
        result.totalDongs = legal.value().size();
        List<List<Double>> valuesByMetric = new ArrayList<>();
        for (int index = 0; index < OPERATIONS.size(); index++) {
            valuesByMetric.add(new ArrayList<>());
        }
        int covered = 0;
        int failures = 0;
        int unusable = 0;
        for (LegalDistrictAdapter.LegalDistrict district : legal.value()) {
            boolean anyValue = false;
            for (int index = 0; index < OPERATIONS.size(); index++) {
                ExternalResult<Double> value = callSoilApi(OPERATIONS.get(index), district.regionCd);
                if (value.isFailure()) {
                    failures++;
                } else if (value.isSuccess()) {
                    if (isValidMetric(index, value.value())) {
                        valuesByMetric.get(index).add(value.value());
                        anyValue = true;
                    } else {
                        result.outliersExcluded++;
                        unusable++;
                    }
                }
            }
            if (anyValue) {
                covered++;
            }
        }
        result.coveredDongs = covered;
        result.partial = covered < result.totalDongs || failures > 0;
        int metricsWithValues = 0;
        for (int index = 0; index < valuesByMetric.size(); index++) {
            List<Double> values = valuesByMetric.get(index);
            if (!values.isEmpty()) {
                setMetricValue(result, index, values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
                metricsWithValues++;
            }
        }
        if (failures > 0) {
            return ExternalResult.failure("SOIL_CHEMISTRY_PROVIDER_FAILURE", result, List.of());
        }
        if (metricsWithValues > 0) {
            return ExternalResult.success(result);
        }
        return unusable > 0
                ? ExternalResult.failure("SOIL_CHEMISTRY_UNUSABLE_RECORDS", result, List.of())
                : ExternalResult.empty();
    }

    @SuppressWarnings("unchecked")
    private ExternalResult<Double> callSoilApi(String operation, String regionCode) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/" + operation)
                .queryParam("serviceKey", serviceKey)
                .queryParam("STDG_CD", regionCode)
                .build(false)
                .toUriString();
        ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.executeRequest(
                retryCount, "SOIL_CHEMISTRY_REQUEST_FAILED", () -> restTemplate.getForObject(url, Map.class));
        if (response.isFailure()) {
            log.debug("Soil API {} failed for {}: {}", operation, regionCode, response.errorCode());
            return ExternalResult.failure(response.errorCode(), response.metrics());
        }
        return extractSoilValue(response.value());
    }

    private ExternalResult<Double> extractSoilValue(Map<String, Object> response) {
        if (response == null) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        Map<String, Object> envelope = ExternalAdapterSupport.map(response.get("response"));
        if (envelope != null) {
            Map<String, Object> header = ExternalAdapterSupport.map(envelope.get("header"));
            if (header != null && !"00".equals(String.valueOf(header.get("resultCode")))) {
                return ExternalResult.failure("SOIL_CHEMISTRY_PROVIDER_" + header.get("resultCode"));
            }
        }
        Map<String, Object> body = findBody(response);
        if (body == null) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        Map<String, Object> items = ExternalAdapterSupport.map(body.get("items"));
        if (items == null) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        List<Map<String, Object>> rows = ExternalAdapterSupport.mapList(items.get("item"));
        if (rows.isEmpty()) {
            return ExternalResult.empty();
        }
        for (Map<String, Object> row : rows) {
            Double value = extractMainValue(row);
            if (value != null) {
                return ExternalResult.success(value);
            }
        }
        return ExternalResult.failure("SOIL_CHEMISTRY_UNUSABLE_RECORDS");
    }

    private Map<String, Object> findBody(Map<String, Object> response) {
        Map<String, Object> envelope = ExternalAdapterSupport.map(response.get("response"));
        if (envelope != null) {
            return ExternalAdapterSupport.map(envelope.get("body"));
        }
        return ExternalAdapterSupport.map(response.get("body"));
    }

    private Double extractMainValue(Map<String, Object> row) {
        for (String key : List.of("vl", "Mean", "mean", "MEAN", "value", "avgVal", "Vl")) {
            Double value = parseDouble(row.get(key));
            if (value != null) {
                return value;
            }
        }
        for (Object candidate : row.values()) {
            Double value = parseDouble(candidate);
            if (value != null && value >= 0 && value < 10000) {
                return value;
            }
        }
        return null;
    }

    private boolean setMetricValue(SoilChemistryResult result, int index, Double value) {
        if (!isValidMetric(index, value)) {
            if (value != null) {
                result.outliersExcluded++;
            }
            return false;
        }
        switch (index) {
            case 0 -> result.ph = value;
            case 1 -> result.organicMatter = value;
            case 2 -> result.availablePhosphate = value;
            case 3 -> result.potassium = value;
            case 4 -> result.calcium = value;
            case 5 -> result.magnesium = value;
            case 6 -> result.ec = value;
            default -> throw new IllegalArgumentException("Unexpected soil metric index: " + index);
        }
        return true;
    }

    private boolean isValidMetric(int index, Double value) {
        if (value == null || !Double.isFinite(value)) {
            return false;
        }
        return switch (index) {
            case 0 -> value >= 3 && value <= 10;
            case 6 -> value >= 0 && value <= 100;
            default -> value >= 0 && value < 10000;
        };
    }

    private List<NormalizedMetric> metricsFor(SoilChemistryResult result, String regionCode, boolean fallback) {
        List<NormalizedMetric> metrics = new ArrayList<>();
        List<Double> values = Arrays.asList(result.ph, result.organicMatter, result.availablePhosphate, result.potassium,
                result.calcium, result.magnesium, result.ec);
        List<String> flags = new ArrayList<>();
        if (result.partial) {
            flags.add("PARTIAL_COVERAGE");
        }
        if (result.outliersExcluded > 0) {
            flags.add("OUTLIERS_EXCLUDED");
        }
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) != null) {
                metrics.add(ExternalAdapterSupport.metric(METRICS.get(index), values.get(index), null, UNITS.get(index),
                        PROVIDER, SERVICE, result.spatialLevel, regionCode, null, fallback, replay,
                        flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
            }
        }
        return metrics;
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isEmpty() || "-".equals(normalized) || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
