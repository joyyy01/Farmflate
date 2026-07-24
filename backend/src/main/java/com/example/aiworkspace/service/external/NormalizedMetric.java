package com.example.aiworkspace.service.external;

import com.example.aiworkspace.service.analysis.LocationResolution;

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
        List<String> validationFlags,
        String evidenceLevel,
        LocationResolution location,
        Double distanceMeters,
        List<String> transformations,
        String sourceRecordId,
        Instant measurementOrIssueAt,
        String fallbackReason) {

    public NormalizedMetric {
        retrievedAt = retrievedAt == null ? Instant.now() : retrievedAt;
        quality = quality == null || quality.isBlank() ? "UNKNOWN" : quality;
        validationFlags = validationFlags == null ? List.of() : List.copyOf(validationFlags);
        evidenceLevel = normalizeEvidenceLevel(evidenceLevel);
        transformations = transformations == null ? List.of() : List.copyOf(transformations);
        fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? null : fallbackReason;
    }

    public NormalizedMetric asCached() {
        if (isCached) {
            return this;
        }
        return new NormalizedMetric(metric, numericValue, textValue, unit, provider, service, spatialLevel,
                regionCode, dataDate, retrievedAt, true, isFallback, isReplay, quality, validationFlags,
                evidenceLevel, location, distanceMeters, transformations, sourceRecordId, measurementOrIssueAt,
                fallbackReason);
    }

    public NormalizedMetric withValidationFlag(String validationFlag) {
        if (validationFlag == null || validationFlag.isBlank() || validationFlags.contains(validationFlag)) {
            return this;
        }
        List<String> flags = new ArrayList<>(validationFlags);
        flags.add(validationFlag);
        return new NormalizedMetric(metric, numericValue, textValue, unit, provider, service, spatialLevel,
                regionCode, dataDate, retrievedAt, isCached, isFallback, isReplay, quality, flags,
                evidenceLevel, location, distanceMeters, transformations, sourceRecordId, measurementOrIssueAt,
                fallbackReason);
    }

    private static String normalizeEvidenceLevel(String value) {
        if (value == null || value.isBlank()) {
            return "U";
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "A", "B", "C", "U" -> normalized;
            default -> "U";
        };
    }
}
