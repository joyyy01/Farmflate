package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.domain.region.RegionAnalysisEntity;
import com.example.aiworkspace.domain.region.RegionAnalysisRepository;
import com.example.aiworkspace.domain.region.Region;
import com.example.aiworkspace.domain.region.RegionRepository;
import com.example.aiworkspace.dto.region.RegionAnalysisRequestDto;
import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import com.example.aiworkspace.service.external.AsosAdapter;
import com.example.aiworkspace.service.external.FixtureProvider;
import com.example.aiworkspace.service.external.ShortForecastAdapter;
import com.example.aiworkspace.service.external.SoilChemistryAdapter;
import com.example.aiworkspace.service.external.SoilSuitabilityAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionAnalysisServiceIdentityTest {

    private static final String OWNER_A = "owner-a@example.com";
    private static final String OWNER_B = "owner-b@example.com";
    private static final String IDEMPOTENCY_KEY = "same-client-key";

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private RegionAnalysisRepository analysisRepository;

    @Mock
    private CropScoringEngine cropScoringEngine;

    @Mock
    private FixtureProvider fixtureProvider;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ShortForecastAdapter shortForecastAdapter;

    @Mock
    private AsosAdapter asosAdapter;

    @Mock
    private SoilChemistryAdapter soilChemistryAdapter;

    @Mock
    private SoilSuitabilityAdapter soilSuitabilityAdapter;

    @Mock
    private LocationResolutionService locationResolutionService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    private RegionAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new RegionAnalysisService(
                regionRepository,
                analysisRepository,
                cropScoringEngine,
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
    void owner_and_rule_scoped_lookups_do_not_reuse_another_owners_identity_or_cache_entry() {
        RegionAnalysisEntity otherOwnersAnalysis = RegionAnalysisEntity.builder()
                .id("7a2db95b-4098-42bd-899e-8186acbd9f35")
                .userEmail(OWNER_A)
                .sidoCode("52")
                .sidoName("전북특별자치도")
                .sigunguCode("52180")
                .sigunguName("고창군")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .ruleVersion("previous-rule-version")
                .analyzedAt(LocalDateTime.now())
                .build();

        when(analysisRepository.findByUserEmailAndIdempotencyKey(anyString(), eq(IDEMPOTENCY_KEY)))
                .thenAnswer(invocation -> OWNER_A.equals(invocation.getArgument(0))
                        ? Optional.of(otherOwnersAnalysis)
                        : Optional.empty());
        when(analysisRepository
                .findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                        anyString(), eq("52180"), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> OWNER_A.equals(invocation.getArgument(0))
                        && "previous-rule-version".equals(invocation.getArgument(2))
                        ? Optional.of(otherOwnersAnalysis)
                        : Optional.empty());

        assertThatThrownBy(() -> service.create(OWNER_B, request()))
                .isInstanceOfSatisfying(RegionAnalysisService.RegionAnalysisException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("REGION_MAPPING_NOT_CONFIGURED"));

        verify(analysisRepository).findByUserEmailAndIdempotencyKey(OWNER_B, IDEMPOTENCY_KEY);
        verify(analysisRepository)
                .findFirstByUserEmailAndSigunguCodeAndRuleVersionAndAnalyzedAtAfterOrderByAnalyzedAtDesc(
                        eq(OWNER_B),
                        eq("52180"),
                        eq(CropScoringEngine.RULE_VERSION),
                        any(LocalDateTime.class));
    }

    @Test
    void public_idempotency_uses_a_non_personal_scope_and_never_reuses_an_owner_entry() {
        RegionAnalysisEntity publicAnalysis = RegionAnalysisEntity.builder()
                .id("fd426c62-20c4-423d-8797-c6d3bf055ca4")
                .analysisScope("PUBLIC")
                .scopeSubject("PUBLIC_REGION")
                .sidoCode("52").sidoName("전북특별자치도")
                .sigunguCode("52180").sigunguName("고창군")
                .idempotencyKey(IDEMPOTENCY_KEY).ruleVersion(CropScoringEngine.RULE_VERSION)
                .reportStatus("PARTIAL").analyzedAt(LocalDateTime.now()).build();
        when(analysisRepository.findByAnalysisScopeAndScopeSubjectAndIdempotencyKey(
                "PUBLIC", "PUBLIC_REGION", IDEMPOTENCY_KEY)).thenReturn(Optional.of(publicAnalysis));

        var result = service.createPublic(request());

        assertThat(result.getAnalysisId()).isEqualTo(publicAnalysis.getId());
        assertThat(result.getStatus()).isEqualTo("PARTIAL");
        verify(analysisRepository).findByAnalysisScopeAndScopeSubjectAndIdempotencyKey(
                "PUBLIC", "PUBLIC_REGION", IDEMPOTENCY_KEY);
    }

    @Test
    void public_idempotency_persistence_conflict_reuses_the_winning_analysis_instead_of_bubbling_a_server_error()
            throws Exception {
        Region region = Region.builder().sidoCode("42").sidoName("강원특별자치도")
                .sigunguCode("42150").sigunguName("강릉시").kmaNx(92).kmaNy(131).asosStationId("105").build();
        RegionAnalysisEntity winner = RegionAnalysisEntity.builder()
                .id("a89a2ce2-01a3-4f7a-a67e-05c1447b9430")
                .analysisScope("PUBLIC").scopeSubject("PUBLIC_REGION")
                .idempotencyKey(IDEMPOTENCY_KEY).reportStatus("PARTIAL").analyzedAt(LocalDateTime.now()).build();
        RegionReportResponseDto report = RegionReportResponseDto.builder()
                .analysisId(UUID.randomUUID().toString()).status("PARTIAL").build();

        ReflectionTestUtils.setField(service, "dataMode", "REPLAY");
        when(analysisRepository.findByAnalysisScopeAndScopeSubjectAndIdempotencyKey(
                "PUBLIC", "PUBLIC_REGION", IDEMPOTENCY_KEY)).thenReturn(Optional.empty(), Optional.of(winner));
        when(regionRepository.findBySidoCodeAndSigunguCode("42", "42150")).thenReturn(Optional.of(region));
        when(locationResolutionService.resolve(any(), eq(region))).thenReturn(new LocationResolution(
                "강원특별자치도 강릉시", null, null, null, 92, 131, "105", "SIGUNGU", "SIGUNGU", "A",
                java.util.List.of(), java.util.List.of(), java.util.List.of(), null));
        when(fixtureProvider.getGochangFixture("42", "42150", "강원특별자치도", "강릉시")).thenReturn(report);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(analysisRepository.saveAndFlush(any(RegionAnalysisEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate scoped idempotency key"));

        var result = service.createPublic(RegionAnalysisRequestDto.builder()
                .sidoCode("42").sidoName("강원특별자치도").sigunguCode("42150").sigunguName("강릉시")
                .idempotencyKey(IDEMPOTENCY_KEY).forceRefresh(true).build());

        assertThat(result.getAnalysisId()).isEqualTo(winner.getId());
        assertThat(result.getStatus()).isEqualTo("PARTIAL");
        verify(analysisRepository).saveAndFlush(any(RegionAnalysisEntity.class));
        verify(analysisRepository, org.mockito.Mockito.times(2))
                .findByAnalysisScopeAndScopeSubjectAndIdempotencyKey("PUBLIC", "PUBLIC_REGION", IDEMPOTENCY_KEY);
    }

    private RegionAnalysisRequestDto request() {
        return RegionAnalysisRequestDto.builder()
                .sidoCode("52")
                .sidoName("전북특별자치도")
                .sigunguCode("52180")
                .sigunguName("고창군")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .forceRefresh(false)
                .build();
    }
}
