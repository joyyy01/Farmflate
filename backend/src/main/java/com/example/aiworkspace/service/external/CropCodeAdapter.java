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

/** Resolves the five supported crops to their provider-issued soil crop codes. */
@Slf4j
@Component
public class CropCodeAdapter {

    private static final String BASE_URL = "http://apis.data.go.kr/1390802/SoilEnviron_cropInfo/getCropInfo";
    private static final String PROVIDER = "RDA";
    private static final String SERVICE = "SoilEnviron_cropInfo";
    private static final Map<String, String> CROP_NAME_MAP = cropNames();

    private final RestTemplate restTemplate;
    private final String serviceKey;
    private final int cacheDays;
    private final boolean replay;
    private final Map<String, CachedCropCodes> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ExternalResult<Map<String, CropCodeMapping>>>> inFlight = new ConcurrentHashMap<>();

    public CropCodeAdapter(
            @Qualifier("externalApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.data-go-kr.service-key}") String serviceKey,
            @Value("${app.cache.crop-code-days:90}") int cacheDays,
            @Value("${app.data-mode:LIVE}") String dataMode) {
        this.restTemplate = restTemplate;
        this.serviceKey = serviceKey;
        this.cacheDays = cacheDays;
        this.replay = "REPLAY".equalsIgnoreCase(dataMode);
    }

    public static class CropCodeMapping {
        public String internalCode;
        public String apiCropCode;
        public String cropName;
        public boolean resolved;
    }

    private record CachedCropCodes(ExternalResult<Map<String, CropCodeMapping>> result, Instant cachedAt) {
    }

