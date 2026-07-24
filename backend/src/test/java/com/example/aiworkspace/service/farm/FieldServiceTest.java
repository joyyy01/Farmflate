package com.example.aiworkspace.service.farm;

import com.example.aiworkspace.domain.farm.FarmEntity;
import com.example.aiworkspace.domain.farm.FarmRepository;
import com.example.aiworkspace.domain.farm.FieldDailyReportEntity;
import com.example.aiworkspace.domain.farm.FieldDailyReportRepository;
import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.dto.field.CreateFieldRequestDto;
import com.example.aiworkspace.dto.field.FieldProfileResponseDto;
import com.example.aiworkspace.dto.region.RegionDto;
import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldServiceTest {

    private static final String OWNER = "owner@example.com";
    private static final String REGION_ANALYSIS_ID = "a0f0b559-117e-4dc5-a1fd-66af73cb8de1";

    @Mock private FarmRepository farmRepository;
    @Mock private FieldDailyReportRepository dailyReportRepository;
    @Mock private RegionAnalysisRepository regionAnalysisRepository;

    private FieldService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-24T08:30:00Z"), ZoneId.of("Asia/Seoul"));
        service = new FieldService(farmRepository, dailyReportRepository, regionAnalysisRepository, objectMapper, clock);
    }

    @Test
    void creates_owned_field_with_stored_deterministic_suitability_and_idempotent_daily_snapshot() throws Exception {
        RegionAnalysisEntity analysis = RegionAnalysisEntity.builder()
                .id(REGION_ANALYSIS_ID)
                .userEmail(OWNER)
                .sidoCode("52")
                .sidoName("전북특별자치도")
                .sigunguCode("52180")
                .sigunguName("고창군")
                .payloadJson(objectMapper.writeValueAsString(regionReport()))
                .build();
        when(regionAnalysisRepository.findByIdAndUserEmail(REGION_ANALYSIS_ID, OWNER)).thenReturn(Optional.of(analysis));
        when(farmRepository.save(any(FarmEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dailyReportRepository.save(any(FieldDailyReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FieldProfileResponseDto created = service.create(OWNER, CreateFieldRequestDto.builder()
                .fieldName("감자밭")
                .cropCode("POTATO")
                .cropName("감자")
                .cultivationMethod("OPEN_FIELD")
                .cultivationStartDate(LocalDate.of(2026, 7, 1))
                .stage("PREPARATION")
                .regionAnalysisId(REGION_ANALYSIS_ID)
                .build());

        assertThat(created.getSuitabilityReport().getSuitabilityScore()).isEqualTo(86);
        assertThat(created.getSuitabilityReport().getConditions()).hasSize(4);
        assertThat(created.getSuitabilityReport().getKeyRisks()).extracting("riskCode").contains("POTATO_WATERLOGGING");
        assertThat(created.getLatestReport().getGenerationReason()).isEqualTo("REGISTRATION");
        verify(regionAnalysisRepository).findByIdAndUserEmail(REGION_ANALYSIS_ID, OWNER);

        FarmEntity activeField = FarmEntity.builder()
                .id(11L)
                .userEmail(OWNER)
                .fieldName("감자밭")
                .cropCode("POTATO")
                .cropName("감자")
                .regionAnalysisId(REGION_ANALYSIS_ID)
                .cultivationMethod("OPEN_FIELD")
                .cultivationStartDate(LocalDate.of(2026, 7, 1))
                .stage("PREPARATION")
                .active(true)
                .build();
        when(farmRepository.findByActiveTrue()).thenReturn(List.of(activeField));
        when(dailyReportRepository.existsByFarmIdAndReportDateAndGenerationReason(11L, LocalDate.of(2026, 7, 24), "DAILY_0600"))
                .thenReturn(false, true);

        service.generateDailyForActiveFields(LocalDate.of(2026, 7, 24));
        service.generateDailyForActiveFields(LocalDate.of(2026, 7, 24));

        ArgumentCaptor<FieldDailyReportEntity> daily = ArgumentCaptor.forClass(FieldDailyReportEntity.class);
        verify(dailyReportRepository, times(2)).save(daily.capture());
        FieldDailyReportEntity generated = daily.getAllValues().get(1);
        assertThat(generated.getGenerationReason()).isEqualTo("DAILY_0600");
        assertThat(generated.getGeneratedAt()).isEqualTo(LocalDateTime.of(2026, 7, 24, 6, 0));
    }

    private RegionReportResponseDto regionReport() {
        return RegionReportResponseDto.builder()
                .analysisId(REGION_ANALYSIS_ID)
                .status("COMPLETED")
                .region(RegionDto.builder().sidoCode("52").sidoName("전북특별자치도")
                        .sigunguCode("52180").sigunguName("고창군").build())
                .analyzedAt("2026-07-24T06:00:00+09:00")
                .recommendedCrops(List.of(RegionReportResponseDto.RecommendedCropDto.builder()
                        .cropCode("POTATO").cropName("감자").score(86).rank(1)
                        .positiveReasons(List.of("지역 토양 적성 통계가 양호합니다.")).build()))
                .cropResults(List.of(RegionReportResponseDto.CropDecisionDto.builder()
                        .cropCode("POTATO").cropName("감자").score(86).baseFitness(86.0).seasonReadiness(63)
                        .soilSuitabilityScore(90).soilPhScore(85).seasonalTemperatureScore(80).build()))
                .topRisks(List.of(RegionReportResponseDto.RiskDto.builder()
                        .riskCode("POTATO_WATERLOGGING").severity("ORANGE").title("배수 부담 증가")
                        .description("집중 강수로 배수 부담이 높습니다.")
                        .affectedCrops(List.of("POTATO")).actions(List.of("배수로를 점검하세요.")).build()))
                .components(RegionReportResponseDto.ComponentsDto.builder()
                        .climate(RegionReportResponseDto.ComponentDetailDto.builder().score(80).grade("GOOD").build())
                        .soil(RegionReportResponseDto.ComponentDetailDto.builder().score(88).grade("GOOD").build())
                        .hazard(RegionReportResponseDto.HazardComponentDetailDto.builder().safetyScore(63).grade("CAUTION").build())
                        .cultivation(RegionReportResponseDto.ComponentDetailDto.builder().score(86).grade("GOOD").build())
                        .build())
                .build();
    }
}
