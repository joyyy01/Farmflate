package com.example.aiworkspace.service.external;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RDA SoilExam V2 chemistry normalizer.
 *
 * <p>SoilExamStat V2 exposes area distributions, not observed chemistry. This
 * adapter instead aggregates the detailed SoilExam V2 records from a bounded,
 * deterministic legal-dong sample. The result reports eligible, sampled, and
 * data-backed dong counts so it never presents the sample as a county census.</p>
 */
@Component
public class SoilChemistryAdapter {

    private static final String BASE_URL = "http://apis.data.go.kr/1390802/SoilEnviron/SoilExam/V2/getSoilExamList";
    private static final String PROVIDER = "RDA";
    private static final String SERVICE = "SoilExam/V2";
    private static final String RDA_SOIL_RATE_SCOPE = "rda-soil";
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

    @Value("${app.external-api.rda-min-interval-ms:500}")
    private int rdaMinIntervalMs;

    @Value("${app.external-api.soil-chemistry-sample-dongs:6}")
    private int legalDongSampleSize;

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
        public String spatialLevel = "LEGAL_DONG_REPRESENTATIVE_SAMPLE";
        /** Number of active legal dongs returned by the authoritative boundary API. */
        public int totalDongs;
        /** Number of deterministic legal-dong requests made for this report. */
        public int sampledDongs;
        /** Number of sampled legal dongs with an authoritative detailed soil record. */
        public int coveredDongs;
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