    public ExternalResult<Map<String, CropCodeMapping>> getCropCodeMappings() {
        String cacheKey = "crop_codes_all";
        return ExternalAdapterSupport.executeOnce(inFlight, cacheKey, () -> {
            CachedCropCodes cached = cache.get(cacheKey);
            if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).toDays() < cacheDays) {
                return cached.result().asCached();
            }
            ExternalResult<Map<String, CropCodeMapping>> result = resolveAll(this::lookupCropCode);
            if (!result.isFailure()) {
                cache.put(cacheKey, new CachedCropCodes(result, Instant.now()));
            }
            return result;
        });
    }

    /** Testable raw-payload boundary for fixture-backed contract tests. */
    public ExternalResult<Map<String, CropCodeMapping>> parse(String body, String contentType) {
        ExternalResult<Map<String, Object>> parsed = ExternalAdapterSupport.parseJsonObject(body, contentType);
        if (parsed.isFailure()) {
            return ExternalResult.failure(parsed.errorCode(), parsed.metrics());
        }
        return resolveAll(cropName -> extractCode(parsed.value(), cropName));
    }

    private ExternalResult<Map<String, CropCodeMapping>> resolveAll(CropCodeLookup lookup) {
        Map<String, CropCodeMapping> mappings = new LinkedHashMap<>();
        int resolved = 0;
        int failures = 0;
        for (Map.Entry<String, String> entry : CROP_NAME_MAP.entrySet()) {
            CropCodeMapping mapping = new CropCodeMapping();
            mapping.internalCode = entry.getKey();
            mapping.cropName = entry.getValue();
            ExternalResult<String> code = lookup.lookup(entry.getValue());
            if (code.isSuccess()) {
                mapping.apiCropCode = code.value();
                mapping.resolved = true;
                resolved++;
            } else {
                mapping.resolved = false;
                if (code.isFailure()) {
                    failures++;
                }
            }
            mappings.put(mapping.internalCode, mapping);
        }
        List<NormalizedMetric> metrics = metricsFor(mappings);
        if (resolved > 0) {
            return ExternalResult.success(mappings, metrics);
        }
        if (failures > 0) {
            return ExternalResult.failure("CROP_CODE_PROVIDER_FAILURE", metrics);
        }
        return ExternalResult.empty(metrics);
    }

    @SuppressWarnings("unchecked")
    private ExternalResult<String> lookupCropCode(String cropName) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .queryParam("serviceKey", serviceKey)
                    .queryParam("Page_No", 1)
                    .queryParam("Page_Size", 100)
                    .queryParam("crop_Nm", cropName)
                    .build(false)
                    .toUriString();
            return extractCode(restTemplate.getForObject(url, Map.class), cropName);
        } catch (Exception exception) {
            log.warn("Crop code lookup failed for {}: {}", cropName, exception.getMessage());
            return ExternalResult.failure("CROP_CODE_REQUEST_FAILED");
        }
    }

    private ExternalResult<String> extractCode(Map<String, Object> response, String cropName) {
        if (response == null) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        Map<String, Object> envelope = ExternalAdapterSupport.map(response.get("response"));
        if (envelope != null) {
            Map<String, Object> header = ExternalAdapterSupport.map(envelope.get("header"));
            if (header != null && !"00".equals(String.valueOf(header.get("resultCode")))) {
                return ExternalResult.failure("CROP_CODE_PROVIDER_" + header.get("resultCode"));
            }
            Map<String, Object> body = ExternalAdapterSupport.map(envelope.get("body"));
            if (body == null) {
                return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
            }
            if (ExternalAdapterSupport.map(body.get("items")) == null) {
                return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
            }
            String code = findBestMatch(itemsFrom(body), cropName);
            return code == null ? ExternalResult.empty() : ExternalResult.success(code);
        }
        List<Map<String, Object>> directItems = directItems(response);
        if (directItems.isEmpty()) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        String code = findBestMatch(directItems, cropName);
        return code == null ? ExternalResult.empty() : ExternalResult.success(code);
    }

    private List<Map<String, Object>> itemsFrom(Map<String, Object> body) {
        Map<String, Object> items = ExternalAdapterSupport.map(body.get("items"));
        return items == null ? List.of() : ExternalAdapterSupport.mapList(items.get("item"));
    }

    private List<Map<String, Object>> directItems(Map<String, Object> response) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : response.values()) {
            Map<String, Object> map = ExternalAdapterSupport.map(value);
            if (map != null && map.containsKey("row")) {
                rows.addAll(ExternalAdapterSupport.mapList(map.get("row")));
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> nested = ExternalAdapterSupport.map(item);
                    if (nested != null && nested.containsKey("row")) {
                        rows.addAll(ExternalAdapterSupport.mapList(nested.get("row")));
                    }
                }
            }
        }
        return rows;
    }

    private String findBestMatch(List<Map<String, Object>> items, String cropName) {
        for (Map<String, Object> item : items) {
            String name = string(item, "crop_Nm", "cropNm", "CROP_NM");
            if (cropName.equals(name)) {
                String code = cropCode(item);
                if (code != null) {
                    return code;
                }
            }
        }
        return null;
    }

    private String cropCode(Map<String, Object> item) {
        return string(item, "soil_Crop_CD", "soilCropCd", "SOIL_CROP_CD", "crop_cd", "cropCd");
    }

    private List<NormalizedMetric> metricsFor(Map<String, CropCodeMapping> mappings) {
        List<NormalizedMetric> metrics = new ArrayList<>();
        for (CropCodeMapping mapping : mappings.values()) {
            metrics.add(ExternalAdapterSupport.metric("crop_code", null,
                    mapping.resolved ? mapping.apiCropCode : mapping.cropName, null, PROVIDER, SERVICE,
                    "NATIONAL", mapping.internalCode, null, false, replay,
                    mapping.resolved ? "GOOD" : "PARTIAL",
                    mapping.resolved ? List.of() : List.of("UNRESOLVED")));
        }
        return metrics;
    }

    private String string(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static Map<String, String> cropNames() {
        Map<String, String> cropNames = new LinkedHashMap<>();
        cropNames.put("APPLE", "사과");
        cropNames.put("PEAR", "배");
        cropNames.put("CUCUMBER", "오이");
        cropNames.put("POTATO", "감자");
        cropNames.put("LETTUCE", "상추");
        return Collections.unmodifiableMap(cropNames);
    }

    @FunctionalInterface
    private interface CropCodeLookup {
        ExternalResult<String> lookup(String cropName);
    }
}
