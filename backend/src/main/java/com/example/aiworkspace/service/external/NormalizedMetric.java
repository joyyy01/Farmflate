package com.example.aiworkspace.service.external;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A provider-independent value plus the provenance needed to use it safely in
 * a regional analysis.  The record is deliberately immutable so cached and
 * in-flight requests cannot leak mutable provider payloads between callers.
 */
public record NormalizedMetric(
        String metric,
        Double numericValue,
        String textValue,
        String unit,
        String provider,
        String service,
        String spatialLevel,
        String regionCode,
        String dataDate,
        Instant retrievedAt,
        boolean isCached,
        boolean isFallback,
        boolean isReplay,
        String quality,
        List<String> validationFlags) {

    public NormalizedMetric {
        retrievedAt = retrievedAt == null ? Instant.now() : retrievedAt;
        quality = quality == null || quality.isBlank() ? "UNKNOWN" : quality;
        validationFlags = validationFlags == null ? List.of() : List.copyOf(validationFlags);
    }

    public NormalizedMetric asCached() {
        if (isCached) {
            return this;
        }
        return new NormalizedMetric(metric, numericValue, textValue, unit, provider, service, spatialLevel,
                regionCode, dataDate, retrievedAt, true, isFallback, isReplay, quality, validationFlags);
    }

    public NormalizedMetric withValidationFlag(String validationFlag) {
        if (validationFlag == null || validationFlag.isBlank() || validationFlags.contains(validationFlag)) {
            return this;
        }
        List<String> flags = new ArrayList<>(validationFlags);
        flags.add(validationFlag);
        return new NormalizedMetric(metric, numericValue, textValue, unit, provider, service, spatialLevel,
                regionCode, dataDate, retrievedAt, isCached, isFallback, isReplay, quality, flags);
    }
}
