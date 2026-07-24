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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** RDA crop-by-region soil suitability normalizer. */
@Slf4j
@Component
public class SoilSuitabilityAdapter {

    private static final String BASE_URL = "http://apis.data.go.kr/1390802/SoilEnviron/SoilFitStat/V2/getSoilCropFitInfo";
    private static final String PROVIDER = "RDA";
    private static final String SERVICE = "SoilFitStat/V2";
    private static final Map<String, Integer> GRADE_SCORE = gradeScores();

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final LegalDistrictAdapter legalDistrictAdapter;
    private final CropCodeAdapter cropCodeAdapter;
    private final int cacheDays;
    private final int retryCount;
    private final boolean replay;
    private final Map<String, CachedSuitability> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ExternalResult<Map<String, SoilSuitabilityResult>>>> inFlight = new ConcurrentHashMap<>();

    public SoilSuitabilityAdapter(
            @Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.data-go-kr.service-key}") String serviceKey,
            LegalDistrictAdapter legalDistrictAdapter,
            CropCodeAdapter cropCodeAdapter,
            @Value("${app.cache.soil-suitability-days:90}") int cacheDays,
            @Value("${app.external-api.retry-count:1}") int retryCount,
            @Value("${app.data-mode:LIVE}") String dataMode) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.legalDistrictAdapter = legalDistrictAdapter;
        this.cropCodeAdapter = cropCodeAdapter;
        this.cacheDays = cacheDays;
        this.retryCount = retryCount;
        this.replay = "REPLAY".equalsIgnoreCase(dataMode);
    }

    public static class SoilSuitabilityResult {
        public String cropCode;
        public double score;
        public boolean hasData;
        public String spatialLevel = "SIGUNGU";
        public boolean partial;
        public int unknownGradesExcluded;
        public Map<String, Double> gradeAreas = new LinkedHashMap<>();
    }

    private record CachedSuitability(ExternalResult<Map<String, SoilSuitabilityResult>> result, Instant cachedAt) {
    }

    public ExternalResult<Map<String, SoilSuitabilityResult>> getSoilSuitability(
            String sigunguCode, String sidoName, String sigunguName) {
        String cacheKey = "suitability_" + sigunguCode;
        return ExternalAdapterSupport.executeOnce(inFlight, cacheKey,
                () -> loadSuitability(sigunguCode, sidoName, sigunguName, cacheKey));
    }

    /** Testable raw-payload boundary for fixture-backed contract tests. */
    public ExternalResult<Map<String, Double>> parse(String body, String contentType) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseProviderObject(body, contentType);
        if (parsed.isFailure()) {
            return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        }
        return extractGradeAreas(parsed.value());
    }

    private ExternalResult<Map<String, SoilSuitabilityResult>> loadSuitability(
            String sigunguCode, String sidoName, String sigunguName, String cacheKey) {
        CachedSuitability cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).toDays() < cacheDays) {
            return cached.result().asCached();
        }
        ExternalResult<Map<String, CropCodeAdapter.CropCodeMapping>> cropCodes = cropCodeAdapter.getCropCodeMappings();
        if (cropCodes.isFailure()) {
            return ExternalResult.failure(cropCodes.errorCode(), cropCodes.metrics());
        }
        if (cropCodes.isEmpty()) {
            return ExternalResult.empty(cropCodes.metrics());
        }

        Map<String, SoilSuitabilityResult> results = new LinkedHashMap<>();
        int withData = 0;
        int failures = 0;
        for (Map.Entry<String, CropCodeAdapter.CropCodeMapping> entry : cropCodes.value().entrySet()) {
            SoilSuitabilityResult result = new SoilSuitabilityResult();
            result.cropCode = entry.getKey();
            CropCodeAdapter.CropCodeMapping mapping = entry.getValue();
            if (!mapping.resolved || mapping.apiCropCode == null) {
                result.partial = true;
                results.put(result.cropCode, result);
                continue;
            }

            boolean fallback = !isLegalDongCode(sigunguCode);
            ExternalResult<Map<String, Double>> gradeAreas = fallback
                    ? fetchLegalDongAggregate(sidoName, sigunguName, mapping.apiCropCode)
                    : fetchGradeAreas(sigunguCode, mapping.apiCropCode);
            if (!fallback && gradeAreas.isEmpty()) {
                gradeAreas = fetchLegalDongAggregate(sidoName, sigunguName, mapping.apiCropCode);
                fallback = true;
            }
            if (gradeAreas.isFailure()) {
                failures++;
                result.partial = true;
                if (gradeAreas.value() != null && !gradeAreas.value().isEmpty()) {
                    result.gradeAreas = gradeAreas.value();
                    result.spatialLevel = fallback ? "LEGAL_DONG_AGGREGATE" : "SIGUNGU";
                    result.score = calculateWeightedScore(result);
                    result.hasData = !result.gradeAreas.isEmpty();
                    if (result.hasData) {
                        withData++;
                    }
                }
                results.put(result.cropCode, result);
                continue;
            }
            if (gradeAreas.isSuccess()) {
                result.gradeAreas = gradeAreas.value();
                result.spatialLevel = fallback ? "LEGAL_DONG_AGGREGATE" : "SIGUNGU";
                result.score = calculateWeightedScore(result);
                result.hasData = !result.gradeAreas.isEmpty();
                if (result.hasData) {
                    withData++;
                }
            }
            results.put(result.cropCode, result);
        }

        List<NormalizedMetric> metrics = metricsFor(results, sigunguCode);
        if (failures > 0) {
            return ExternalResult.failure("SOIL_SUITABILITY_PROVIDER_FAILURE", results, metrics);
        }
        if (withData > 0) {
            ExternalResult<Map<String, SoilSuitabilityResult>> result = ExternalResult.success(results, metrics);
            cache.put(cacheKey, new CachedSuitability(result, Instant.now()));
            return result;
        }
        return ExternalResult.empty(metrics);
    }

    private ExternalResult<Map<String, Double>> fetchGradeAreas(String regionCode, String apiCropCode) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("serviceKey", serviceKey)
                .queryParam("STDG_CD", regionCode)
                .queryParam("soil_Crop_CD", apiCropCode)
                .build(false)
                .toUriString();
        ExternalResult<String> payload = ExternalAdapterSupport.executeRequest(
                retryCount, "SOIL_SUITABILITY_REQUEST_FAILED", () -> restTemplate.getForObject(url, String.class));
        if (payload.isFailure()) {
            log.debug("Soil suitability fetch failed for stdg={}, crop={}: {}", regionCode, apiCropCode,
                    payload.errorCode());
            return ExternalResult.failure(payload.errorCode(), payload.metrics());
        }
        ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.parseProviderObject(payload.value(), null);
        if (response.isFailure()) {
            return ExternalResult.failure(response.errorCode(), response.metrics());
        }
        return extractGradeAreas(response.value());
    }

    private ExternalResult<Map<String, Double>> fetchLegalDongAggregate(
            String sidoName, String sigunguName, String apiCropCode) {
        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> legal = legalDistrictAdapter.getDistrictCodes(sidoName, sigunguName);
        if (legal.isFailure()) {
            return ExternalResult.failure("SOIL_SUITABILITY_LOCATION_LOOKUP_FAILED", legal.metrics());
        }
        if (legal.isEmpty()) {
            return ExternalResult.failure("SOIL_SUITABILITY_LOCATION_NOT_RESOLVED", legal.metrics());
        }
        Map<String, Double> aggregate = new LinkedHashMap<>();
        int failures = 0;
        for (LegalDistrictAdapter.LegalDistrict district : legal.value()) {
            ExternalResult<Map<String, Double>> areas = fetchGradeAreas(district.regionCd, apiCropCode);
            if (areas.isFailure()) {
                failures++;
                continue;
            }
            if (areas.isSuccess()) {
                areas.value().forEach((grade, area) -> aggregate.merge(grade, area, Double::sum));
            }
        }
        if (failures > 0) {
            return ExternalResult.failure("SOIL_SUITABILITY_PROVIDER_FAILURE", aggregate, List.of());
        }
        return aggregate.isEmpty() ? ExternalResult.empty() : ExternalResult.success(aggregate);
    }

    private ExternalResult<Map<String, Double>> extractGradeAreas(Map<String, Object> response) {
        if (response == null) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        String providerCode = ExternalAdapterSupport.providerResultCode(response);
        if (providerCode != null && !ExternalAdapterSupport.isProviderSuccessCode(providerCode)) {
            if (ExternalAdapterSupport.isProviderNoDataCode(providerCode)) {
                return ExternalResult.empty();
            }
            return ExternalResult.failure("SOIL_SUITABILITY_PROVIDER_" + providerCode);
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
        Map<String, Double> areas = parseGradeAreas(rows);
        if (areas.isEmpty()) {
            return ExternalResult.failure("SOIL_SUITABILITY_UNUSABLE_RECORDS");
        }
        boolean knownGrade = areas.keySet().stream().anyMatch(grade -> scoreForGrade(grade) != null);
        return knownGrade
                ? ExternalResult.success(areas)
                : ExternalResult.failure("SOIL_SUITABILITY_UNUSABLE_RECORDS", areas, List.of());
    }

    private Map<String, Object> findBody(Map<String, Object> response) {
        Map<String, Object> envelope = ExternalAdapterSupport.map(response.get("response"));
        if (envelope != null) {
            return ExternalAdapterSupport.map(envelope.get("body"));
        }
        return ExternalAdapterSupport.map(response.get("body"));
    }

    private Map<String, Double> parseGradeAreas(List<Map<String, Object>> rows) {
        Map<String, Double> areas = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String grade = string(row, "soil_Grd_Nm", "soilGrdNm", "SOIL_GRD_NM", "grdNm");
            Double area = number(row, "soil_Grd_Area", "soilGrdArea", "SOIL_GRD_AREA", "area", "grdArea");
            if (area == null) {
                area = number(row, "soil_Grd_Ratio", "soilGrdRatio", "ratio");
            }
            if (grade != null && area != null && Double.isFinite(area) && area > 0 && area < 1_000_000_000d) {
                areas.merge(grade, area, Double::sum);
            }
        }
        return areas;
    }

    /** Public deterministic mapping for provider grade labels. */
    public Integer scoreForGrade(String grade) {
        if (grade == null) {
            return null;
        }
        for (Map.Entry<String, Integer> entry : GRADE_SCORE.entrySet()) {
            if (grade.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private double calculateWeightedScore(SoilSuitabilityResult result) {
        double weightedTotal = 0;
        double totalArea = 0;
        for (Map.Entry<String, Double> entry : result.gradeAreas.entrySet()) {
            Integer score = scoreForGrade(entry.getKey());
            if (score == null) {
                result.unknownGradesExcluded++;
                continue;
            }
            weightedTotal += entry.getValue() * score;
            totalArea += entry.getValue();
        }
        return totalArea == 0 ? 0 : weightedTotal / totalArea;
    }

    private List<NormalizedMetric> metricsFor(Map<String, SoilSuitabilityResult> results, String regionCode) {
        List<NormalizedMetric> metrics = new ArrayList<>();
        for (SoilSuitabilityResult result : results.values()) {
            List<String> flags = new ArrayList<>();
            if (!result.hasData) {
                flags.add("NO_DATA");
            }
            if (result.partial) {
                flags.add("PARTIAL_COVERAGE");
            }
            if (result.unknownGradesExcluded > 0) {
                flags.add("UNKNOWN_GRADE_EXCLUDED");
            }
            metrics.add(ExternalAdapterSupport.metric("soil.suitability.score", result.hasData ? result.score : null,
                    result.cropCode, "score", PROVIDER, SERVICE, result.spatialLevel, regionCode, null,
                    "LEGAL_DONG_AGGREGATE".equals(result.spatialLevel), replay,
                    flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
        }
        return metrics;
    }

    private String string(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private Double number(Map<String, Object> row, String... keys) {
        String value = string(row, keys);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isLegalDongCode(String regionCode) {
        return regionCode != null && regionCode.matches("\\d{10}");
    }

    private static Map<String, Integer> gradeScores() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("매우적합", 100);
        scores.put("최적지", 100);
        scores.put("적합", 85);
        scores.put("적지", 85);
        scores.put("보통", 65);
        scores.put("가능지", 65);
        scores.put("주의", 40);
        scores.put("저위생산지", 40);
        scores.put("부적합", 10);
        scores.put("부적지", 10);
        return Collections.unmodifiableMap(scores);
    }
}
