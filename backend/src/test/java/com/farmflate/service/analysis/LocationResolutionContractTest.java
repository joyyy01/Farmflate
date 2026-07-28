package com.farmflate.service.analysis;

import com.farmflate.domain.region.Region;
import com.farmflate.dto.region.LocationRequestDto;
import com.farmflate.integration.AddressLocationAdapter;
import com.farmflate.integration.ParcelIdentifierAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationResolutionContractTest {

    private static final String ADDRESS = "전북특별자치도 고창군 고창읍 중앙로 1";
    private static final String PNU = "5279025021100010000";

    @Mock
    private AddressLocationAdapter addressLocationAdapter;

    @Mock
    private ParcelIdentifierAdapter parcelIdentifierAdapter;

    private LocationResolutionService service;

    @BeforeEach
    void setUp() {
        service = new LocationResolutionService(addressLocationAdapter, parcelIdentifierAdapter, new KmaGridConverter());
    }

    @Test
    void address_chain_keeps_official_parcel_provenance_and_degrades_without_an_invented_pnu() {
        AddressLocationAdapter.Coordinate coordinate = new AddressLocationAdapter.Coordinate(
                ADDRESS, 35.4358, 126.7020, "FIXTURE_ADDRESS_GEOCODE");
        when(addressLocationAdapter.resolve(ADDRESS)).thenReturn(Optional.of(coordinate));
        when(parcelIdentifierAdapter.resolvePnu(35.4358, 126.7020))
                .thenReturn(Optional.of(new ParcelIdentifierAdapter.ParcelIdentifier(PNU, "FIXTURE_OFFICIAL_PNU")));

        LocationResolution resolved = service.resolve(LocationRequestDto.builder().address(ADDRESS).build(), region());

        assertThat(resolved.addressLabel()).isEqualTo(ADDRESS);
        assertThat(resolved.latitude()).isEqualTo(35.4358);
        assertThat(resolved.longitude()).isEqualTo(126.7020);
        assertThat(resolved.pnu()).isEqualTo(PNU);
        assertThat(resolved.kmaNx()).isEqualTo(55);
        assertThat(resolved.kmaNy()).isEqualTo(80);
        assertThat(resolved.asosStationId()).isEqualTo("146");
        assertThat(resolved.evidenceLevel()).isEqualTo("B");
        assertThat(resolved.transformations()).containsExactly(
                "ADDRESS_TO_COORDINATE", "COORDINATE_TO_PNU", "COORDINATE_TO_KMA_GRID", "REGION_TO_NEAREST_ASOS_STATION");
        assertThat(resolved.sourceRefs()).contains("FIXTURE_ADDRESS_GEOCODE", "FIXTURE_OFFICIAL_PNU", "KMA_GRID_CONVERTER");

        when(parcelIdentifierAdapter.resolvePnu(35.4358, 126.7020)).thenReturn(Optional.empty());
        LocationResolution degraded = service.resolve(LocationRequestDto.builder().address(ADDRESS).build(), region());

        assertThat(degraded.pnu()).isNull();
        assertThat(degraded.evidenceLevel()).isEqualTo("C");
        assertThat(degraded.precisionBadge()).isEqualTo("COORDINATE");
        assertThat(degraded.validationFlags()).contains("PNU_LOOKUP_UNAVAILABLE", "REGIONAL_STATION_MAPPING");
        assertThat(degraded.fallbackReason()).isEqualTo("PNU_LOOKUP_UNAVAILABLE");
        assertThat(degraded.kmaNx()).isEqualTo(55);
        assertThat(degraded.kmaNy()).isEqualTo(80);
        assertThat(degraded.asosStationId()).isEqualTo("146");

        when(addressLocationAdapter.resolve(ADDRESS)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolve(LocationRequestDto.builder().address(ADDRESS).build(), region()))
                .isInstanceOfSatisfying(RegionAnalysisService.RegionAnalysisException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LOCATION_RESOLUTION_UNAVAILABLE"));
    }

    private Region region() {
        return Region.builder()
                .sidoCode("52")
                .sidoName("전북특별자치도")
                .sigunguCode("52180")
                .sigunguName("고창군")
                .kmaNx(56)
                .kmaNy(80)
                .asosStationId("146")
                .build();
    }
}
