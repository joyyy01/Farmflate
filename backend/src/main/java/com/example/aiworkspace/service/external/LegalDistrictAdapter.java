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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Administrative Safety Ministry legal-dong provider normalizer. */
@Slf4j
@Component
public class LegalDistrictAdapter {

    private static final String BASE_URL = "http://apis.data.go.kr/1741000/StanReginCd/getStanReginCdList";
    private static final String PROVIDER = "MOIS";
    private static final String SERVICE = "StanReginCd";

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final int cacheDays;
    private final int retryCount;
    private final boolean replay;
    private final Map<String, CachedDistrict> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ExternalResult<List<LegalDistrict>>>> inFlight = new ConcurrentHashMap<>();

    public LegalDistrictAdapter(
            @Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.data-go-kr.service-key}") String serviceKey,
            @Value("${app.cache.legal-district-days:30}") int cacheDays,
            @Value("${app.external-api.retry-count:1}") int retryCount,
            @Value("${app.data-mode:LIVE}") String dataMode) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.cacheDays = cacheDays;
        this.retryCount = retryCount;
        this.replay = "REPLAY".equalsIgnoreCase(dataMode);
    }

    public static class LegalDistrict {
        public String regionCd;
        public String locataddNm;
        public String locallowNm;
    }

    private record CachedDistrict(ExternalResult<List<LegalDistrict>> result, Instant cachedAt) {
    }

    /** Returns active, de-duplicated eup/myeon/dong rows only. */
    public ExternalResult<List<LegalDistrict>> getDistrictCodes(String sidoName, String sigunguName) {
        String cacheKey = sidoName + "_" + sigunguName;
        return ExternalAdapterSupport.executeOnce(inFlight, cacheKey, () -> {
            CachedDistrict cached = cache.get(cacheKey);
            if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).toDays() < cacheDays) {
                return cached.result().asCached();
            }
            ExternalResult<List<LegalDistrict>> result = fetchDistricts(sidoName + " " + sigunguName);
            if (!result.isFailure()) {
                cache.put(cacheKey, new CachedDistrict(result, Instant.now()));
            }
            return result;
        });
    }

    /** Testable raw-payload boundary for fixture-backed contract tests. */
    public ExternalResult<List<LegalDistrict>> parse(String body, String contentType) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseJsonObject(body, contentType);
        if (parsed.isFailure()) {
            return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        }
        return normalize(parsed.value(), "fixture");
    }

    private ExternalResult<List<LegalDistrict>> fetchDistricts(String locationName) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("ServiceKey", serviceKey)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1000)
                .queryParam("type", "json")
                .queryParam("locatadd_nm", locationName)
                .build()
                .encode()
                .toUri();
        // This provider currently labels its JSON body as text/html; retrieve text
        // first and normalize from the body so RestTemplate does not reject it.
        ExternalResult<String> payload = ExternalAdapterSupport.executeRequest(
                retryCount, "LEGAL_DISTRICT_REQUEST_FAILED", () -> restTemplate.getForObject(uri, String.class));
        if (payload.isFailure()) {
            log.warn("Legal district API failed for {}: {}", locationName, payload.errorCode());
            return ExternalResult.failure(payload.errorCode(), payload.metrics());
        }
        ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.parseJsonObject(payload.value(), null);
        if (response.isFailure()) {
            return ExternalResult.failure(response.errorCode(), response.metrics());
        }
        return normalize(response.value(), locationName);
    }

    private ExternalResult<List<LegalDistrict>> normalize(Map<String, Object> response, String requestedRegion) {
        if (response == null) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        Object rawEnvelope = response.get("StanReginCd");
        List<Map<String, Object>> envelopeParts = ExternalAdapterSupport.mapList(rawEnvelope);
        if (envelopeParts.isEmpty()) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }

        for (Map<String, Object> part : envelopeParts) {
            String providerErrorCode = providerErrorCode(part);
            if (providerErrorCode != null) {
                return ExternalResult.failure("LEGAL_DISTRICT_PROVIDER_" + providerErrorCode);
            }
        }
        List<Map<String, Object>> rows = extractRows(rawEnvelope, 0);
        if (rows == null) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        if (rows.isEmpty()) {
            return ExternalResult.empty();
        }

        Set<String> seenCodes = new HashSet<>();
        List<LegalDistrict> districts = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String regionCode = string(row, "region_cd", "regionCd");
            if (regionCode == null || regionCode.isBlank() || !seenCodes.add(regionCode)
                    || !isActive(row) || !isLegalDong(row)) {
                continue;
            }
            LegalDistrict district = new LegalDistrict();
            district.regionCd = regionCode;
            district.locataddNm = string(row, "locatadd_nm", "locataddNm");
            district.locallowNm = string(row, "locallow_nm", "locallowNm");
            districts.add(district);
        }
        if (districts.isEmpty()) {
            return ExternalResult.empty();
        }
        return ExternalResult.success(districts, metricsFor(districts, requestedRegion));
    }

    private String providerErrorCode(Map<String, Object> value) {
        Map<String, Object> result = ExternalAdapterSupport.map(value.get("RESULT"));
        if (result == null) {
            result = ExternalAdapterSupport.map(value.get("result"));
        }
        String code = string(result, "CODE", "code", "resultCode", "result_Code");
        if (code != null && !isProviderSuccessCode(code)) {
            return code;
        }
        for (Map<String, Object> headPart : ExternalAdapterSupport.mapList(value.get("head"))) {
            String headErrorCode = providerErrorCode(headPart);
            if (headErrorCode != null) {
                return headErrorCode;
            }
        }
        return null;
    }

    private boolean isProviderSuccessCode(String code) {
        return code.startsWith("INFO-0") || "00".equals(code) || "200".equals(code);
    }

    private List<Map<String, Object>> extractRows(Object node, int depth) {
        if (depth > 4) {
            return null;
        }
        Map<String, Object> map = ExternalAdapterSupport.map(node);
        if (map != null) {
            if (map.containsKey("row")) {
                Object rawRows = map.get("row");
                return rawRows instanceof Map<?, ?> || rawRows instanceof List<?>
                        ? ExternalAdapterSupport.mapList(rawRows)
                        : null;
            }
            for (Object value : map.values()) {
                List<Map<String, Object>> rows = extractRows(value, depth + 1);
                if (rows != null) {
                    return rows;
                }
            }
            return null;
        }
        if (node instanceof List<?> values) {
            for (Object value : values) {
                List<Map<String, Object>> rows = extractRows(value, depth + 1);
                if (rows != null) {
                    return rows;
                }
            }
        }
        return null;
    }

    private List<NormalizedMetric> metricsFor(List<LegalDistrict> districts, String requestedRegion) {
        List<NormalizedMetric> metrics = new ArrayList<>();
        metrics.add(ExternalAdapterSupport.metric("legal_district.count", (double) districts.size(), null, "count",
                PROVIDER, SERVICE, "SIGUNGU", requestedRegion, null, false, replay, "GOOD", List.of()));
        for (LegalDistrict district : districts) {
            metrics.add(ExternalAdapterSupport.metric("legal_district.code", null, district.regionCd, null,
                    PROVIDER, SERVICE, "LEGAL_DONG", district.regionCd, null, false, replay, "GOOD", List.of()));
        }
        return metrics;
    }

    private boolean isActive(Map<String, Object> row) {
        String use = string(row, "use_yn", "useYn", "use_at", "useAt", "status");
        if (use != null && ("N".equalsIgnoreCase(use) || "INACTIVE".equalsIgnoreCase(use)
                || "DISABLED".equalsIgnoreCase(use))) {
            return false;
        }
        String deleted = string(row, "del_yn", "delYn", "deleted_yn", "deletedYn");
        return deleted == null || !("Y".equalsIgnoreCase(deleted) || "TRUE".equalsIgnoreCase(deleted));
    }

    private boolean isLegalDong(Map<String, Object> row) {
        return true;
    }

    private String string(Map<String, Object> row, String... keys) {
        if (row == null) {
            return null;
        }
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
}