    /** Testable detailed SoilExam V2 payload boundary; returns the observed pH field. */
    public ExternalResult<Double> parse(String body, String contentType) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseProviderObject(body, contentType);
        if (parsed.isFailure()) {
            return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        }
        ExternalResult<SoilChemistryResult> record = extractSoilExamRecord(parsed.value());
        if (record.isFailure()) {
            return ExternalResult.failure(record.errorCode(), record.metrics());
        }
        if (record.isEmpty()) {
            return ExternalResult.empty(record.metrics());
        }
        return record.value().ph == null
                ? ExternalResult.failure("SOIL_CHEMISTRY_UNUSABLE_RECORDS", record.metrics())
                : ExternalResult.success(record.value().ph, record.metrics());
    }

    private ExternalResult<SoilChemistryResult> loadSoilChemistry(
            String sigunguCode, String sidoName, String sigunguName, String cacheKey) {
        CachedSoil cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).toDays() < cacheDays) {
            return cached.result().asCached();
        }

        ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> legal = resolveLegalDongs(
                sigunguCode, sidoName, sigunguName);
        if (legal.isFailure() || legal.isEmpty()) {
            return legal.isEmpty()
                    ? ExternalResult.failure("SOIL_CHEMISTRY_LOCATION_NOT_RESOLVED", legal.metrics())
                    : ExternalResult.failure("SOIL_CHEMISTRY_LOCATION_LOOKUP_FAILED_" + legal.errorCode(), legal.metrics());
        }

        List<LegalDistrictAdapter.LegalDistrict> sample = representativeSample(legal.value(), sampleLimit());
        ExternalResult<SoilChemistryResult> result = aggregateSample(sample, legal.value().size(), sigunguCode);
        if (result.isSuccess()) {
            cache.put(cacheKey, new CachedSoil(result, Instant.now()));
        }
        return result;
    }

    private ExternalResult<List<LegalDistrictAdapter.LegalDistrict>> resolveLegalDongs(
            String sigunguCode, String sidoName, String sigunguName) {
        if (!isLegalDongCode(sigunguCode)) {
            return legalDistrictAdapter.getDistrictCodes(sidoName, sigunguName);
        }
        LegalDistrictAdapter.LegalDistrict district = new LegalDistrictAdapter.LegalDistrict();
        district.regionCd = sigunguCode;
        return ExternalResult.success(List.of(district));
    }

    private ExternalResult<SoilChemistryResult> aggregateSample(
            List<LegalDistrictAdapter.LegalDistrict> sample, int totalDongs, String sigunguCode) {
        SoilChemistryResult aggregate = new SoilChemistryResult();
        aggregate.totalDongs = totalDongs;
        aggregate.sampledDongs = sample.size();
        List<List<Double>> valuesByMetric = new ArrayList<>();
        for (int index = 0; index < METRICS.size(); index++) {
            valuesByMetric.add(new ArrayList<>());
        }

        int providerFailures = 0;
        for (LegalDistrictAdapter.LegalDistrict district : sample) {
            ExternalResult<SoilChemistryResult> record = fetchSoilExam(district.regionCd);
            if (record.isFailure()) {
                providerFailures++;
                continue;
            }
            if (record.isEmpty()) {
                continue;
            }
            SoilChemistryResult value = record.value();
            if (value.ph == null) {
                aggregate.outliersExcluded += value.outliersExcluded;
                continue;
            }
            aggregate.coveredDongs++;
            aggregate.outliersExcluded += value.outliersExcluded;
            addValues(valuesByMetric, value);
        }

        if (aggregate.coveredDongs > 0) {
            applyAverages(aggregate, valuesByMetric);
            aggregate.partial = aggregate.coveredDongs < aggregate.sampledDongs || providerFailures > 0;
            List<NormalizedMetric> metrics = metricsFor(aggregate, sigunguCode);
            if (providerFailures > 0) {
                return ExternalResult.failure("SOIL_CHEMISTRY_PROVIDER_FAILURE", aggregate, metrics);
            }
            return ExternalResult.success(aggregate, metrics);
        }

        List<NormalizedMetric> metrics = metricsFor(aggregate, sigunguCode);
        return providerFailures > 0
                ? ExternalResult.failure("SOIL_CHEMISTRY_PROVIDER_FAILURE", aggregate, metrics)
                : ExternalResult.empty(metrics);
    }

    private ExternalResult<SoilChemistryResult> fetchSoilExam(String legalDongCode) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("serviceKey", serviceKey)
                .queryParam("Page_Size", 1)
                .queryParam("Page_No", 1)
                .queryParam("STDG_CD", legalDongCode)
                .build()
                .encode()
                .toUri();
        ExternalResult<String> payload = ExternalAdapterSupport.executePacedRequest(
                RDA_SOIL_RATE_SCOPE, Math.max(0, rdaMinIntervalMs), retryCount,
                "SOIL_CHEMISTRY_REQUEST_FAILED", () -> restTemplate.getForObject(uri, String.class));
        if (payload.isFailure()) {
            return ExternalResult.failure(payload.errorCode(), payload.metrics());
        }
        ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.parseProviderObject(payload.value(), null);
        if (response.isFailure()) {
            return ExternalResult.failure(response.errorCode(), response.metrics());
        }
        return extractSoilExamRecord(response.value());
    }

    private ExternalResult<SoilChemistryResult> extractSoilExamRecord(Map<String, Object> response) {
        if (response == null) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        String providerCode = ExternalAdapterSupport.providerResultCode(response);
        if (providerCode != null && !ExternalAdapterSupport.isProviderSuccessCode(providerCode)) {
            return ExternalAdapterSupport.isProviderNoDataCode(providerCode)
                    ? ExternalResult.empty()
                    : ExternalResult.failure("SOIL_CHEMISTRY_PROVIDER_" + providerCode);
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
            SoilChemistryResult result = new SoilChemistryResult();
            result.spatialLevel = "LEGAL_DONG";
            setMetricValue(result, 0, value(row, "ACID", "acid"));
            setMetricValue(result, 1, value(row, "OM", "om"));
            setMetricValue(result, 2, value(row, "VLDPHA", "vldpha"));
            setMetricValue(result, 3, value(row, "POSIFERT_K", "posifert_k"));
            setMetricValue(result, 4, value(row, "POSIFERT_CA", "posifert_ca"));
            setMetricValue(result, 5, value(row, "POSIFERT_MG", "posifert_mg"));
            setMetricValue(result, 6, value(row, "EC", "ec"));
            if (result.ph != null) {
                return ExternalResult.success(result);
            }
        }
        return ExternalResult.failure("SOIL_CHEMISTRY_UNUSABLE_RECORDS");
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

    private void addValues(List<List<Double>> valuesByMetric, SoilChemistryResult value) {
        List<Double> values = Arrays.asList(value.ph, value.organicMatter, value.availablePhosphate, value.potassium,
                value.calcium, value.magnesium, value.ec);
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) != null) {
                valuesByMetric.get(index).add(values.get(index));
            }
        }
    }

    private void applyAverages(SoilChemistryResult result, List<List<Double>> valuesByMetric) {
        for (int index = 0; index < valuesByMetric.size(); index++) {
            List<Double> values = valuesByMetric.get(index);
            if (!values.isEmpty()) {
                setMetricValue(result, index, values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            }
        }
    }

    private Map<String, Object> findBody(Map<String, Object> response) {
        Map<String, Object> envelope = ExternalAdapterSupport.map(response.get("response"));
        if (envelope != null) {
            return ExternalAdapterSupport.map(envelope.get("body"));
        }
        return ExternalAdapterSupport.map(response.get("body"));
    }

    private Double value(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Double parsed = parseDouble(row.get(key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private boolean isLegalDongCode(String regionCode) {
        return regionCode != null && regionCode.matches("\\d{10}");
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

    private List<NormalizedMetric> metricsFor(SoilChemistryResult result, String regionCode) {
        List<NormalizedMetric> metrics = new ArrayList<>();
        List<Double> values = Arrays.asList(result.ph, result.organicMatter, result.availablePhosphate, result.potassium,
                result.calcium, result.magnesium, result.ec);
        List<String> flags = new ArrayList<>();
        if (result.sampledDongs < result.totalDongs) {
            flags.add("REPRESENTATIVE_LEGAL_DONG_SAMPLE");
        }
        if (result.coveredDongs < result.sampledDongs) {
            flags.add("OFFICIAL_NO_RECORDS_WITHIN_SAMPLE");
        }
        if (result.partial) {
            flags.add("PARTIAL_SAMPLE_COVERAGE");
        }
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index) != null) {
                metrics.add(ExternalAdapterSupport.metric(METRICS.get(index), values.get(index), null, UNITS.get(index),
                        PROVIDER, SERVICE, result.spatialLevel, regionCode, null, true, replay,
                        flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
            }
        }
        metrics.add(ExternalAdapterSupport.metric("soil.eligible_legal_dongs", (double) result.totalDongs, null, "count",
                PROVIDER, SERVICE, result.spatialLevel, regionCode, null, true, replay, "GOOD", flags));
        metrics.add(ExternalAdapterSupport.metric("soil.sampled_legal_dongs", (double) result.sampledDongs, null, "count",
                PROVIDER, SERVICE, result.spatialLevel, regionCode, null, true, replay, "GOOD", flags));
        metrics.add(ExternalAdapterSupport.metric("soil.data_backed_legal_dongs", (double) result.coveredDongs, null, "count",
                PROVIDER, SERVICE, result.spatialLevel, regionCode, null, true, replay, "GOOD", flags));
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
