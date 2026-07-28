package com.farmflate.service.analysis.rules;

import com.farmflate.service.analysis.CropScoringEngine;
import com.farmflate.service.analysis.CropScoringEngine.CropProfile;

import java.util.List;

/** Immutable, versioned coefficients used by the crop decision engine. */
public final class CropRuleCatalog {

    private static final List<String> TIEBREAK_ORDER =
            List.of("APPLE", "PEAR", "CUCUMBER", "POTATO", "LETTUCE");

    private static final List<CropProfile> PROFILES = List.of(
            new CropProfile("APPLE", "사과", 18.0, 24.0, 5.0, null,
                    5.8, 6.3, 0.5, 1.5, 0.5, 0.45, 0.20, 0.25, 0.10, 0.10, true),
            new CropProfile("PEAR", "배", null, null, 5.0, 20.0,
                    5.5, 6.5, 0.5, 1.5, 0.5, 0.45, 0.20, 0.25, 0.10, 0.10, true),
            new CropProfile("CUCUMBER", "오이", 20.0, 25.0, 5.0, null,
                    5.5, 6.8, 0.5, 1.2, 0.4, 0.35, 0.15, 0.30, 0.20, 0.10, true),
            new CropProfile("POTATO", "감자", 15.0, 21.0, 5.0, null,
                    5.0, 6.0, 0.5, 1.8, 0.5, 0.50, 0.15, 0.20, 0.15, 0.10, true),
            new CropProfile("LETTUCE", "상추", 15.0, 20.0, 5.0, null,
                    6.6, 7.2, 0.5, 1.0, 0.3, 0.30, 0.20, 0.25, 0.25, 0.10, true)
    );

    public List<CropProfile> profiles() {
        return PROFILES;
    }

    public List<String> supportedCropCodes() {
        return PROFILES.stream().map(profile -> profile.cropCode).toList();
    }

    public int tieBreakIndex(String cropCode) {
        return TIEBREAK_ORDER.indexOf(cropCode);
    }
}
