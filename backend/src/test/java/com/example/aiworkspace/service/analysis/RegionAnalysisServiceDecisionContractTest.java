package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.domain.region.Region;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.domain.region.RegionRepository;
import com.example.aiworkspace.dto.region.RegionAnalysisRequestDto;
import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import com.example.aiworkspace.service.external.AsosAdapter;
import com.example.aiworkspace.service.external.ExternalResult;
import com.example.aiworkspace.service.external.FixtureProvider;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import com.example.aiworkspace.service.external.SoilChemistryAdapter;
import com.example.aiworkspace.service.external.SoilSuitabilityAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionAnalysisServiceDecisionContractTest {

    @Mock private RegionRepository regionRepository;
    @Mock private RegionAnalysisRepository analysisRepository;
    @Mock private FixtureProvider fixtureProvider;
    @Mock private ShortForecastAdapter shortForecastAdapter;
    @Mock private AsosAdapter asosAdapter;
    @Mock private SoilChemistryAdapter soilChemistryAdapter;
    @Mock private SoilSuitabilityAdapter soilSuitabilityAdapter;
    @Mock private LocationResolutionService locationResolutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RegionAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new RegionAnalysisService(regionRepository, analysisRepository, new CropScoringEngine(), fixtureProvider,
                objectMapper, shortForecastAdapter, asosAdapter, soilChemistryAdapter, soilSuitabilityAdapter,
                locationResolutionService);
    }

    @Test
    void live_analysis_persists_screen_contract_from_engine_output_not_a_synthetic_fixture() throws Exception {
        Region region = Region.builder().sidoCode("52").sidoName("전북특별자치도")
                .sigunguCode("52180").sigunguName("고창군").kmaNx(55).kmaNy(80).asosStationId("146").build();
        when(analysisRepository.findByUserEmailAndIdempotencyKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(Optional.empty());
        when(regionRepository.findBySidoCodeAndSigunguCode("52", "52180")).thenReturn(Optional.of(region));
        when(locationResolutionService.resolve(any(), any())).thenReturn(location());
        when(shortForecastAdapter.getForecast3Days(55, 80)).thenReturn(ExternalResult.success(List.of(forecast())));
        when(asosAdapter.get30DaySummary("146")).thenReturn(ExternalResult.success(asos()));
        when(soilChemistryAdapter.getSoilChemistry("52180", "전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(soilChemistry()));
        when(soilSuitabilityAdapter.getSoilSuitability("52180", "전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(Map.of("POTATO", suitability("POTATO", 92.0),
                        "PEAR", suitability("PEAR", 88.0), "LETTUCE", suitability("LETTUCE", 82.0))));
        when(analysisRepository.save(any(RegionAnalysisEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create("owner@example.com", RegionAnalysisRequestDto.builder()
                .sidoCode("52").sidoName("전북특별자치도").sigunguCode("52180").sigunguName("고창군")
                .idempotencyKey("live-contract").forceRefresh(false).build());

        ArgumentCaptor<RegionAnalysisEntity> stored = ArgumentCaptor.forClass(RegionAnalysisEntity.class);
        org.mockito.Mockito.verify(analysisRepository).save(stored.capture());
        RegionReportResponseDto report = objectMapper.readValue(stored.getValue().getPayloadJson(), RegionReportResponseDto.class);

        assertThat(report.getStatus()).isEqualTo("COMPLETED");
        assertThat(report.getDataMode()).isEqualTo("LIVE");
        assertThat(report.getBaseFitness()).isNotNull();
        assertThat(report.getSeasonReadiness()).isNotNull();
        assertThat(report.getComponents()).isNotNull();
        assertThat(report.getEnvironmentFeatures()).isNotEmpty();
        assertThat(report.getRecommendedCrops()).hasSize(3);
        assertThat(report.getCropResults()).isNotEmpty();
        assertThat(report.getTopRisks()).extracting(RegionReportResponseDto.RiskDto::getRiskCode)
                .contains("POTATO_WATERLOGGING");
        assertThat(report.getSources()).allSatisfy(source -> assertThat(source.getStatus()).isEqualTo("SUCCESS"));
    }

    @Test
    void provider_failure_is_persisted_as_partial_with_missing_metric_not_replayed() throws Exception {
        Region region = Region.builder().sidoCode("52").sidoName("전북특별자치도")
                .sigunguCode("52180").sigunguName("고창군").kmaNx(55).kmaNy(80).asosStationId("146").build();
        when(analysisRepository.findByUserEmailAndIdempotencyKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(Optional.empty());
        when(regionRepository.findBySidoCodeAndSigunguCode("52", "52180")).thenReturn(Optional.of(region));
        when(locationResolutionService.resolve(any(), any())).thenReturn(location());
        when(shortForecastAdapter.getForecast3Days(55, 80)).thenReturn(ExternalResult.failure("FORECAST_TIMEOUT"));
        when(asosAdapter.get30DaySummary("146")).thenReturn(ExternalResult.success(asos()));
        when(soilChemistryAdapter.getSoilChemistry("52180", "전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(soilChemistry()));
        when(soilSuitabilityAdapter.getSoilSuitability("52180", "전북특별자치도", "고창군"))
                .thenReturn(ExternalResult.success(Map.of("POTATO", suitability("POTATO", 92.0),
                        "PEAR", suitability("PEAR", 88.0), "LETTUCE", suitability("LETTUCE", 82.0))));
        when(analysisRepository.save(any(RegionAnalysisEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create("owner@example.com", RegionAnalysisRequestDto.builder()
                .sidoCode("52").sidoName("전북특별자치도").sigunguCode("52180").sigunguName("고창군")
                .idempotencyKey("partial-contract").forceRefresh(false).build());

        ArgumentCaptor<RegionAnalysisEntity> stored = ArgumentCaptor.forClass(RegionAnalysisEntity.class);
        verify(analysisRepository).save(stored.capture());
        RegionReportResponseDto report = objectMapper.readValue(stored.getValue().getPayloadJson(), RegionReportResponseDto.class);

        assertThat(report.getStatus()).isEqualTo("PARTIAL");
        assertThat(report.getMissingMetrics()).contains("FORECAST_PROVIDER_FAILURE:FORECAST_TIMEOUT");
        assertThat(report.getSources()).anySatisfy(source -> {
            assertThat(source.getService()).isEqualTo("단기예보");
            assertThat(source.getStatus()).isEqualTo("FAILURE");
            assertThat(source.getFallbackReason()).isEqualTo("FORECAST_TIMEOUT");
        });
        verify(fixtureProvider, never()).getGochangFixture(any(), any(), any(), any());
    }

    @Test
    void official_soil_availability_limits_are_explicit_in_the_partial_report() throws Exception {
        Region region = Region.builder().sidoCode("41").sidoName("경기도")
                .sigunguCode("41110").sigunguName("수원시").kmaNx(60).kmaNy(121).asosStationId("119").build();
        when(analysisRepository.findByUserEmailAndIdempotencyKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(analysisRepository.findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                anyString(), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(Optional.empty());
        when(regionRepository.findBySidoCodeAndSigunguCode("41", "41110")).thenReturn(Optional.of(region));
        when(locationResolutionService.resolve(any(), any())).thenReturn(location());
        when(shortForecastAdapter.getForecast3Days(55, 80)).thenReturn(ExternalResult.success(List.of(forecast())));
        when(asosAdapter.get30DaySummary("146")).thenReturn(ExternalResult.success(asos()));
        when(soilChemistryAdapter.getSoilChemistry("41110", "경기도", "수원시"))
                .thenReturn(ExternalResult.failure("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH"));
        when(soilSuitabilityAdapter.getSoilSuitability("41110", "경기도", "수원시"))
                .thenReturn(ExternalResult.empty());
        when(analysisRepository.save(any(RegionAnalysisEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create("owner@example.com", RegionAnalysisRequestDto.builder()
                .sidoCode("41").sidoName("경기도").sigunguCode("41110").sigunguName("수원시")
                .idempotencyKey("availability-contract").forceRefresh(false).build());

        ArgumentCaptor<RegionAnalysisEntity> stored = ArgumentCaptor.forClass(RegionAnalysisEntity.class);
        verify(analysisRepository).save(stored.capture());
        RegionReportResponseDto report = objectMapper.readValue(stored.getValue().getPayloadJson(), RegionReportResponseDto.class);

        assertThat(report.getStatus()).isEqualTo("PARTIAL");
        assertThat(report.getRecommendedCrops()).isEmpty();
        assertThat(report.getMissingMetrics()).contains(
                "SOIL_CHEMISTRY_UNAVAILABLE:SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH",
                "SOIL_SUITABILITY_NO_RECORDS");
        assertThat(report.getSources()).anySatisfy(source -> {
            assertThat(source.getService()).isEqualTo("농경지화학성 통계");
            assertThat(source.getStatus()).isEqualTo("UNAVAILABLE");
            assertThat(source.getFallbackReason()).isEqualTo("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH");
            assertThat(source.getTransformations()).contains("AREA_DISTRIBUTION_NOT_COERCED_TO_PH");
        });
        assertThat(report.getSources()).anySatisfy(source -> {
            assertThat(source.getService()).isEqualTo("작물별 토양적성");
            assertThat(source.getStatus()).isEqualTo("EMPTY");
            assertThat(source.getFallbackReason()).isEqualTo("NO_RECORDS");
        });
        assertThat(report.getTips()).extracting(RegionReportResponseDto.TipDto::getTipCode)
                .contains("SOIL_STATISTICS_LIMITATION");
    }

    private LocationResolution location() {
        return new LocationResolution("전북특별자치도 고창군", 35.43, 126.70, null, 55, 80, "146", "SIGUNGU",
                "REGION", "C", List.of("REGION_MAPPING:52/52180"), List.of("REGION_MAPPING"), List.of(), null);
    }

    private ShortForecastAdapter.DailyForecast forecast() {
        ShortForecastAdapter.DailyForecast value = new ShortForecastAdapter.DailyForecast();
        value.date = "20260725";
        value.minTemp = 14.0;
        value.maxTemp = 25.0;
        value.pcpTotal = 65.0;
        value.rehAvg = 78.0;
        value.wsdMax = 3.0;
        return value;
    }

    private AsosAdapter.Asos30DaySummary asos() {
        AsosAdapter.Asos30DaySummary value = new AsosAdapter.Asos30DaySummary();
        value.meanTemperature30d = 20.0;
        return value;
    }

    private SoilChemistryAdapter.SoilChemistryResult soilChemistry() {
        SoilChemistryAdapter.SoilChemistryResult value = new SoilChemistryAdapter.SoilChemistryResult();
        value.ph = 5.8;
        return value;
    }

    private SoilSuitabilityAdapter.SoilSuitabilityResult suitability(String cropCode, double score) {
        SoilSuitabilityAdapter.SoilSuitabilityResult value = new SoilSuitabilityAdapter.SoilSuitabilityResult();
        value.cropCode = cropCode;
        value.hasData = true;
        value.score = score;
        return value;
    }
}
