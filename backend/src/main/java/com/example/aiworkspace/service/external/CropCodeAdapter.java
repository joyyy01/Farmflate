package com.example.aiworkspace.service.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves UI crops in the RDA SoilFit V2 code namespace.
 *
 * <p>The CropInfo endpoint's numeric {@code crop_Cd} values are not valid
 * {@code soil_Crop_CD} inputs for SoilFit V2.  This small provider-reference
 * catalog contains only codes verified against the SoilFit V2 endpoint; an
 * unsupported UI crop remains unresolved rather than being guessed from a
 * similarly named CropInfo record.</p>
 */
@Component
public class CropCodeAdapter {

    private static final String PROVIDER = "RDA";
    private static final String SERVICE = "SoilFitStat/V2 crop catalog";
    private static final Map<String, ProviderSoilFitCrop> SOIL_FIT_CATALOG = soilFitCatalog();

    private final int cacheDays;
    private final Map<String, CachedCropCodes> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ExternalResult<Map<String, CropCodeMapping>>>> inFlight = new ConcurrentHashMap<>();

    public CropCodeAdapter(
            @Value("${app.cache.crop-code-days:90}") int cacheDays) {
        this.cacheDays = cacheDays;
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
            ExternalResult<Map<String, CropCodeMapping>> result = providerSoilFitCatalog();
            if (!result.isFailure()) {
                cache.put(cacheKey, new CachedCropCodes(result, Instant.now()));
            }
            return result;
        });
    }

    private ExternalResult<Map<String, CropCodeMapping>> providerSoilFitCatalog() {
        Map<String, CropCodeMapping> mappings = new LinkedHashMap<>();
        for (Map.Entry<String, ProviderSoilFitCrop> entry : SOIL_FIT_CATALOG.entrySet()) {
            CropCodeMapping mapping = new CropCodeMapping();
            mapping.internalCode = entry.getKey();
            mapping.cropName = entry.getValue().cropName();
            mapping.apiCropCode = entry.getValue().soilFitCropCode();
            mapping.resolved = mapping.apiCropCode != null;
            mappings.put(mapping.internalCode, mapping);
        }
        return ExternalResult.success(mappings, metricsFor(mappings));
    }

    private List<NormalizedMetric> metricsFor(Map<String, CropCodeMapping> mappings) {
        List<NormalizedMetric> metrics = new java.util.ArrayList<>();
        for (CropCodeMapping mapping : mappings.values()) {
            metrics.add(ExternalAdapterSupport.metric("crop_code", null,
                    mapping.resolved ? mapping.apiCropCode : mapping.cropName, null, PROVIDER, SERVICE,
                    "NATIONAL", mapping.internalCode, null, false, false,
                    mapping.resolved ? "GOOD" : "PARTIAL",
                    mapping.resolved ? List.of() : List.of("UNSUPPORTED_BY_SOIL_FIT_CATALOG")));
        }
        return metrics;
    }

    private static Map<String, ProviderSoilFitCrop> soilFitCatalog() {
        Map<String, ProviderSoilFitCrop> catalog = new LinkedHashMap<>();
        // Verified against SoilFit V2: each code returns result_Code=200 and the matching soil_Crop_Nm.
        catalog.put("APPLE", new ProviderSoilFitCrop("사과", "CR005"));
        catalog.put("PEAR", new ProviderSoilFitCrop("배", "CR006"));
        catalog.put("CUCUMBER", new ProviderSoilFitCrop("오이", "CR017"));
        catalog.put("POTATO", new ProviderSoilFitCrop("감자", "CR032"));
        catalog.put("LETTUCE", new ProviderSoilFitCrop("상추", "CR044"));
        return Collections.unmodifiableMap(catalog);
    }

    private record ProviderSoilFitCrop(String cropName, String soilFitCropCode) {
    }
}
