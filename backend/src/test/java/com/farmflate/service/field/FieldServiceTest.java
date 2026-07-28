package com.farmflate.service.field;

import com.farmflate.domain.farm.FarmEntity;
import com.farmflate.domain.farm.FarmRepository;
import com.farmflate.domain.farm.FieldDailyReportEntity;
import com.farmflate.domain.farm.FieldDailyReportRepository;
import com.farmflate.domain.region.RegionAnalysisEntity;
import com.farmflate.domain.region.RegionAnalysisRepository;
import com.farmflate.dto.field.CreateFieldRequestDto;
import com.farmflate.dto.field.FieldAlertDto;
import com.farmflate.dto.field.FieldDailyReportDto;
import com.farmflate.dto.field.FieldDailyStatus;
import com.farmflate.dto.field.FieldProfileResponseDto;
import com.farmflate.dto.field.FieldSuitabilityReportDto;
import com.farmflate.dto.region.RegionDto;
import com.farmflate.dto.region.RegionReportResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FieldServiceTest {

    private static final String OWNER = "owner@example.com";
    private static final String REGION_ANALYSIS_ID = "a0f0b559-117e-4dc5-a1fd-66af73cb8de1";

    @Mock private FarmRepository farmRepository;
    @Mock private FieldDailyReportRepository dailyReportRepository;
    @Mock private RegionAnalysisRepository regionAnalysisRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;

    private FieldService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-24T08:30:00Z"), ZoneId.of("Asia/Seoul"));
        service = new FieldService(farmRepository, dailyReportRepository, regionAnalysisRepository, applicationEventPublisher, objectMapper, clock);
    }

    @Test
    void creates_owned_field_and_queues_daily_report_after_commit() throws Exception {
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
        when(farmRepository.save(any(FarmEntity.class))).thenAnswer(invocation -> {
            FarmEntity entity = invocation.getArgument(0);
            // Real JPA save() assigns the IDENTITY-generated id; simulate that here
            // since withDailyStatus()/getOrCreate() key their lookups off field.getId().
            org.springframework.test.util.ReflectionTestUtils.setField(entity, "id", 11L);
            return entity;
        });
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
        verify(applicationEventPublisher).publishEvent(any(FieldDailyReportRequestedEvent.class));
        verify(regionAnalysisRepository).findByIdAndUserEmail(REGION_ANALYSIS_ID, OWNER);
    }

    @Test
    void lists_saved_field_with_reconstructed_suitability_after_refresh() throws Exception {
        FarmEntity field = FarmEntity.builder()
                .id(11L).userEmail(OWNER).fieldName("감자밭").cropCode("POTATO").cropName("감자")
                .regionAnalysisId(REGION_ANALYSIS_ID).cultivationMethod("OPEN_FIELD")
                .cultivationStartDate(LocalDate.of(2026, 7, 1)).stage("PREPARATION").active(true).build();
        RegionAnalysisEntity analysis = RegionAnalysisEntity.builder().id(REGION_ANALYSIS_ID).userEmail(OWNER)
                .payloadJson(objectMapper.writeValueAsString(regionReport())).build();
        FieldDailyReportDto registration = FieldDailyReportDto.builder().id("registration-1").fieldId("11")
                .reportDate("2026-07-24").generatedAt("2026-07-24T17:30:00")
                .generationReason("REGISTRATION").suitabilityScore(86).summary("감자 적합도 요약")
                .prioritizedActions(List.of("배수로를 점검하세요."))
                .keyRisks(List.of(FieldSuitabilityReportDto.RiskDto.builder().riskCode("POTATO_WATERLOGGING")
                        .severity("ORANGE").title("배수 부담 증가").description("집중 강수로 배수 부담이 높습니다.")
                        .actions(List.of("배수로를 점검하세요.")).build()))
                .conditions(List.of(FieldSuitabilityReportDto.ConditionDto.builder().key("CLIMATE")
                        .label("기후").score(80).status("GOOD").description("지역 분석 기준").build())).build();
        when(farmRepository.findByUserEmailOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(field));
        when(regionAnalysisRepository.findByIdInAndUserEmail(anyList(), eq(OWNER))).thenReturn(List.of(analysis));
        when(dailyReportRepository.findLatestByFarmIdsAndOwnerEmail(anyList(), eq(OWNER)))
                .thenReturn(List.of(FieldDailyReportEntity.builder().farmId(11L).ownerEmail(OWNER)
                        .payloadJson(objectMapper.writeValueAsString(registration)).build()));
        when(dailyReportRepository.findLatestByFarmIdsAndOwnerEmailAndGenerationReason(anyList(), eq(OWNER), eq("DAILY_0630")))
                .thenReturn(List.of());

        FieldProfileResponseDto listed = service.getFields(OWNER).get(0);

        assertThat(listed.getSuitabilityReport()).isNotNull();
        assertThat(listed.getSuitabilityReport().getSuitabilityScore()).isEqualTo(86);
        assertThat(listed.getSuitabilityReport().getGrade()).isEqualTo("GOOD");
        assertThat(listed.getSuitabilityReport().getConditions()).hasSize(4);
        assertThat(listed.getSuitabilityReport().getKeyRisks()).extracting("riskCode").contains("POTATO_WATERLOGGING");
        assertThat(listed.getLatestReport().getPrioritizedActions()).containsExactly("배수로를 점검하세요.");
        JsonNode payload = objectMapper.valueToTree(listed);
        assertThat(payload.at("/suitabilityReport/conditions/0/key").asText()).isEqualTo("CLIMATE");
        assertThat(payload.at("/suitabilityReport/keyRisks/0/actions/0").asText()).isEqualTo("배수로를 점검하세요.");
        assertThat(payload.at("/latestReport/prioritizedActions/0").asText()).isEqualTo("배수로를 점검하세요.");
        verify(dailyReportRepository).findLatestByFarmIdsAndOwnerEmail(anyList(), eq(OWNER));
        verify(dailyReportRepository).findLatestByFarmIdsAndOwnerEmailAndGenerationReason(anyList(), eq(OWNER), eq("DAILY_0630"));
    }

    @Test
    void exposes_danger_status_and_alerts_from_the_latest_daily_report() throws Exception {
        FarmEntity field = FarmEntity.builder()
                .id(11L).userEmail(OWNER).fieldName("감자밭").cropCode("POTATO").cropName("감자")
                .regionAnalysisId(REGION_ANALYSIS_ID).cultivationMethod("OPEN_FIELD")
                .cultivationStartDate(LocalDate.of(2026, 7, 1)).stage("PREPARATION").active(true).build();
        RegionAnalysisEntity analysis = RegionAnalysisEntity.builder().id(REGION_ANALYSIS_ID).userEmail(OWNER)
                .payloadJson(objectMapper.writeValueAsString(regionReport())).build();
        FieldDailyReportDto daily = FieldDailyReportDto.builder()
                .id("daily-danger").fieldId("11").reportDate("2026-07-24").generatedAt("2026-07-24T08:30:00")
                .generationReason("DAILY_0630").status(FieldDailyStatus.CAUTION)
                .headline("오후 고온에 대비하세요.")
                .alerts(List.of(FieldAlertDto.builder().key("AFTERNOON_HEAT").severity("HIGH")
                        .title("오후 고온 주의").description("고온 피해를 줄이기 위한 점검이 필요합니다.").build()))
                .build();
        FieldDailyReportEntity storedDaily = FieldDailyReportEntity.builder().id("daily-danger").farmId(11L)
                .ownerEmail(OWNER).payloadJson(objectMapper.writeValueAsString(daily)).build();
        when(farmRepository.findByUserEmailOrderByCreatedAtDesc(OWNER)).thenReturn(List.of(field));
        when(regionAnalysisRepository.findByIdInAndUserEmail(anyList(), eq(OWNER))).thenReturn(List.of(analysis));
        when(dailyReportRepository.findLatestByFarmIdsAndOwnerEmail(anyList(), eq(OWNER))).thenReturn(List.of(storedDaily));
        when(dailyReportRepository.findLatestByFarmIdsAndOwnerEmailAndGenerationReason(anyList(), eq(OWNER), eq("DAILY_0630")))
                .thenReturn(List.of(storedDaily));

        FieldProfileResponseDto listed = service.getFields(OWNER).get(0);

        assertThat(listed.getDailyStatus()).isEqualTo(FieldDailyStatus.DANGER);
        assertThat(listed.getDailyStatusLabel()).isEqualTo("위험");
        assertThat(listed.getDailyAlerts()).extracting(FieldAlertDto::getTitle).containsExactly("오후 고온 주의");
    }

    @Test
    void rejects_crafted_crop_code_and_name_that_resolve_to_different_region_crops() throws Exception {
        RegionAnalysisEntity analysis = RegionAnalysisEntity.builder().id(REGION_ANALYSIS_ID).userEmail(OWNER)
                .payloadJson(objectMapper.writeValueAsString(regionReport())).build();
        when(regionAnalysisRepository.findByIdAndUserEmail(REGION_ANALYSIS_ID, OWNER)).thenReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.create(OWNER, CreateFieldRequestDto.builder()
                .fieldName("사과밭").cropCode("POTATO").cropName("사과").cultivationMethod("OPEN_FIELD")
                .cultivationStartDate(LocalDate.of(2026, 7, 1)).regionAnalysisId(REGION_ANALYSIS_ID).build()))
                .isInstanceOf(FieldService.FieldException.class)
                .satisfies(exception -> assertThat(((FieldService.FieldException) exception).getHttpStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .extracting("code").isEqualTo("FIELD_CROP_CODE_NAME_MISMATCH");
        verify(farmRepository, never()).save(any(FarmEntity.class));
    }

    @Test
    void rejects_crop_not_eligible_in_linked_owned_region_analysis() throws Exception {
        RegionAnalysisEntity analysis = RegionAnalysisEntity.builder().id(REGION_ANALYSIS_ID).userEmail(OWNER)
                .payloadJson(objectMapper.writeValueAsString(regionReport())).build();
        when(regionAnalysisRepository.findByIdAndUserEmail(REGION_ANALYSIS_ID, OWNER)).thenReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.create(OWNER, CreateFieldRequestDto.builder()
                .fieldName("사과밭").cropCode("APPLE").cropName("사과").cultivationMethod("OPEN_FIELD")
                .cultivationStartDate(LocalDate.of(2026, 7, 1)).regionAnalysisId(REGION_ANALYSIS_ID).build()))
                .isInstanceOf(FieldService.FieldException.class)
                .satisfies(exception -> assertThat(((FieldService.FieldException) exception).getHttpStatus())
                        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
                .extracting("code").isEqualTo("FIELD_CROP_NOT_ELIGIBLE");
        verify(farmRepository, never()).save(any(FarmEntity.class));
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
