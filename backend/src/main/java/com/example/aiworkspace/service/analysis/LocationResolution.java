package com.example.aiworkspace.service.analysis;

import java.util.List;

/** Immutable, reportable proof of how the analysis location was resolved. */
public record LocationResolution(
        String addressLabel,
        Double latitude,
        Double longitude,
        String pnu,
        Integer kmaNx,
        Integer kmaNy,
        String asosStationId,
        String spatialLevel,
        String precisionBadge,
        String evidenceLevel,
        List<String> sourceRefs,
        List<String> transformations,
        List<String> validationFlags,
        String fallbackReason) {

    public LocationResolution {
        spatialLevel = blankToDefault(spatialLevel, "UNKNOWN");
        precisionBadge = blankToDefault(precisionBadge, "UNKNOWN");
        evidenceLevel = normalizeEvidenceLevel(evidenceLevel);
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        transformations = transformations == null ? List.of() : List.copyOf(transformations);
        validationFlags = validationFlags == null ? List.of() : List.copyOf(validationFlags);
        fallbackReason = fallbackReason == null || fallbackReason.isBlank() ? null : fallbackReason;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String normalizeEvidenceLevel(String value) {
        if (value == null || value.isBlank()) {
            return "U";
        }
        return switch (value.trim().toUpperCase()) {
            case "A", "B", "C", "U" -> value.trim().toUpperCase();
            default -> "U";
        };
    }
}
