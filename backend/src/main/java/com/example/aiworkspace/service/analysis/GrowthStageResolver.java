package com.example.aiworkspace.service.analysis;

import java.util.Locale;

/**
 * Resolves the provenance of a crop growth stage without inferring one from
 * unverified growing-degree-day values.  A caller can provide a stage observed
 * by a user today; a provider-derived stage is accepted only when its source
 * explicitly marks it verified.
 */
public class GrowthStageResolver {

    public enum Source {
        USER_INPUT,
        VERIFIED_GDD,
        UNRESOLVED
    }

    public static class GddEvidence {
        public Double cumulativeGdd;
        public String stage;
        public String sourceRef;
        public boolean verified;
    }

    public static class ResolvedGrowthStage {
        public String cropCode;
        public String stage;
        public Source source;
        public String sourceRef;
        public Double cumulativeGdd;
    }

    public ResolvedGrowthStage resolve(String cropCode, String userEnteredStage) {
        return resolve(cropCode, userEnteredStage, null);
    }

    public ResolvedGrowthStage resolve(String cropCode, String userEnteredStage, GddEvidence gddEvidence) {
        ResolvedGrowthStage resolved = new ResolvedGrowthStage();
        resolved.cropCode = normalize(cropCode);

        // Numeric GDD alone is intentionally insufficient: this component does
        // not contain crop threshold tables and therefore must not invent a stage.
        if (gddEvidence != null && gddEvidence.verified && hasText(gddEvidence.stage)) {
            resolved.stage = normalize(gddEvidence.stage);
            resolved.source = Source.VERIFIED_GDD;
            resolved.sourceRef = gddEvidence.sourceRef;
            resolved.cumulativeGdd = gddEvidence.cumulativeGdd;
            return resolved;
        }

        if (hasText(userEnteredStage)) {
            resolved.stage = normalize(userEnteredStage);
            resolved.source = Source.USER_INPUT;
            return resolved;
        }

        resolved.stage = "UNSPECIFIED";
        resolved.source = Source.UNRESOLVED;
        return resolved;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
