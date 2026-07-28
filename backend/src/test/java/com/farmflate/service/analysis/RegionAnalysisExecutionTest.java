package com.farmflate.service.analysis;

import com.farmflate.domain.region.RegionAnalysisRepository;
import com.farmflate.domain.region.RegionAnalysisEntity;
import com.farmflate.domain.region.RegionRepository;
import com.farmflate.domain.region.Region;
import com.farmflate.dto.region.LocationRequestDto;
import com.farmflate.dto.region.RegionAnalysisRequestDto;
import com.farmflate.integration.ShortForecastAdapter;
import com.farmflate.integration.ExternalResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionAnalysisExecutionTest {

    @Test
    void skips_provider_work_when_another_worker_already_claimed_the_analysis() {
        RegionRepository regionRepository = mock(RegionRepository.class);
        RegionAnalysisRepository analysisRepository = mock(RegionAnalysisRepository.class);
        ExternalDataCollector collector = mock(ExternalDataCollector.class);
        RegionAnalysisService service = new RegionAnalysisService(
                regionRepository,
                analysisRepository,
                mock(CropScoringEngine.class),
                new ObjectMapper(),
                mock(ShortForecastAdapter.class),
                collector,
                mock(LocationResolutionService.class),
                mock(ApplicationEventPublisher.class));
        when(analysisRepository.claimForExecution(eq("analysis-1"), any(LocalDateTime.class), anyString())).thenReturn(0);

        service.executePersistedAnalysis("analysis-1");

        verifyNoInteractions(regionRepository, collector);
    }

    @Test
    void stops_when_a_reclaimed_execution_token_cannot_update_progress() {
        RegionRepository regionRepository = mock(RegionRepository.class);
        RegionAnalysisRepository analysisRepository = mock(RegionAnalysisRepository.class);
        ExternalDataCollector collector = mock(ExternalDataCollector.class);
        LocationResolutionService locationResolutionService = mock(LocationResolutionService.class);
        RegionAnalysisService service = service(regionRepository, analysisRepository, collector, locationResolutionService);
        RegionAnalysisEntity entity = persistedAnalysis();
        Region region = region();
        when(analysisRepository.claimForExecution(eq("analysis-1"), any(LocalDateTime.class), anyString())).thenReturn(1);
        when(analysisRepository.findById("analysis-1")).thenReturn(Optional.of(entity));
        when(regionRepository.findBySidoCodeAndSigunguCode("52", "52180")).thenReturn(Optional.of(region));
        when(locationResolutionService.resolve(any(), eq(region))).thenReturn(location());
        when(analysisRepository.updateProgressIfOwned(eq("analysis-1"), anyString(), eq("RECENT_WEATHER"), anyString()))
                .thenReturn(0);

        service.executePersistedAnalysis("analysis-1");

        verifyNoInteractions(collector);
        verify(analysisRepository, never()).failIfOwned(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean());
        verify(analysisRepository, never()).completeIfOwned(
                anyString(), anyString(), anyString(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void stale_worker_cannot_persist_a_failure_after_its_token_is_reclaimed() {
        RegionRepository regionRepository = mock(RegionRepository.class);
        RegionAnalysisRepository analysisRepository = mock(RegionAnalysisRepository.class);
        ExternalDataCollector collector = mock(ExternalDataCollector.class);
        LocationResolutionService locationResolutionService = mock(LocationResolutionService.class);
        RegionAnalysisService service = service(regionRepository, analysisRepository, collector, locationResolutionService);
        Region region = region();
        when(analysisRepository.claimForExecution(eq("analysis-1"), any(LocalDateTime.class), anyString())).thenReturn(1);
        when(analysisRepository.findById("analysis-1")).thenReturn(Optional.of(persistedAnalysis()));
        when(regionRepository.findBySidoCodeAndSigunguCode("52", "52180")).thenReturn(Optional.of(region));
        when(locationResolutionService.resolve(any(), eq(region))).thenReturn(location());
        when(analysisRepository.updateProgressIfOwned(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(collector.collect(any(), any())).thenReturn(failedCollection());
        when(analysisRepository.failIfOwned(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyBoolean())).thenReturn(0);

        service.executePersistedAnalysis("analysis-1");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(analysisRepository).claimForExecution(eq("analysis-1"), any(LocalDateTime.class), token.capture());
        verify(analysisRepository).failIfOwned(eq("analysis-1"), eq(token.getValue()), anyString(), anyString(),
                anyString(), anyString(), anyBoolean());
        verify(analysisRepository, never()).saveAndFlush(any());
    }

    @Test
    void stale_worker_cannot_complete_after_its_token_is_reclaimed() {
        RegionRepository regionRepository = mock(RegionRepository.class);
        RegionAnalysisRepository analysisRepository = mock(RegionAnalysisRepository.class);
        ExternalDataCollector collector = mock(ExternalDataCollector.class);
        LocationResolutionService locationResolutionService = mock(LocationResolutionService.class);
        RegionAnalysisService service = service(regionRepository, analysisRepository, collector, locationResolutionService);
        Region region = region();
        when(analysisRepository.claimForExecution(eq("analysis-1"), any(LocalDateTime.class), anyString())).thenReturn(1);
        when(analysisRepository.findById("analysis-1")).thenReturn(Optional.of(persistedAnalysis()));
        when(regionRepository.findBySidoCodeAndSigunguCode("52", "52180")).thenReturn(Optional.of(region));
        when(locationResolutionService.resolve(any(), eq(region))).thenReturn(location());
        when(analysisRepository.updateProgressIfOwned(anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(collector.collect(any(), any())).thenReturn(emptyCollection());
        when(analysisRepository.completeIfOwned(anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), any(), anyString(), anyString(), any(), anyString())).thenReturn(0);

        service.executePersistedAnalysis("analysis-1");

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(analysisRepository).claimForExecution(eq("analysis-1"), any(LocalDateTime.class), token.capture());
        verify(analysisRepository).completeIfOwned(eq("analysis-1"), eq(token.getValue()), anyString(), any(), anyString(),
                anyString(), anyString(), any(), anyString(), anyString(), any(), anyString());
        verify(analysisRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejects_a_location_request_that_cannot_be_persisted() throws Exception {
        RegionRepository regionRepository = mock(RegionRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        RegionAnalysisService service = new RegionAnalysisService(
                regionRepository,
                mock(RegionAnalysisRepository.class),
                mock(CropScoringEngine.class),
                objectMapper,
                mock(ShortForecastAdapter.class),
                mock(ExternalDataCollector.class),
                mock(LocationResolutionService.class),
                mock(ApplicationEventPublisher.class));
        Region region = Region.builder().sidoCode("52").sidoName("전북특별자치도")
                .sigunguCode("52180").sigunguName("고창군").build();
        when(regionRepository.findBySidoCodeAndSigunguCode("52", "52180")).thenReturn(java.util.Optional.of(region));
        when(objectMapper.writeValueAsString(any())).thenThrow(new IllegalStateException("serialization failed"));
        RegionAnalysisRequestDto request = RegionAnalysisRequestDto.builder()
                .sidoCode("52").sigunguCode("52180")
                .sidoName("전북특별자치도").sigunguName("고창군")
                .idempotencyKey("location-serialization")
                .location(LocationRequestDto.builder().useRegionReference(true).build())
                .build();

        assertThatThrownBy(() -> service.create("owner@example.com", request))
                .isInstanceOf(RegionAnalysisService.RegionAnalysisException.class)
                .extracting(error -> ((RegionAnalysisService.RegionAnalysisException) error).getCode())
                .isEqualTo("LOCATION_REQUEST_SERIALIZATION_FAILED");
    }

    private RegionAnalysisService service(RegionRepository regionRepository, RegionAnalysisRepository analysisRepository,
                                          ExternalDataCollector collector, LocationResolutionService locationResolutionService) {
        return new RegionAnalysisService(
                regionRepository,
                analysisRepository,
                new CropScoringEngine(),
                new ObjectMapper(),
                mock(ShortForecastAdapter.class),
                collector,
                locationResolutionService,
                mock(ApplicationEventPublisher.class));
    }

    private RegionAnalysisEntity persistedAnalysis() {
        return RegionAnalysisEntity.builder()
                .id("analysis-1")
                .analysisScope("OWNER")
                .scopeSubject("owner@example.com")
                .purpose("PRIMARY")
                .sidoCode("52")
                .sidoName("전북특별자치도")
                .sigunguCode("52180")
                .sigunguName("고창군")
                .build();
    }

    private Region region() {
        return Region.builder().sidoCode("52").sidoName("전북특별자치도")
                .sigunguCode("52180").sigunguName("고창군").build();
    }

    private LocationResolution location() {
        return new LocationResolution("고창군", null, null, null, 55, 125, "146", "REGION", "C", "B",
                List.of(), List.of(), List.of(), null);
    }

    private ExternalDataCollector.CollectedProviderData failedCollection() {
        return new ExternalDataCollector.CollectedProviderData(
                ExternalResult.failure("ASOS_DOWN"),
                ExternalResult.failure("FORECAST_DOWN"),
                ExternalResult.failure("MIDTERM_DOWN"),
                ExternalResult.failure("SOIL_CHEMISTRY_DOWN"),
                ExternalResult.failure("SOIL_SUITABILITY_DOWN"));
    }

    private ExternalDataCollector.CollectedProviderData emptyCollection() {
        return new ExternalDataCollector.CollectedProviderData(
                ExternalResult.empty(),
                ExternalResult.empty(),
                ExternalResult.empty(),
                ExternalResult.empty(),
                ExternalResult.empty());
    }
}
