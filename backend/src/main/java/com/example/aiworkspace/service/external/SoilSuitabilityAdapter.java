package com.example.aiworkspace.service.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
    private static final String RDA_SOIL_RATE_SCOPE = "rda-soil";
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

    @Value("${app.external-api.rda-min-interval-ms:500}")
    private int rdaMinIntervalMs;

    @Value("${app.external-api.soil-suitability-sample-dongs:6}")
    private int legalDongSampleSize;

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
        public String unavailableReason;
        public int unknownGradesExcluded;
        public int totalDongs;
        public int sampledDongs;
        public int coveredDongs;
        public Map<String, Double> gradeAreas = new LinkedHashMap<>();
    }

    private record CachedSuitability(ExternalResult<Map<String, SoilSuitabilityResult>> result, Instant cachedAt) {
    }

    private record GradeAreasAggregate(Map<String, Double> gradeAreas, int totalDongs, int sampledDongs,
                                       int coveredDongs) {
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
        return extractGradeAreas(parsed.value(), null);
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
        String cropNameValidationError = null;
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
            ExternalResult<GradeAreasAggregate> gradeAreas = fallback
                    ? fetchLegalDongAggregate(sidoName, sigunguName, mapping.apiCropCode, mapping.cropName)
                    : fetchDirectGradeAreas(sigunguCode, mapping.apiCropCode, mapping.cropName);
            if (!fallback && gradeAreas.isEmpty()) {
                gradeAreas = fetchLegalDongAggregate(sidoName, sigunguName, mapping.apiCropCode, mapping.cropName);
                fallback = true;
            }
            if (gradeAreas.isFailure()) {
                result.partial = true;
                result.unavailableReason = gradeAreas.errorCode();
                if (gradeAreas.value() != null && !gradeAreas.value().gradeAreas().isEmpty()) {
                    applyGradeAreas(result, gradeAreas.value(), fallback);
                    result.score = calculateWeightedScore(result);
                    result.hasData = !result.gradeAreas.isEmpty();
                    if (result.hasData) {
                        withData++;
                    }
                }
                results.put(result.cropCode, result);
                if (isLocationResolutionError(gradeAreas.errorCode())) {
                    return ExternalResult.failure(gradeAreas.errorCode(), results, metricsFor(results, sigunguCode));
                }
                if (isCropNameValidationError(gradeAreas.errorCode()) && cropNameValidationError == null) {
                    cropNameValidationError = gradeAreas.errorCode();
                }
                failures++;
                continue;
            }
            if (gradeAreas.isSuccess()) {
                applyGradeAreas(result, gradeAreas.value(), fallback);
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
            return ExternalResult.failure(
                    withData == 0 && cropNameValidationError != null
                            ? cropNameValidationError
                            : "SOIL_SUITABILITY_PROVIDER_FAILURE",
                    results, metrics);
        }
        if (withData > 0) {
            ExternalResult<Map<String, SoilSuitabilityResult>> result = ExternalResult.success(results, metrics);
            cache.put(cacheKey, new CachedSuitability(result, Instant.now()));
            return result;
        }
        return ExternalResult.empty(metrics);
    }

    private ExternalResult<Map<String, Double>> fetchGradeAreas(
            String regionCode, String apiCropCode, String expectedCropName) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("serviceKey", serviceKey)
                .queryParam("STDG_CD", regionCode)
                .queryParam("soil_Crop_CD", apiCropCode)
                .build()
                .encode()
                .toUri();
        ExternalResult<String> payload = ExternalAdapterSupport.executePacedRequest(
                RDA_SOIL_RATE_SCOPE, Math.max(0, rdaMinIntervalMs), retryCount,
                "SOIL_SUITABILITY_REQUEST_FAILED", () -> restTemplate.getForObject(uri, String.class));
        if (payload.isFailure()) {
            log.debug("Soil suitability fetch failed for stdg={}, crop={}: {}", regionCode, apiCropCode,
                    payload.errorCode());
            return ExternalResult.failure(payload.errorCode(), payload.metrics());
        }
        ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.parseProviderObject(payload.value(), null);
        if (response.isFailure()) {
            return ExternalResult.failure(response.errorCode(), response.metrics());
        }
        return extractGradeAreas(response.value(), expectedCropName);
    }

    private ExternalResult<GradeAreasAggregate> fetchDirectGradeAreas(
            String regionCode, String apiCropCode, String expectedCropName) {
        ExternalResult<Map<String, Double>> areas = fetchGradeAreas(regionCode, apiCropCode, expectedCropName);
        if (areas.isFailure()) {
            return ExternalResult.failure(areas.errorCode(), areas.metrics());
        }
        if (areas.isEmpty()) {
            return ExternalResult.empty(areas.metrics());
        }
        return ExternalResult.success(new GradeAreasAggregate(areas.value(), 1, 1, 1), areas.metrics());
    }

    private ExternalResult<GradeAreasAggregate> fetchLegalDongAggregate(
            String sidoName, String sigunguName, String apiCropCode, String expectedCropName) {
        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> legal = legalDistrictAdapter.getDistrictCodes(sidoName, sigunguName);
        if (legal.isFailure()) {
            return ExternalResult.failure("SOIL_SUITABILITY_LOCATION_LOOKUP_FAILED_" + legal.errorCode(), legal.metrics());
        }
        if (legal.isEmpty()) {
            return ExternalResult.failure("SOIL_SUITABILITY_LOCATION_NOT_RESOLVED", legal.metrics());
        }
        Map<String, Double> aggregate = new LinkedHashMap<>();
        int failures = 0;
        int covered = 0;
        List<LegalDistrictAdapter.LegalDistrict> sample = representativeSample(legal.value(), sampleLimit());
        for (LegalDistrictAdapter.LegalDistrict district : sample) {
            ExternalResult<Map<String, Double>> areas = fetchGradeAreas(district.regionCd, apiCropCode, expectedCropName);
            if (areas.isFailure()) {
                failures++;
                continue;
            }
            if (areas.isSuccess()) {
                areas.value().forEach((grade, area) -> aggregate.merge(grade, area, Double::sum));
                covered++;
            }
        }
        GradeAreasAggregate value = new GradeAreasAggregate(aggregate, legal.value().size(), sample.size(), covered);
        if (failures > 0) {
            return ExternalResult.failure("SOIL_SUITABILITY_PROVIDER_FAILURE", value, List.of());
        }
        return aggregate.isEmpty() ? ExternalResult.empty() : ExternalResult.success(value);
    }

    private ExternalResult<Map<String, Double>> extractGradeAreas(
            Map<String, Object> response, String expectedCropName) {
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
        String cropNameValidationError = validateReturnedCropName(rows, expectedCropName);
        if (cropNameValidationError != null) {
            return ExternalResult.failure(cropNameValidationError);
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

    private String validateReturnedCropName(List<Map<String, Object>> rows, String expectedCropName) {
        if (expectedCropName == null || expectedCropName.isBlank()) {
            return null;
        }
        List<String> returnedNames = rows.stream()
                .map(row -> string(row, "soil_Crop_Nm", "soilCropNm", "SOIL_CROP_NM"))
                .filter(name -> name != null && !name.isBlank())
                .toList();
        if (returnedNames.isEmpty()) {
            return "SOIL_SUITABILITY_CROP_NAME_UNVERIFIED";
        }
        String expected = normalizeCropName(expectedCropName);
        return returnedNames.stream().allMatch(name -> normalizeCropName(name).equals(expected))
                ? null
                : "SOIL_SUITABILITY_CROP_NAME_MISMATCH";
    }

    private String normalizeCropName(String cropName) {
        return cropName == null ? "" : cropName.replaceAll("\\s+", "").trim();
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
            // SoilFit V2's current response is a single item with area bands,
            // not one row per grade. Normalize both the live schema and the
            // older grade-row schema without inventing a missing category.
            addArea(areas, "매우적합", number(row, "high_Suit_Area", "highSuitArea"));
            addArea(areas, "적합", number(row, "suit_Area", "suitArea"));
            addArea(areas, "가능", number(row, "poss_Area", "possArea"));
            addArea(areas, "낮음", number(row, "low_Suit_Area", "lowSuitArea"));
            addArea(areas, "기타", number(row, "etc_Area", "etcArea"));
            String grade = string(row, "soil_Grd_Nm", "soilGrdNm", "SOIL_GRD_NM", "grdNm");
            Double area = number(row, "soil_Grd_Area", "soilGrdArea", "SOIL_GRD_AREA", "area", "grdArea");
            if (area == null) {
                area = number(row, "soil_Grd_Ratio", "soilGrdRatio", "ratio");
            }
            if (grade != null) {
                addArea(areas, grade, area);
            }
        }
        return areas;
    }

    private void addArea(Map<String, Double> areas, String grade, Double area) {
        if (area != null && Double.isFinite(area) && area >= 0 && area < 1_000_000_000d) {
            areas.merge(grade, area, Double::sum);
        }
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

    private void applyGradeAreas(SoilSuitabilityResult result, GradeAreasAggregate aggregate, boolean fallback) {
        result.gradeAreas = new LinkedHashMap<>(aggregate.gradeAreas());
        result.totalDongs = aggregate.totalDongs();
        result.sampledDongs = aggregate.sampledDongs();
        result.coveredDongs = aggregate.coveredDongs();
        result.partial = result.coveredDongs < result.sampledDongs;
        result.spatialLevel = fallback ? "LEGAL_DONG_REPRESENTATIVE_SAMPLE" : "LEGAL_DONG";
    }

    private List<LegalDistrictAdapter.LegalDistrict> representativeSample(
            List<LegalDistrictAdapter.LegalDistrict> districts, int limit) {
        List<LegalDistrictAdapter.LegalDistrict> ordered = districts.stream()
                .filter(district -> district != null && isLegalDongCode(district.regionCd))
                .sorted(Comparator.comparing(district -> district.regionCd))
                .toList();
        if (ordered.size() <= limit) {
            return ordered;
        }
        if (limit <= 1) {
            return List.of(ordered.get(0));
        }
        LinkedHashSet<LegalDistrictAdapter.LegalDistrict> sample = new LinkedHashSet<>();
        for (int index = 0; index < limit; index++) {
            int sourceIndex = Math.round((float) index * (ordered.size() - 1) / (limit - 1));
            sample.add(ordered.get(sourceIndex));
        }
        return List.copyOf(sample);
    }

    private int sampleLimit() {
        return legalDongSampleSize > 0 ? legalDongSampleSize : 6;
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
            if (isCropNameValidationError(result.unavailableReason)) {
                flags.add(result.unavailableReason);
            }
            if (result.sampledDongs < result.totalDongs) {
                flags.add("REPRESENTATIVE_LEGAL_DONG_SAMPLE");
            }
            if (result.coveredDongs < result.sampledDongs) {
                flags.add("OFFICIAL_NO_RECORDS_WITHIN_SAMPLE");
            }
            if (result.unknownGradesExcluded > 0) {
                flags.add("UNKNOWN_GRADE_EXCLUDED");
            }
            metrics.add(ExternalAdapterSupport.metric("soil.suitability.score", result.hasData ? result.score : null,
                    result.cropCode, "score", PROVIDER, SERVICE, result.spatialLevel, regionCode, null,
                    result.spatialLevel.startsWith("LEGAL_DONG"), replay,
                    flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
            if (result.totalDongs > 0 || result.sampledDongs > 0 || result.coveredDongs > 0) {
                metrics.add(ExternalAdapterSupport.metric("soil.suitability.eligible_legal_dongs",
                        (double) result.totalDongs, result.cropCode, "count", PROVIDER, SERVICE,
                        result.spatialLevel, regionCode, null, result.spatialLevel.startsWith("LEGAL_DONG"), replay,
                        flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
                metrics.add(ExternalAdapterSupport.metric("soil.suitability.sampled_legal_dongs",
                        (double) result.sampledDongs, result.cropCode, "count", PROVIDER, SERVICE,
                        result.spatialLevel, regionCode, null, result.spatialLevel.startsWith("LEGAL_DONG"), replay,
                        flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
                metrics.add(ExternalAdapterSupport.metric("soil.suitability.data_backed_legal_dongs",
                        (double) result.coveredDongs, result.cropCode, "count", PROVIDER, SERVICE,
                        result.spatialLevel, regionCode, null, result.spatialLevel.startsWith("LEGAL_DONG"), replay,
                        flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
            }
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

    private boolean isLocationResolutionError(String errorCode) {
        return errorCode != null && errorCode.startsWith("SOIL_SUITABILITY_LOCATION_");
    }

    private boolean isCropNameValidationError(String errorCode) {
        return errorCode != null && errorCode.startsWith("SOIL_SUITABILITY_CROP_NAME_");
    }

    private static Map<String, Integer> gradeScores() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("매우적합", 100);
        scores.put("최적지", 100);
        scores.put("적합", 85);
        scores.put("적지", 85);
        scores.put("보통", 65);
        scores.put("가능지", 65);
        scores.put("가능", 65);
        scores.put("주의", 40);
        scores.put("저위생산지", 40);
        scores.put("낮음", 40);
        scores.put("부적합", 10);
        scores.put("부적지", 10);
        scores.put("기타", 0);
        return Collections.unmodifiableMap(scores);
    }
}
