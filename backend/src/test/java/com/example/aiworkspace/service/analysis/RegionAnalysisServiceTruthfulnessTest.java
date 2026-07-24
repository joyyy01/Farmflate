package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.domain.region.RegionRepository;
import com.example.aiworkspace.service.external.AsosAdapter;
import com.example.aiworkspace.service.external.FixtureProvider;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import com.example.aiworkspace.service.external.SoilChemistryAdapter;
import com.example.aiworkspace.service.external.SoilSuitabilityAdapter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionAnalysisServiceTruthfulnessTest {

    private static final String OWNER = "owner@example.com";

    @Mock private RegionRepository regionRepository;
    @Mock private RegionAnalysisRepository analysisRepository;
    @Mock private FixtureProvider fixtureProvider;
    @Mock private ObjectMapper objectMapper;
    @Mock private ShortForecastAdapter shortForecastAdapter;
    @Mock private AsosAdapter asosAdapter;
    @Mock private SoilChemistryAdapter soilChemistryAdapter;
    @Mock private SoilSuitabilityAdapter soilSuitabilityAdapter;
    @Mock private LocationResolutionService locationResolutionService;
    @Mock private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    private RegionAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new RegionAnalysisService(
                regionRepository,
                analysisRepository,
                new CropScoringEngine(),
                fixtureProvider,
                objectMapper,
                shortForecastAdapter,
                asosAdapter,
                soilChemistryAdapter,
                soilSuitabilityAdapter,
                locationResolutionService,
                applicationEventPublisher);
    }

    @Test
    void invalidPersistedPayload_isTypedFailure_andNeverSilentlyReplayed() throws Exception {
        UUID analysisId = UUID.randomUUID();
        RegionAnalysisEntity entity = RegionAnalysisEntity.builder()
                .id(analysisId.toString())
                .userEmail(OWNER)
                .sidoCode("52")
                .sidoName("전북특별자치도")
                .sigunguCode("52180")
                .sigunguName("고창군")
                .payloadJson("{not-json}")
                .build();
        when(analysisRepository.findByIdAndUserEmail(analysisId.toString(), OWNER)).thenReturn(Optional.of(entity));
        when(objectMapper.readValue(eq("{not-json}"), any(Class.class)))
                .thenThrow(new JsonProcessingException("malformed fixture") { });

        assertThatThrownBy(() -> service.getReport(OWNER, analysisId))
                .isInstanceOfSatisfying(RegionAnalysisService.RegionAnalysisException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REGION_REPORT_PAYLOAD_UNAVAILABLE"));

        verify(fixtureProvider, never()).getGochangFixture(any(), any(), any(), any());
    }
}
