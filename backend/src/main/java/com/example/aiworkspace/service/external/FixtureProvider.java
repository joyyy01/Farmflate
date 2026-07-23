package com.example.aiworkspace.service.external;

import com.example.aiworkspace.dto.region.RegionDto;
import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
public class FixtureProvider {

    private String getKoreanSubject(String name) {
        if (name == null || name.isBlank()) return "이 지역은";
        char lastChar = name.charAt(name.length() - 1);
        if (lastChar >= 0xAC00 && lastChar <= 0xD7A3) {
            boolean hasJongsung = (lastChar - 0xAC00) % 28 != 0;
            return name + (hasJongsung ? "은" : "는");
        }
        return name + "는";
    }

    public RegionReportResponseDto getGochangFixture(String sidoCode, String sigunguCode, String sidoName, String sigunguName) {
        String analysisId = UUID.randomUUID().toString();
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        String safeSido = sidoName != null && !sidoName.isBlank() ? sidoName : "전북특별자치도";
        String safeSigungu = sigunguName != null && !sigunguName.isBlank() ? sigunguName : "고창군";
        String regionKey = safeSido + " " + safeSigungu;

        // Dynamic regional environment score computation (range: 80 ~ 94)
        int dynamicScore = 80 + (Math.abs(regionKey.hashCode()) % 15);
        String gradeStr = dynamicScore >= 88 ? "EXCELLENT" : "GOOD";
        String subjectName = getKoreanSubject(safeSigungu);

        return RegionReportResponseDto.builder()
                .analysisId(analysisId)
                .region(RegionDto.builder()
                        .sidoCode(sidoCode)
                        .sidoName(safeSido)
                        .sigunguCode(sigunguCode)
                        .sigunguName(safeSigungu)
                        .build())
                .regionScore(dynamicScore)
                .grade(gradeStr)
                .summary(subjectName + " 현재 계절에 여러 작물을 재배하기 양호한 환경이지만, 장마철 배수 관리에는 주의가 필요해요.")
                .confidence(RegionReportResponseDto.ConfidenceDto.builder()
                        .grade("HIGH")
                        .score(dynamicScore + 2)
                        .message("최신 기상자료와 지역 토양 통계를 종합 검토했어요.")
                        .build())
                .components(RegionReportResponseDto.ComponentsDto.builder()
                        .climate(RegionReportResponseDto.ComponentDetailDto.builder().score(dynamicScore + 1).grade("GOOD").build())
                        .soil(RegionReportResponseDto.ComponentDetailDto.builder().score(dynamicScore - 2).grade("GOOD").build())
                        .hazard(RegionReportResponseDto.HazardComponentDetailDto.builder().safetyScore(dynamicScore - 4).grade("CAUTION").build())
                        .cultivation(RegionReportResponseDto.ComponentDetailDto.builder().score(dynamicScore + 3).grade("GOOD").build())
                        .build())
                .recommendedCrops(List.of(
                        RegionReportResponseDto.RecommendedCropDto.builder()
                                .cropCode("POTATO")
                                .cropName("감자")
                                .score(Math.min(dynamicScore + 5, 98))
                                .rank(1)
                                .positiveReasons(List.of(
                                        "서늘한 기후 조건과 배수가 우수한 토양 생육 환경에 잘 맞습니다.",
                                        "지역 대표 토양 pH(5.8)가 감자 권장 생육 지표에 부합해요."
                                ))
                                .cautionReason("앞으로 많은 비가 예상되어 배수로 관리가 필요해요.")
                                .build(),
                        RegionReportResponseDto.RecommendedCropDto.builder()
                                .cropCode("LETTUCE")
                                .cropName("상추")
                                .score(dynamicScore + 1)
                                .rank(2)
                                .positiveReasons(List.of(
                                        "토양 유기물 함량과 배수 상태가 우수합니다.",
                                        "현재 기온과 습도가 상추 생장에 적절해요."
                                ))
                                .cautionReason("고온 다습한 날씨에는 환기 및 통풍 관리에 신경 써주세요.")
                                .build(),
                        RegionReportResponseDto.RecommendedCropDto.builder()
                                .cropCode("APPLE")
                                .cropName("사과")
                                .score(dynamicScore - 6)
                                .rank(3)
                                .positiveReasons(List.of(
                                        "풍부한 일조시간과 기후 일교차 조건이 부합해요."
                                ))
                                .cautionReason("여름철 집중호우 대비 배수가 필수적입니다.")
                                .build()
                ))
                .topRisks(List.of(
                        RegionReportResponseDto.RiskDto.builder()
                                .rank(1)
                                .riskCode("HEAVY_RAIN")
                                .level("CAUTION")
                                .title("장마철 집중호우 대비")
                                .description("향후 강수가 특정 시기에 몰릴 수 있어 밭 주변 배수 관리가 중요해요.")
                                .period(RegionReportResponseDto.PeriodDto.builder()
                                        .start("2026-07-24T00:00:00+09:00")
                                        .end("2026-07-25T00:00:00+09:00")
                                        .build())
                                .affectedCrops(List.of("POTATO", "LETTUCE"))
                                .actions(List.of("밭 주변 배수로가 막히지 않았는지 점검하세요."))
                                .source(RegionReportResponseDto.SourceDto.builder()
                                        .provider("농촌진흥청")
                                        .service("농사로 영농기술 가이드")
                                        .sourceUrl("https://www.nongsaro.go.kr")
                                        .dataDate("2026-07-24")
                                        .build())
                                .build()
                ))
                .tips(List.of(
                        RegionReportResponseDto.TipDto.builder()
                                .rank(1)
                                .tipCode("DRAINAGE_BEFORE_RAIN")
                                .title("장마철 배수 관리가 중요해요")
                                .summary("이 지역은 여름철 많은 비가 몰릴 수 있어 밭 주변 배수로 점검이 필요해요.")
                                .sourceType("NONGSARO")
                                .sourceName("농사로 공식자료")
                                .sourceUrl("https://www.nongsaro.go.kr")
                                .actionLabel("농사로 원문 보기")
                                .dataDate("2026-07-24")
                                .build(),
                        RegionReportResponseDto.TipDto.builder()
                                .rank(2)
                                .tipCode("SOIL_TEST_GUIDE")
                                .title("시군구 농업기술센터 토양검정 활용")
                                .summary("무료 토양 검정 서비스를 통해 정확한 pH와 비료 처방전을 받아보세요.")
                                .sourceType("CURATED_OFFICIAL_GUIDE")
                                .sourceName("농촌진흥청 흙토람")
                                .sourceUrl("https://soil.rda.go.kr")
                                .actionLabel("흙토람 홈페이지 바로가기")
                                .dataDate("2026-07-24")
                                .build()
                ))
                .sources(List.of(
                        RegionReportResponseDto.SourceDto.builder()
                                .provider("기상청")
                                .service("단기예보 및 ASOS 시간자료")
                                .sourceUrl("https://www.weather.go.kr")
                                .dataDate("2026-07-24")
                                .build(),
                        RegionReportResponseDto.SourceDto.builder()
                                .provider("농촌진흥청 국립농업과학원")
                                .service("농경지화학성 통계정보 V2")
                                .sourceUrl("https://soil.rda.go.kr")
                                .dataDate("2025")
                                .build()
                ))
                .analyzedAt(nowStr)
                .dataMode("REPLAY")
                .build();
    }
}
