package com.farmflate.dto.region;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Optional user location input. Canonical sido/sigungu identity remains on
 * {@link RegionAnalysisRequestDto}; this object can only refine that region.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationRequestDto {

    private static final Set<String> DERIVED_FIELDS = Set.of(
            "score", "evidenceLevel", "evidenceGrade", "kmaNx", "kmaNy", "asosStationId");

    private String address;
    private Double latitude;
    private Double longitude;
    private String pnu;
    private Boolean useRegionReference;
    private String parcelSoilTestReference;

    /** Reject server-derived location/report values rather than silently trusting them. */
    @JsonAnySetter
    public void rejectUnknownLocationField(String name, Object value) {
        if (DERIVED_FIELDS.contains(name)) {
            throw new IllegalArgumentException("Client-supplied derived location value is not allowed: " + name);
        }
        throw new IllegalArgumentException("Unknown location field: " + name);
    }

    public boolean hasExactlyOnePrimaryLocator() {
        int primaryLocatorCount = 0;
        if (hasAddress()) {
            primaryLocatorCount++;
        }
        if (latitude != null || longitude != null) {
            if (!hasCompleteValidCoordinates()) {
                return false;
            }
            primaryLocatorCount++;
        }
        if (hasPnu()) {
            primaryLocatorCount++;
        }
        if (isRegionReference()) {
            primaryLocatorCount++;
        }
        return primaryLocatorCount == 1
                && (!hasAddress() || address.length() <= 500)
                && (!hasPnu() || pnu.matches("\\d{19}"))
                && (parcelSoilTestReference == null || parcelSoilTestReference.length() <= 200);
    }

    public boolean hasAddress() {
        return address != null && !address.isBlank();
    }

    public boolean hasPnu() {
        return pnu != null && !pnu.isBlank();
    }

    public boolean hasCompleteValidCoordinates() {
        return latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }

    public boolean isRegionReference() {
        return Boolean.TRUE.equals(useRegionReference);
    }

    public String normalizedPnu() {
        return pnu == null ? null : pnu.trim();
    }

    public String normalizedAddress() {
        return address == null ? null : address.trim();
    }
}
