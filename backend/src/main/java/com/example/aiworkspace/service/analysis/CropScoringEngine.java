package com.example.aiworkspace.service.analysis;

import com.example.aiworkspace.dto.region.RegionReportResponseDto;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CropScoringEngine {

    public static class CropProfile {
        public String cropCode;
        public String cropName;
        // Climate
        public double tempOptimalMin, tempOptimalMax, tempCriticalMin, tempCriticalMax;
        public double precipOptimalMin, precipOptimalMax, precipCriticalMin, precipCriticalMax;
        public double humidOptimalMin, humidOptimalMax, humidCriticalMin, humidCriticalMax;
        // Soil
        public double phOptimalMin, phOptimalMax, phCriticalMin, phCriticalMax;

        public CropProfile(String cropCode, String cropName,
                           double tOptMin, double tOptMax, double tCritMin, double tCritMax,
                           double pOptMin, double pOptMax, double pCritMin, double pCritMax,
                           double hOptMin, double hOptMax, double hCritMin, double hCritMax,
                           double phOptMin, double phOptMax, double phCritMin, double phCritMax) {
            this.cropCode = cropCode;
            this.cropName = cropName;
            this.tempOptimalMin = tOptMin; this.tempOptimalMax = tOptMax;
            this.tempCriticalMin = tCritMin; this.tempCriticalMax = tCritMax;
            this.precipOptimalMin = pOptMin; this.precipOptimalMax = pOptMax;
            this.precipCriticalMin = pCritMin; this.precipCriticalMax = pCritMax;
            this.humidOptimalMin = hOptMin; this.humidOptimalMax = hOptMax;
            this.humidCriticalMin = hCritMin; this.humidCriticalMax = hCritMax;
            this.phOptimalMin = phOptMin; this.phOptimalMax = phOptMax;
            this.phCriticalMin = phCritMin; this.phCriticalMax = phCritMax;
        }
    }

    private static final List<CropProfile> PROFILES = List.of(
            new CropProfile("APPLE", "사과", 18, 26, 10, 34, 40, 150, 0, 300, 60, 75, 30, 90, 5.5, 6.5, 4.5, 7.5),
            new CropProfile("PEAR", "배", 18, 27, 11, 35, 40, 160, 0, 320, 60, 75, 30, 90, 5.5, 6.5, 4.5, 7.5),
            new CropProfile("CUCUMBER", "오이", 20, 28, 12, 36, 50, 180, 0, 350, 65, 85, 40, 95, 6.0, 6.8, 5.0, 7.8),
            new CropProfile("POTATO", "감자", 14, 23, 5, 30, 20, 100, 0, 250, 55, 75, 30, 95, 5.0, 6.0, 4.0, 7.5),
            new CropProfile("LETTUCE", "상추", 15, 22, 8, 28, 30, 120, 0, 280, 60, 80, 35, 95, 6.0, 7.0, 4.5, 8.0)
    );

    public Double calculateScore(Double val, double optMin, double optMax, double critMin, double critMax) {
        if (val == null) return null;
        if (val < critMin || val > critMax) return 0.0;
        if (val >= optMin && val <= optMax) return 100.0;
        if (val < optMin) {
            return 100.0 * (val - critMin) / (optMin - critMin);
        } else {
            return 100.0 * (critMax - val) / (critMax - optMax);
        }
    }

    public static class EvaluationData {
        public Double avgTemp;
        public Double maxTemp;
        public Double minTemp;
        public Double precip30d;
        public Double humidity;
        public Double maxWind;
        public Double soilPh;
        public Double soilEc;
        public Map<String, Double> cropSoilSuitability = new HashMap<>(); // cropCode -> suitability score 0~100
    }

    public static class EvaluatedCrop {
        public String cropCode;
        public String cropName;
        public double climateScore;
        public double soilScore;
        public double hazardSafetyScore;
        public double cultivationScore;
        public double totalScore;
        public List<String> positiveReasons = new ArrayList<>();
        public String cautionReason;
    }

    public List<EvaluatedCrop> evaluateCrops(EvaluationData data, int hazardSafetyScore) {
        List<EvaluatedCrop> list = new ArrayList<>();

        for (CropProfile p : PROFILES) {
            EvaluatedCrop c = new EvaluatedCrop();
            c.cropCode = p.cropCode;
            c.cropName = p.cropName;
            c.hazardSafetyScore = hazardSafetyScore;

            // Climate component
            Double sTemp = calculateScore(data.avgTemp, p.tempOptimalMin, p.tempOptimalMax, p.tempCriticalMin, p.tempCriticalMax);
            Double sPrecip = calculateScore(data.precip30d, p.precipOptimalMin, p.precipOptimalMax, p.precipCriticalMin, p.precipCriticalMax);
            Double sHumid = calculateScore(data.humidity, p.humidOptimalMin, p.humidOptimalMax, p.humidCriticalMin, p.humidCriticalMax);

            double climateSum = 0.0;
            double climateWeightSum = 0.0;
            if (sTemp != null) { climateSum += sTemp * 0.55; climateWeightSum += 0.55; }
            if (sPrecip != null) { climateSum += sPrecip * 0.25; climateWeightSum += 0.25; }
            if (sHumid != null) { climateSum += sHumid * 0.20; climateWeightSum += 0.20; }
            c.climateScore = climateWeightSum > 0 ? climateSum / climateWeightSum : 75.0;

            // Soil component
            Double sPh = calculateScore(data.soilPh, p.phOptimalMin, p.phOptimalMax, p.phCriticalMin, p.phCriticalMax);
            Double sSuit = data.cropSoilSuitability.getOrDefault(p.cropCode, 85.0);

            double soilSum = 0.0;
            double soilWeightSum = 0.0;
            if (sPh != null) { soilSum += sPh * 0.45; soilWeightSum += 0.45; }
            if (sSuit != null) { soilSum += sSuit * 0.55; soilWeightSum += 0.55; }
            c.soilScore = soilWeightSum > 0 ? soilSum / soilWeightSum : 80.0;
            c.cultivationScore = sSuit != null ? sSuit : 80.0;

            // Total Score: 0.40 climate + 0.35 soil + 0.15 hazard + 0.10 cultivation
            c.totalScore = 0.40 * c.climateScore + 0.35 * c.soilScore + 0.15 * c.hazardSafetyScore + 0.10 * c.cultivationScore;

            // Reasons
            if (c.climateScore >= 80) c.positiveReasons.add("최근 기온과 기후 조건이 " + p.cropName + " 재배 기준에 적합해요.");
            if (c.soilScore >= 80) c.positiveReasons.add("지역 대표 토양 산성도가 권장 범위에 가까워요.");
            if (c.positiveReasons.isEmpty()) c.positiveReasons.add("지역 토양 적성 등급이 양호한 편이에요.");

            if (data.precip30d != null && data.precip30d > 150) {
                c.cautionReason = "여름철 많은 비로 인해 배수 관리에 유의하세요.";
            } else if (c.soilScore < 70) {
                c.cautionReason = "토양 산도 조절 및 처방 검토가 필요해요.";
            } else {
                c.cautionReason = "기상 변화에 맞춘 세심한 환경 확인이 권장돼요.";
            }

            list.add(c);
        }

        // Sort descending by totalScore, then climateScore, then soilScore, then fixed profile order
        list.sort((a, b) -> {
            int cmp = Double.compare(b.totalScore, a.totalScore);
            if (cmp != 0) return cmp;
            cmp = Double.compare(b.climateScore, a.climateScore);
            if (cmp != 0) return cmp;
            return Double.compare(b.soilScore, a.soilScore);
        });

        return list;
    }

    public Map<String, Object> calculateHazardRisks(EvaluationData data) {
        List<RegionReportResponseDto.RiskDto> risks = new ArrayList<>();
        int penalty = 0;

        if (data.maxTemp != null && data.maxTemp >= 33.0) {
            boolean isDanger = data.maxTemp >= 35.0;
            penalty += isDanger ? 20 : 10;
            risks.add(RegionReportResponseDto.RiskDto.builder()
                    .rank(risks.size() + 1)
                    .riskCode("HEAT")
                    .level(isDanger ? "DANGER" : "CAUTION")
                    .title("여름철 폭염 경보")
                    .description("최고기온이 " + data.maxTemp + "°C에 달해 작물 차광 및 수분 관리가 필요해요.")
                    .actions(List.of("차광막을 설치하고 수분을 충분히 공급하세요."))
                    .affectedCrops(List.of("LETTUCE", "CUCUMBER"))
                    .source(RegionReportResponseDto.SourceDto.builder()
                            .provider("기상청")
                            .service("단기예보 조회서비스")
                            .sourceUrl("https://www.data.go.kr/")
                            .dataDate("2026-07-24")
                            .build())
                    .build());
        }

        if (data.minTemp != null && data.minTemp <= 5.0) {
            boolean isDanger = data.minTemp <= 0.0;
            penalty += isDanger ? 20 : 10;
            risks.add(RegionReportResponseDto.RiskDto.builder()
                    .rank(risks.size() + 1)
                    .riskCode("COLD")
                    .level(isDanger ? "DANGER" : "CAUTION")
                    .title("저온 및 냉해 주의")
                    .description("일 최저기온이 " + data.minTemp + "°C로 떨어져 저온 피해 주의가 필요해요.")
                    .actions(List.of("보온 덮개 및 보온재 점검을 진행하세요."))
                    .affectedCrops(List.of("POTATO", "CUCUMBER"))
                    .source(RegionReportResponseDto.SourceDto.builder()
                            .provider("기상청")
                            .service("단기예보 조회서비스")
                            .sourceUrl("https://www.data.go.kr/")
                            .dataDate("2026-07-24")
                            .build())
                    .build());
        }

        // Heavy Rain Risk
        if (data.precip30d != null && data.precip30d >= 100.0) {
            boolean isDanger = data.precip30d >= 200.0;
            penalty += isDanger ? 20 : 10;
            risks.add(RegionReportResponseDto.RiskDto.builder()
                    .rank(risks.size() + 1)
                    .riskCode("HEAVY_RAIN")
                    .level(isDanger ? "DANGER" : "CAUTION")
                    .title("장마철 집중호우 주의")
                    .description("많은 비가 집중될 수 있어 배수로 관리가 필수적입니다.")
                    .actions(List.of("밭 주변 배수로가 막히지 않았는지 확인하세요."))
                    .affectedCrops(List.of("POTATO", "LETTUCE"))
                    .source(RegionReportResponseDto.SourceDto.builder()
                            .provider("기상청")
                            .service("단기예보 조회서비스")
                            .sourceUrl("https://www.data.go.kr/")
                            .dataDate("2026-07-24")
                            .build())
                    .build());
        }

        int totalPenalty = Math.min(40, penalty);
        int safetyScore = Math.max(0, 100 - totalPenalty);

        Map<String, Object> res = new HashMap<>();
        res.put("safetyScore", safetyScore);
        res.put("risks", risks.stream().limit(3).collect(Collectors.toList()));
        return res;
    }
}
