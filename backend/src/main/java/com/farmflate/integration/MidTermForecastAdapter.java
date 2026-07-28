package com.farmflate.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes KMA's day 4–10 temperature forecast into the same typed boundary
 * used by the decision engine.  Mid-term temperatures are published for KMA
 * forecast stations, not every legal district, so the returned records are
 * explicitly representative-area data and never replace the exact-grid short
 * forecast used for the immediate three-day guidance.
 */
@Slf4j
@Component
public class MidTermForecastAdapter {
    private static final String BASE_URL = "http://apis.data.go.kr/1360000/MidFcstInfoService/getMidTa";
    private static final String PROVIDER = "KMA";
    private static final String SERVICE = "MidFcstInfoService/getMidTa";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TM_FC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** KMA representative stations. An operator can replace a region through MIDTERM_REGION_OVERRIDES. */
    private static final Map<String, String> REPRESENTATIVE_REGION_CODES = Map.ofEntries(
            Map.entry("서울특별시", "11B10101"), Map.entry("인천광역시", "11B20201"),
            Map.entry("경기도", "11B20601"), Map.entry("강원특별자치도", "11D10301"), Map.entry("강원도", "11D10301"),
            Map.entry("대전광역시", "11C20401"), Map.entry("세종특별자치시", "11C20404"),
            Map.entry("충청북도", "11C10301"), Map.entry("충청남도", "11C20101"),
            Map.entry("광주광역시", "21F20501"), Map.entry("전북특별자치도", "11F10201"), Map.entry("전라북도", "11F10201"),
            Map.entry("전라남도", "21F20801"), Map.entry("대구광역시", "11H10701"),
            Map.entry("경상북도", "11H10501"), Map.entry("부산광역시", "11H20201"),
            Map.entry("울산광역시", "11H20101"), Map.entry("경상남도", "11H20301"),
            Map.entry("제주특별자치도", "11G00201"), Map.entry("제주도", "11G00201")
    );

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final int retryCount;
    private final int cacheMinutes;
    private final Map<String, String> configuredOverrides;
    private final Map<String, CachedForecast> cache = new ConcurrentHashMap<>();

