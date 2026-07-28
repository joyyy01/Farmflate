package com.farmflate.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** KMA ASOS 30-day aggregation with typed provider outcomes. */
@Slf4j
@Component
public class AsosAdapter {

    private static final String BASE_URL = "http://apis.data.go.kr/1360000/AsosHourlyInfoService/getWthrDataList";
    private static final String PROVIDER = "KMA";
    private static final String SERVICE = "AsosHourlyInfoService";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final int cacheHours;
    private final int retryCount;
    private final ExternalApiCacheService dbCache;
    private final Map<String, CachedAsos> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ExternalResult<Asos30DaySummary>>> inFlight = new ConcurrentHashMap<>();
    private static final TypeReference<Asos30DaySummary> ASOS_TYPE = new TypeReference<>() {};

    public AsosAdapter(
            @Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.data-go-kr.service-key}") String serviceKey,
            @Value("${app.cache.asos-hours:24}") int cacheHours,
            @Value("${app.external-api.retry-count:1}") int retryCount,
            ExternalApiCacheService dbCache) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.cacheHours = cacheHours;
        this.retryCount = retryCount;
        this.dbCache = dbCache;
    }

    public static class Asos30DaySummary {
        public Double meanTemperature30d;
        public Double meanDailyMaxTemperature30d;
        public Double meanDailyMinTemperature30d;
        public Double totalPrecipitation30d;
        public Double meanHumidity30d;
        public Double totalSunshineHours30d;
        public int dataPointCount;
        public int outliersExcluded;
        public boolean partial;
    }

    private record CachedAsos(ExternalResult<Asos30DaySummary> result, Instant cachedAt) {
    }

    private record AsosPage(int totalCount, List<Map<String, Object>> items) {
    }

    public ExternalResult<Asos30DaySummary> get30DaySummary(String stationId) {
        String cacheKey = stationId + "_30d";
        return ExternalAdapterSupport.executeOnce(inFlight, cacheKey, () -> loadSummary(stationId, cacheKey));
    }

    /** Testable raw-payload boundary for fixture-backed contract tests. */
    public ExternalResult<Asos30DaySummary> parse(String body, String contentType) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseJsonObject(body, contentType);
        if (parsed.isFailure()) {
            return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        }
        ExternalResult<AsosPage> page = extractPage(parsed.value());
        if (page.isFailure()) {
            return ExternalResult.failure(page.errorCode(), page.metrics());
        }
        if (page.isEmpty()) {
            return ExternalResult.empty();
        }
        Asos30DaySummary summary = aggregate(page.value().items());
        return ExternalResult.success(summary, metricsFor(summary, "fixture", null));
    }

    private ExternalResult<Asos30DaySummary> loadSummary(String stationId, String cacheKey) {
        CachedAsos cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).toHours() < cacheHours) {
            log.debug("ASOS cache hit: {}", cacheKey);
            return cached.result().asCached();
        }

        String dbKey = "KMA_ASOS_30D:" + stationId;
        Optional<Asos30DaySummary> dbHit = dbCache.tryReadCache(dbKey, ASOS_TYPE);
        if (dbHit.isPresent()) {
            log.debug("ASOS DB cache hit: {}", dbKey);
            ExternalResult<Asos30DaySummary> result = ExternalResult.success(
                    dbHit.get(), metricsFor(dbHit.get(), stationId, null));
            cache.put(cacheKey, new CachedAsos(result, Instant.now()));
            return result.asCached();
        }

        LocalDate endDate = LocalDate.now(KST).minusDays(1);
        LocalDate startDate = endDate.minusDays(29);
        List<Map<String, Object>> allItems = new ArrayList<>();
        int totalCount = Integer.MAX_VALUE;
        boolean partial = false;

        for (int pageNumber = 1; allItems.size() < totalCount && pageNumber <= 10; pageNumber++) {
            ExternalResult<AsosPage> page = fetchPage(stationId, startDate, endDate, pageNumber);
            if (page.isFailure()) {
                if (allItems.isEmpty()) {
                    Optional<Asos30DaySummary> stale = dbCache.tryReadStale(dbKey, ASOS_TYPE);
                    if (stale.isPresent()) {
                        log.info("ASOS using stale DB cache: {}", dbKey);
                        return ExternalResult.success(stale.get(), metricsFor(stale.get(), stationId, null)).asCached();
                    }
                    return ExternalResult.failure(page.errorCode(), page.metrics());
                }
                partial = true;
                break;
            }
            if (page.isEmpty()) {
                break;
            }
            totalCount = page.value().totalCount();
            allItems.addAll(page.value().items());
            if (page.value().items().isEmpty()) {
                break;
            }
        }
        if (allItems.isEmpty()) {
            return ExternalResult.empty();
        }

        Asos30DaySummary summary = aggregate(allItems);
        summary.partial = summary.partial || partial;
        ExternalResult<Asos30DaySummary> result = ExternalResult.success(
                summary, metricsFor(summary, stationId, endDate.toString()));
        cache.put(cacheKey, new CachedAsos(result, Instant.now()));
        dbCache.writeCache(dbKey, PROVIDER, SERVICE, stationId, summary, Duration.ofHours(cacheHours));
        return result;
    }

    @SuppressWarnings("unchecked")
    private ExternalResult<AsosPage> fetchPage(String stationId, LocalDate startDate, LocalDate endDate, int pageNo) {
        int retries = Math.max(0, retryCount);
        for (int attempt = 0; ; attempt++) {
            URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .queryParam("ServiceKey", serviceKey)
                    .queryParam("pageNo", pageNo)
                    .queryParam("numOfRows", 999)
                    .queryParam("dataType", "JSON")
                    .queryParam("dataCd", "ASOS")
                    .queryParam("dateCd", "HR")
                    .queryParam("startDt", startDate.format(DATE_FMT))
                    .queryParam("startHh", "00")
                    .queryParam("endDt", endDate.format(DATE_FMT))
                    .queryParam("endHh", "23")
                    .queryParam("stnIds", stationId)
                    .build()
                    .encode()
                    .toUri();
            ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.executeRequest(
                    retryCount, "ASOS_REQUEST_FAILED", () -> restTemplate.getForObject(uri, Map.class));
            if (response.isFailure()) {
                log.warn("ASOS API call failed for station {}: {}", stationId, response.errorCode());
                return ExternalResult.failure(response.errorCode(), response.metrics());
            }
            // The provider answers a momentary timeout or quota hit with an
            // ordinary HTTP 200 body (resultCode 05/22), not an HTTP failure,
            // so executeRequest's transport retry never sees it. Retry once
            // more here before giving up, instead of surfacing a transient
            // blip as a permanently missing weather metric.
            String providerCode = ExternalAdapterSupport.providerResultCode(response.value());
            if (attempt < retries && ExternalAdapterSupport.isProviderTransientFailureCode("ASOS_PROVIDER_" + providerCode)) {
                log.info("ASOS provider transient result {} for station {}; retrying ({}/{})",
                        providerCode, stationId, attempt + 1, retries);
                ExternalAdapterSupport.backoffSleep(attempt);
                continue;
            }
            return extractPage(response.value());
        }
    }

    private ExternalResult<AsosPage> extractPage(Map<String, Object> response) {
        if (response == null) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        Map<String, Object> envelope = ExternalAdapterSupport.map(response.get("response"));
        if (envelope == null) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        Map<String, Object> header = ExternalAdapterSupport.map(envelope.get("header"));
        if (header == null) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        String resultCode = String.valueOf(header.get("resultCode"));
        if (!"00".equals(resultCode)) {
            return ExternalResult.failure("ASOS_PROVIDER_" + resultCode);
        }
        Map<String, Object> body = ExternalAdapterSupport.map(envelope.get("body"));
        if (body == null) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        if (!body.containsKey("totalCount")) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        int totalCount = ExternalAdapterSupport.intValue(body.get("totalCount"), -1);
        if (totalCount < 0) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        Map<String, Object> items = ExternalAdapterSupport.map(body.get("items"));
        if (totalCount == 0) {
            return ExternalResult.empty();
        }
        if (items == null) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        List<Map<String, Object>> itemList = ExternalAdapterSupport.mapList(items.get("item"));
        if (itemList.isEmpty()) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        return ExternalResult.success(new AsosPage(totalCount, itemList));
    }

    private Asos30DaySummary aggregate(List<Map<String, Object>> items) {
        Asos30DaySummary summary = new Asos30DaySummary();
        List<Double> temperatures = new ArrayList<>();
        List<Double> humidities = new ArrayList<>();
        Map<String, List<Double>> dailyTemperatures = new LinkedHashMap<>();
        double precipitation = 0;
        double sunshine = 0;

        for (Map<String, Object> item : items) {
            Double temperature = parseDouble(item.get("ta"));
            Double rain = parseDouble(item.get("rn"));
            Double humidity = parseDouble(item.get("hm"));
            Double sun = parseDouble(item.get("ss"));
            if (temperature != null && (temperature < -50 || temperature > 60)) {
                temperature = null;
                summary.outliersExcluded++;
            }
            if (humidity != null && (humidity < 0 || humidity > 100)) {
                humidity = null;
                summary.outliersExcluded++;
            }
            if (rain != null && rain < 0) {
                rain = null;
                summary.outliersExcluded++;
            }
            if (temperature != null) {
                temperatures.add(temperature);
                String timestamp = String.valueOf(item.get("tm"));
                String date = timestamp.length() >= 10 ? timestamp.substring(0, 10) : "unknown";
                dailyTemperatures.computeIfAbsent(date, ignored -> new ArrayList<>()).add(temperature);
            }
            if (humidity != null) {
                humidities.add(humidity);
            }
            if (rain != null) {
                precipitation += rain;
            }
            if (sun != null && sun >= 0) {
                sunshine += sun;
            }
        }
        summary.dataPointCount = items.size();
        summary.partial = items.size() < 500;
        summary.totalPrecipitation30d = precipitation;
        summary.totalSunshineHours30d = sunshine;
        if (!temperatures.isEmpty()) {
            summary.meanTemperature30d = temperatures.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        }
        if (!humidities.isEmpty()) {
            summary.meanHumidity30d = humidities.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        }
        if (!dailyTemperatures.isEmpty()) {
            summary.meanDailyMaxTemperature30d = dailyTemperatures.values().stream()
                    .mapToDouble(day -> day.stream().mapToDouble(Double::doubleValue).max().orElseThrow())
                    .average().orElseThrow();
            summary.meanDailyMinTemperature30d = dailyTemperatures.values().stream()
                    .mapToDouble(day -> day.stream().mapToDouble(Double::doubleValue).min().orElseThrow())
                    .average().orElseThrow();
        }
        return summary;
    }

    private List<NormalizedMetric> metricsFor(Asos30DaySummary summary, String stationId, String dataDate) {
        List<String> flags = new ArrayList<>();
        if (summary.partial) {
            flags.add("PARTIAL_COVERAGE");
        }
        if (summary.outliersExcluded > 0) {
            flags.add("OUTLIERS_EXCLUDED");
        }
        List<NormalizedMetric> metrics = new ArrayList<>();
        addMetric(metrics, "asos.mean_temperature_30d", summary.meanTemperature30d, "C", stationId, dataDate, flags);
        addMetric(metrics, "asos.mean_daily_max_temperature_30d", summary.meanDailyMaxTemperature30d, "C", stationId, dataDate, flags);
        addMetric(metrics, "asos.mean_daily_min_temperature_30d", summary.meanDailyMinTemperature30d, "C", stationId, dataDate, flags);
        addMetric(metrics, "asos.total_precipitation_30d", summary.totalPrecipitation30d, "mm", stationId, dataDate, flags);
        addMetric(metrics, "asos.mean_humidity_30d", summary.meanHumidity30d, "%", stationId, dataDate, flags);
        addMetric(metrics, "asos.total_sunshine_30d", summary.totalSunshineHours30d, "h", stationId, dataDate, flags);
        return metrics;
    }

    private void addMetric(List<NormalizedMetric> target, String name, Double value, String unit,
                           String stationId, String dataDate, List<String> flags) {
        if (value != null) {
            target.add(ExternalAdapterSupport.metric(name, value, null, unit, PROVIDER, SERVICE, "STATION",
                    stationId, dataDate, false, false, flags.isEmpty() ? "GOOD" : "PARTIAL", flags));
        }
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
