package com.example.aiworkspace.service.external;

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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KMA short-forecast normalizer.  A valid provider response with no items is
 * EMPTY; a malformed response, an HTML error page, or a non-success provider
 * code is FAILURE and is never collapsed into an empty forecast.
 */
@Slf4j
@Component
public class ShortForecastAdapter {

    private static final String BASE_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String PROVIDER = "KMA";
    private static final String SERVICE = "VilageFcstInfoService_2.0";
    private static final List<String> BASE_TIMES = List.of("0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final int cacheDurationMinutes;
    private final int retryCount;
    private final ExternalApiCacheService dbCache;
    private final Map<String, CachedForecast> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ExternalResult<List<DailyForecast>>>> inFlight = new ConcurrentHashMap<>();
    private static final TypeReference<List<DailyForecast>> FORECAST_TYPE = new TypeReference<>() {};

    public ShortForecastAdapter(
            @Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.data-go-kr.service-key}") String serviceKey,
            @Value("${app.cache.short-forecast-minutes:30}") int cacheDurationMinutes,
            @Value("${app.external-api.retry-count:1}") int retryCount,
            ExternalApiCacheService dbCache) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.cacheDurationMinutes = cacheDurationMinutes;
        this.retryCount = retryCount;
        this.dbCache = dbCache;
    }

    public static class DailyForecast {
        public String date;
        public Double minTemp;
        public Double maxTemp;
        public Integer popMax;
        public Double pcpTotal;
        public Double rehAvg;
        public Double wsdMax;
        public List<Double> tmpValues = new ArrayList<>();
        public List<Integer> popValues = new ArrayList<>();
        public List<Double> pcpValues = new ArrayList<>();
        public List<Double> rehValues = new ArrayList<>();
        public List<Double> wsdValues = new ArrayList<>();

        public void finalize_() {
            if (minTemp == null && !tmpValues.isEmpty()) {
                minTemp = tmpValues.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            }
            if (maxTemp == null && !tmpValues.isEmpty()) {
                maxTemp = tmpValues.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            }
            if (!popValues.isEmpty()) {
                popMax = popValues.stream().mapToInt(Integer::intValue).max().orElseThrow();
            }
            if (!pcpValues.isEmpty()) {
                pcpTotal = pcpValues.stream().mapToDouble(Double::doubleValue).sum();
            }
            if (!rehValues.isEmpty()) {
                rehAvg = rehValues.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            }
            if (!wsdValues.isEmpty()) {
                wsdMax = wsdValues.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
            }
        }
    }

    private record CachedForecast(ExternalResult<List<DailyForecast>> result, Instant cachedAt) {
    }

    /** Returns a non-null typed result for the next three forecast days. */
    public ExternalResult<List<DailyForecast>> getForecast3Days(int nx, int ny) {
        ZonedDateTime now = ZonedDateTime.now(KST);
        String[] baseInfo = findBaseDateTime(now);
        String cacheKey = nx + "_" + ny + "_" + baseInfo[0] + "_" + baseInfo[1];
        return ExternalAdapterSupport.executeOnce(inFlight, cacheKey,
                () -> loadForecast(nx, ny, baseInfo[0], baseInfo[1], cacheKey));
    }

    /** Testable raw-payload boundary for fixture-backed contract tests. */
    public ExternalResult<List<DailyForecast>> parse(String body, String contentType) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseJsonObject(body, contentType);
        if (parsed.isFailure()) {
            return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        }
        ExternalResult<List<Map<String, Object>>> items = extractItems(parsed.value());
        if (items.isFailure()) {
            return ExternalResult.failure(items.errorCode(), items.metrics());
        }
        if (items.isEmpty()) {
            return ExternalResult.empty(items.metrics());
        }
        List<DailyForecast> forecasts = aggregateByDay(items.value());
        return forecasts.isEmpty()
                ? ExternalResult.empty()
                : ExternalResult.success(forecasts, metricsFor(forecasts, "fixture", false));
    }

    private ExternalResult<List<DailyForecast>> loadForecast(
            int nx, int ny, String baseDate, String baseTime, String cacheKey) {
        CachedForecast cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).toMinutes() < cacheDurationMinutes) {
            log.debug("Short forecast cache hit: {}", cacheKey);
            return cached.result().asCached();
        }

        String dbKey = "KMA_SHORT_FORECAST:" + nx + ":" + ny + ":" + baseDate + ":" + baseTime;
        Optional<List<DailyForecast>> dbHit = dbCache.tryReadCache(dbKey, FORECAST_TYPE);
        if (dbHit.isPresent()) {
            log.debug("Short forecast DB cache hit: {}", dbKey);
            ExternalResult<List<DailyForecast>> result = ExternalResult.success(
                    dbHit.get(), metricsFor(dbHit.get(), nx + "," + ny, false));
            cache.put(cacheKey, new CachedForecast(result, Instant.now()));
            return result.asCached();
        }

        ExternalResult<List<Map<String, Object>>> fetched = fetchAllItems(nx, ny, baseDate, baseTime);
        boolean fallback = false;
        for (int attempt = 0; attempt < 3 && fetched.isEmpty(); attempt++) {
            String[] previous = previousBaseTime(baseDate, baseTime);
            baseDate = previous[0];
            baseTime = previous[1];
            fallback = true;
            log.info("Short forecast publication fallback: {} {}", baseDate, baseTime);
            fetched = fetchAllItems(nx, ny, baseDate, baseTime);
        }
        if (fetched.isFailure()) {
            Optional<List<DailyForecast>> stale = dbCache.tryReadStale(dbKey, FORECAST_TYPE);
            if (stale.isPresent()) {
                log.info("Short forecast using stale DB cache: {}", dbKey);
                return ExternalResult.success(stale.get(), metricsFor(stale.get(), nx + "," + ny, false)).asCached();
            }
            return ExternalResult.failure(fetched.errorCode(), fetched.metrics());
        }
        if (fetched.isEmpty()) {
            return ExternalResult.empty();
        }

        List<DailyForecast> forecasts = aggregateByDay(fetched.value());
        if (forecasts.isEmpty()) {
            return ExternalResult.empty();
        }
        ExternalResult<List<DailyForecast>> result = ExternalResult.success(
                forecasts, metricsFor(forecasts, nx + "," + ny, fallback));
        cache.put(cacheKey, new CachedForecast(result, Instant.now()));
        dbCache.writeCache(dbKey, PROVIDER, SERVICE, null, forecasts, Duration.ofMinutes(cacheDurationMinutes));
        return result;
    }

    @SuppressWarnings("unchecked")
    private ExternalResult<List<Map<String, Object>>> fetchAllItems(int nx, int ny, String baseDate, String baseTime) {
        ExternalResult<Map<String, Object>> firstResponse = requestPage(nx, ny, baseDate, baseTime, 1);
        if (firstResponse.isFailure()) {
            return ExternalResult.failure(firstResponse.errorCode(), firstResponse.metrics());
        }
        Map<String, Object> firstPage = firstResponse.value();
        ExternalResult<List<Map<String, Object>>> first = extractItems(firstPage);
        if (!first.isSuccess()) {
            return first;
        }
        List<Map<String, Object>> allItems = new ArrayList<>(first.value());
        int totalCount = extractTotalCount(firstPage);
        for (int page = 2; page <= Math.min((totalCount + 999) / 1000, 5); page++) {
            ExternalResult<Map<String, Object>> pageResponse = requestPage(nx, ny, baseDate, baseTime, page);
            if (pageResponse.isFailure()) {
                return ExternalResult.failure(pageResponse.errorCode(), pageResponse.metrics());
            }
            ExternalResult<List<Map<String, Object>>> next = extractItems(pageResponse.value());
            if (next.isFailure()) {
                return ExternalResult.failure(next.errorCode());
            }
            if (next.isSuccess()) {
                allItems.addAll(next.value());
            }
        }
        return allItems.isEmpty() ? ExternalResult.empty() : ExternalResult.success(allItems);
    }

    @SuppressWarnings("unchecked")
    private ExternalResult<Map<String, Object>> requestPage(int nx, int ny, String baseDate, String baseTime, int page) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("ServiceKey", serviceKey)
                .queryParam("pageNo", page)
                .queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build()
                .encode()
                .toUri();
        return ExternalAdapterSupport.executeRequest(
                retryCount, "KMA_REQUEST_FAILED", () -> restTemplate.getForObject(uri, Map.class));
    }

    private ExternalResult<List<Map<String, Object>>> extractItems(Map<String, Object> response) {
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
            return ExternalResult.failure("KMA_PROVIDER_" + resultCode);
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
        return ExternalResult.success(itemList);
    }

    private int extractTotalCount(Map<String, Object> response) {
        Map<String, Object> envelope = response == null ? null : ExternalAdapterSupport.map(response.get("response"));
        Map<String, Object> body = envelope == null ? null : ExternalAdapterSupport.map(envelope.get("body"));
        return body == null ? 0 : ExternalAdapterSupport.intValue(body.get("totalCount"), 0);
    }

    private List<DailyForecast> aggregateByDay(List<Map<String, Object>> items) {
        Map<String, DailyForecast> byDate = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String date = value(item, "fcstDate");
            String category = value(item, "category");
            String forecastValue = value(item, "fcstValue");
            if (date == null || category == null || forecastValue == null) {
                continue;
            }
            DailyForecast day = byDate.computeIfAbsent(date, ignored -> {
                DailyForecast created = new DailyForecast();
                created.date = date;
                return created;
            });
            switch (category) {
                case "TMN" -> day.minTemp = parseDouble(forecastValue);
                case "TMX" -> day.maxTemp = parseDouble(forecastValue);
                case "TMP" -> add(day.tmpValues, parseDouble(forecastValue));
                case "POP" -> add(day.popValues, parseInt(forecastValue));
                case "PCP" -> add(day.pcpValues, parsePcp(forecastValue));
                case "REH" -> {
                    Double value = parseDouble(forecastValue);
                    if (value != null && value >= 0 && value <= 100) {
                        day.rehValues.add(value);
                    }
                }
                case "WSD" -> {
                    Double value = parseDouble(forecastValue);
                    if (value != null && value >= 0) {
                        day.wsdValues.add(value);
                    }
                }
                default -> {
                    // Irrelevant KMA category.
                }
            }
        }
        List<DailyForecast> result = new ArrayList<>();
        for (DailyForecast day : byDate.values()) {
            day.finalize_();
            result.add(day);
            if (result.size() == 3) {
                break;
            }
        }
        return result;
    }

    private List<NormalizedMetric> metricsFor(List<DailyForecast> forecasts, String regionCode, boolean fallback) {
        List<NormalizedMetric> metrics = new ArrayList<>();
        for (DailyForecast day : forecasts) {
            addMetric(metrics, "forecast.min_temperature", day.minTemp, "C", regionCode, day.date, fallback);
            addMetric(metrics, "forecast.max_temperature", day.maxTemp, "C", regionCode, day.date, fallback);
            addMetric(metrics, "forecast.precipitation_probability", day.popMax == null ? null : day.popMax.doubleValue(), "%", regionCode, day.date, fallback);
            addMetric(metrics, "forecast.precipitation", day.pcpTotal, "mm", regionCode, day.date, fallback);
            addMetric(metrics, "forecast.humidity", day.rehAvg, "%", regionCode, day.date, fallback);
            addMetric(metrics, "forecast.wind_speed", day.wsdMax, "m/s", regionCode, day.date, fallback);
        }
        return metrics;
    }

    private void addMetric(List<NormalizedMetric> target, String name, Double value, String unit,
                           String regionCode, String date, boolean fallback) {
        if (value != null) {
            target.add(ExternalAdapterSupport.metric(name, value, null, unit, PROVIDER, SERVICE, "GRID",
                    regionCode, date, fallback, false, "GOOD", List.of()));
        }
    }

    /**
     * KMA precipitation normalization: no rain -> 0, below 1mm -> 0.5, ranges
     * -> midpoint, and an open-ended range -> its lower bound.
     */
    public Double parsePcp(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if ("강수없음".equals(normalized)) {
            return 0.0;
        }
        if ("1.0mm 미만".equals(normalized) || "1.0mm미만".equals(normalized)) {
            return 0.5;
        }
        if (normalized.contains("~")) {
            String[] parts = normalized.replace("mm", "").split("~", -1);
            if (parts.length == 2) {
                Double low = parseDouble(parts[0]);
                Double high = parseDouble(parts[1]);
                return low == null || high == null ? null : (low + high) / 2.0;
            }
            return null;
        }
        if (normalized.contains("이상")) {
            return parseDouble(normalized.replace("mm", "").replace("이상", ""));
        }
        return parseDouble(normalized.replace("mm", ""));
    }

    private String[] findBaseDateTime(ZonedDateTime now) {
        ZonedDateTime adjusted = now.minusMinutes(15);
        String date = adjusted.format(DATE_FMT);
        String currentTime = adjusted.format(DateTimeFormatter.ofPattern("HHmm"));
        for (int index = BASE_TIMES.size() - 1; index >= 0; index--) {
            if (currentTime.compareTo(BASE_TIMES.get(index)) >= 0) {
                return new String[]{date, BASE_TIMES.get(index)};
            }
        }
        return new String[]{adjusted.minusDays(1).format(DATE_FMT), "2300"};
    }

    private String[] previousBaseTime(String baseDate, String baseTime) {
        int index = BASE_TIMES.indexOf(baseTime);
        if (index <= 0) {
            return new String[]{LocalDate.parse(baseDate, DATE_FMT).minusDays(1).format(DATE_FMT), "2300"};
        }
        return new String[]{baseDate, BASE_TIMES.get(index - 1)};
    }

    private String value(Map<String, Object> item, String key) {
        Object value = item.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <T> void add(List<T> values, T value) {
        if (value != null) {
            values.add(value);
        }
    }
}
