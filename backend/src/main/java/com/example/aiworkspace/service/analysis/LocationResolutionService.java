package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.domain.region.Region;
import com.example.aiworkspace.dto.region.LocationRequestDto;
import com.example.aiworkspace.service.external.AddressLocationAdapter;
import com.example.aiworkspace.service.external.ParcelIdentifierAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Resolves an optional precise locator without substituting a different region. */
@Service
@RequiredArgsConstructor
public class LocationResolutionService {

    private final AddressLocationAdapter addressLocationAdapter;
    private final ParcelIdentifierAdapter parcelIdentifierAdapter;
    private final KmaGridConverter kmaGridConverter;

    public LocationResolution resolve(LocationRequestDto request, Region region) {
        if (region == null || !region.hasRegionalWeatherMapping()) {
            throw RegionAnalysisService.RegionAnalysisException.mappingNotConfigured(
                    region == null ? "UNKNOWN" : region.getSidoCode(),
                    region == null ? "UNKNOWN" : region.getSigunguCode());
        }
        if (request == null || request.isRegionReference()) {
            return regionalResolution(region, null, null, "REGION_REFERENCE", null);
        }
        if (!request.hasExactlyOnePrimaryLocator()) {
            throw RegionAnalysisService.RegionAnalysisException.invalidRequest();
        }

        if (request.hasAddress()) {
            AddressLocationAdapter.Coordinate coordinate = addressLocationAdapter.resolve(request.normalizedAddress())
                    .orElseThrow(() -> RegionAnalysisService.RegionAnalysisException
                            .locationResolutionUnavailable("ADDRESS_TO_COORDINATE_UNAVAILABLE"));
            return resolveCoordinate(region, coordinate, null, request.getParcelSoilTestReference(),
                    "ADDRESS_TO_COORDINATE");
        }
        if (request.hasCompleteValidCoordinates()) {
            return resolveCoordinate(region,
                    new AddressLocationAdapter.Coordinate(null, request.getLatitude(), request.getLongitude(), "USER_COORDINATE"),
                    null, request.getParcelSoilTestReference(), "INPUT_COORDINATE");
        }

        String pnu = request.normalizedPnu();
        Optional<AddressLocationAdapter.Coordinate> coordinate = parcelIdentifierAdapter.resolveCoordinate(pnu);
        if (coordinate.isPresent()) {
            return resolveCoordinate(region, coordinate.get(), pnu, request.getParcelSoilTestReference(),
                    "PNU_TO_COORDINATE");
        }
        return regionalResolution(region, null, pnu, "PNU_TO_REGIONAL_MAPPING",
                "PNU_COORDINATE_LOOKUP_UNAVAILABLE");
    }

    private LocationResolution resolveCoordinate(
            Region region,
            AddressLocationAdapter.Coordinate coordinate,
            String suppliedPnu,
            String parcelSoilTestReference,
            String initialTransformation) {
        KmaGridConverter.GridCoordinate grid;
        try {
            grid = kmaGridConverter.toGrid(coordinate.latitude(), coordinate.longitude());
        } catch (IllegalArgumentException exception) {
            throw RegionAnalysisService.RegionAnalysisException.locationResolutionUnavailable("INVALID_COORDINATE_RESULT");
        }

        List<String> sourceRefs = new ArrayList<>();
        addIfPresent(sourceRefs, coordinate.sourceRef());
        List<String> transformations = new ArrayList<>(List.of(initialTransformation));
        List<String> flags = new ArrayList<>();
        addParcelSoilReference(sourceRefs, flags, parcelSoilTestReference);

        String pnu = suppliedPnu;
        String evidenceLevel = "C";
        String spatialLevel = "COORDINATE";
        String precisionBadge = "COORDINATE";
        String fallbackReason = null;
        if (pnu == null) {
            Optional<ParcelIdentifierAdapter.ParcelIdentifier> parcel = parcelIdentifierAdapter
                    .resolvePnu(coordinate.latitude(), coordinate.longitude());
            if (parcel.isPresent()) {
                pnu = parcel.get().pnu();
                addIfPresent(sourceRefs, parcel.get().sourceRef());
                transformations.add("COORDINATE_TO_PNU");
                evidenceLevel = "B";
                spatialLevel = "PARCEL";
                precisionBadge = "PARCEL";
            } else {
                flags.add("PNU_LOOKUP_UNAVAILABLE");
                fallbackReason = "PNU_LOOKUP_UNAVAILABLE";
            }
        } else {
            transformations.add("OFFICIAL_PNU_COORDINATE_LOOKUP");
            sourceRefs.add("OFFICIAL_PARCEL_IDENTIFIER");
            evidenceLevel = "B";
            spatialLevel = "PARCEL";
            precisionBadge = "PARCEL";
        }

        transformations.add("COORDINATE_TO_KMA_GRID");
        transformations.add("REGION_TO_NEAREST_ASOS_STATION");
        sourceRefs.add("KMA_GRID_CONVERTER");
        sourceRefs.add(region.regionalMappingSourceRef());
        flags.add("REGIONAL_STATION_MAPPING");

        return new LocationResolution(
                coordinate.addressLabel(),
                coordinate.latitude(),
                coordinate.longitude(),
                pnu,
                grid.nx(),
                grid.ny(),
                region.getAsosStationId(),
                spatialLevel,
                precisionBadge,
                evidenceLevel,
                sourceRefs,
                transformations,
                flags,
                fallbackReason);
    }

    private LocationResolution regionalResolution(
            Region region,
            String addressLabel,
            String pnu,
            String transformation,
            String fallbackReason) {
        List<String> sourceRefs = new ArrayList<>(List.of(region.regionalMappingSourceRef()));
        if (pnu != null) {
            sourceRefs.add("USER_PNU:" + pnu);
        }
        List<String> flags = new ArrayList<>(List.of("REGIONAL_MAPPING"));
        if (fallbackReason != null) {
            flags.add(fallbackReason);
        }
        return new LocationResolution(
                addressLabel,
                null,
                null,
                pnu,
                region.getKmaNx(),
                region.getKmaNy(),
                region.getAsosStationId(),
                "SIGUNGU",
                "REGIONAL",
                "C",
                sourceRefs,
                List.of(transformation, "REGION_TO_NEAREST_ASOS_STATION"),
                flags,
                fallbackReason);
    }

    private void addParcelSoilReference(List<String> sourceRefs, List<String> flags, String reference) {
        if (reference != null && !reference.isBlank()) {
            sourceRefs.add("USER_PARCEL_SOIL_TEST:" + reference.trim());
            flags.add("USER_PARCEL_SOIL_TEST_REFERENCE");
        }
    }

    private void addIfPresent(List<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }
}
