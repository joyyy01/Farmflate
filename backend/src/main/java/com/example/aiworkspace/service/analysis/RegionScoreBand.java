package com.example.aiworkspace.service.analysis;

/**
 * The single farmer-facing scale for region and field suitability scores.
 * Keep the grade and the explanatory sentence together so the two cannot
 * silently drift into different score bands.
 */
public final class RegionScoreBand {

    public record Classification(String grade, String summary) {
    }

    private RegionScoreBand() {
    }

    public static Classification classify(int score) {
        if (score >= 80) {
            return new Classification("GOOD", "현재 조건에서 재배를 시작하기 좋은 환경입니다.");
        }
        if (score >= 60) {
            return new Classification("MODERATE", "전반적으로 재배가 가능하지만 일부 환경 관리가 필요합니다.");
        }
        if (score >= 40) {
            return new Classification("CAUTION", "재배 전 위험요인을 확인하고 보완 계획을 세워야 합니다.");
        }
        return new Classification("POOR", "현재 조건에서는 재배 부담이 크므로 추가 확인이 필요합니다.");
    }

    public static String gradeFor(Integer score) {
        return score == null ? "UNAVAILABLE" : classify(score).grade();
    }
}