    public MidTermForecastAdapter(
            @Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.data-go-kr.service-key}") String serviceKey,
            @Value("${app.external-api.retry-count:1}") int retryCount,
            @Value("${app.cache.midterm-forecast-minutes:360}") int cacheMinutes,
            @Value("${app.external-api.midterm-region-overrides:}") String configuredOverrides) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.retryCount = retryCount;
        this.cacheMinutes = cacheMinutes;
        this.configuredOverrides = parseOverrides(configuredOverrides);
    }

    public static class DailyForecast {
        public String date;
        public Double minTemp;
        public Double maxTemp;
        /** KMA's district-level mid-term product is a representative station forecast. */
        public boolean representativeArea = true;
        public String regionCode;
    }

    private record CachedForecast(ExternalResult<List<DailyForecast>> result, Instant storedAt) {
    }

    public ExternalResult<List<DailyForecast>> getForecast4To10Days(String sidoName, String sigunguCode) {
        String regionCode = configuredOverrides.getOrDefault(sigunguCode, REPRESENTATIVE_REGION_CODES.get(sidoName));
        if (regionCode == null || regionCode.isBlank()) {
            return ExternalResult.failure("MIDTERM_LOCATION_NOT_RESOLVED");
        }
        String tmFc = latestPublicationTime(ZonedDateTime.now(KST));
        String cacheKey = regionCode + ":" + tmFc;
        CachedForecast cached = cache.get(cacheKey);
        if (cached != null && Duration.between(cached.storedAt(), Instant.now()).toMinutes() < cacheMinutes) {
            return cached.result().asCached();
        }

        ExternalResult<List<DailyForecast>> result = request(regionCode, tmFc);
        if (result.isEmpty()) {
            String previous = previousPublicationTime(tmFc);
            result = request(regionCode, previous);
        }
        if (result.isSuccess()) cache.put(cacheKey, new CachedForecast(result, Instant.now()));
        return result;
    }

    /** Contract-testable raw payload boundary. */
    public ExternalResult<List<DailyForecast>> parse(String body, String contentType, String tmFc, String regionCode) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseJsonObject(body, contentType);
        if (parsed.isFailure()) return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        Map<String, Object> response = ExternalAdapterSupport.map(parsed.value().get("response"));
        Map<String, Object> header = ExternalAdapterSupport.map(response == null ? null : response.get("header"));
        String resultCode = ExternalAdapterSupport.providerResultCode(parsed.value());
        if (header == null || resultCode == null) return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        if (ExternalAdapterSupport.isProviderNoDataCode(resultCode)) return ExternalResult.empty();
        if (!ExternalAdapterSupport.isProviderSuccessCode(resultCode)) return ExternalResult.failure("KMA_MIDTERM_PROVIDER_" + resultCode);
        Map<String, Object> bodyMap = ExternalAdapterSupport.map(response.get("body"));
        Map<String, Object> items = ExternalAdapterSupport.map(bodyMap == null ? null : bodyMap.get("items"));
        List<Map<String, Object>> rows = items == null ? List.of() : ExternalAdapterSupport.mapList(items.get("item"));
        if (rows.isEmpty()) return ExternalResult.empty();
        List<DailyForecast> forecasts = toForecasts(rows.get(0), tmFc, regionCode);
        return forecasts.isEmpty() ? ExternalResult.empty() : ExternalResult.success(forecasts, metricsFor(forecasts));
    }

    private ExternalResult<List<DailyForecast>> request(String regionCode, String tmFc) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("ServiceKey", serviceKey).queryParam("pageNo", 1).queryParam("numOfRows", 10)
                .queryParam("dataType", "JSON").queryParam("regId", regionCode).queryParam("tmFc", tmFc)
                .build().encode().toUri();
        ExternalResult<String> response = ExternalAdapterSupport.executeRequest(
                retryCount, "KMA_MIDTERM_REQUEST_FAILED", () -> restTemplate.getForObject(uri, String.class));
        if (response.isFailure()) return ExternalResult.failure(response.errorCode(), response.metrics());
        return parse(response.value(), "application/json", tmFc, regionCode);
    }

    private List<DailyForecast> toForecasts(Map<String, Object> row, String tmFc, String regionCode) {
        LocalDate publicationDate;
        try {
            publicationDate = LocalDateTime.parse(tmFc, TM_FC_FORMAT).toLocalDate();
        } catch (Exception exception) {
            return List.of();
        }
        List<DailyForecast> forecasts = new ArrayList<>();
        for (int offset = 4; offset <= 10; offset++) {
            Double min = boundedTemperature(row.get("taMin" + offset));
            Double max = boundedTemperature(row.get("taMax" + offset));
            // A single-side temperature is not enough to assess both heat and
            // cold risk.  Do not promote an incomplete day to a successful
            // forecast: omitting it lets the analysis surface it as missing
            // data rather than creating false confidence.
            if (min == null || max == null) continue;
            DailyForecast forecast = new DailyForecast();
            forecast.date = publicationDate.plusDays(offset).toString();
            forecast.minTemp = min;
            forecast.maxTemp = max;
            forecast.regionCode = regionCode;
            forecasts.add(forecast);
        }
        return forecasts;
    }

    private List<NormalizedMetric> metricsFor(List<DailyForecast> forecasts) {
        List<NormalizedMetric> metrics = new ArrayList<>();
        for (DailyForecast day : forecasts) {
            metrics.add(ExternalAdapterSupport.metric("midterm.min_temperature", day.minTemp, null, "C", PROVIDER,
                    SERVICE, "REPRESENTATIVE_FORECAST_STATION", day.regionCode, day.date, false, false,
                    "REPRESENTATIVE_AREA", List.of("MIDTERM_FORECAST")));
            metrics.add(ExternalAdapterSupport.metric("midterm.max_temperature", day.maxTemp, null, "C", PROVIDER,
                    SERVICE, "REPRESENTATIVE_FORECAST_STATION", day.regionCode, day.date, false, false,
                    "REPRESENTATIVE_AREA", List.of("MIDTERM_FORECAST")));
        }
        return metrics;
    }

    private String latestPublicationTime(ZonedDateTime now) {
        ZonedDateTime publication = now.getHour() >= 18 ? now.withHour(18) : now.withHour(6);
        if (now.getHour() < 6) publication = publication.minusDays(1).withHour(18);
        return publication.withMinute(0).withSecond(0).withNano(0).format(TM_FC_FORMAT);
    }

    private String previousPublicationTime(String tmFc) {
        try {
            return LocalDateTime.parse(tmFc, TM_FC_FORMAT).minusHours(12).format(TM_FC_FORMAT);
        } catch (Exception exception) {
            return tmFc;
        }
    }

    private Double boundedTemperature(Object value) {
        if (value instanceof Number number) return validTemperature(number.doubleValue());
        try {
            return validTemperature(Double.parseDouble(String.valueOf(value)));
        } catch (Exception exception) {
            return null;
        }
    }

    private Double validTemperature(double value) {
        return Double.isFinite(value) && value >= -60 && value <= 60 ? value : null;
    }

    private Map<String, String> parseOverrides(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        Map<String, String> overrides = new ConcurrentHashMap<>();
        for (String pair : raw.split(",")) {
            String[] entry = pair.trim().split("=", 2);
            if (entry.length == 2 && entry[0].matches("\\d+") && entry[1].matches("[0-9A-Z]+")) {
                overrides.put(entry[0], entry[1]);
            }
        }
        return Map.copyOf(overrides);
    }
}
