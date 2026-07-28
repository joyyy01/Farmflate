package com.farmflate.service.analysis;

import com.farmflate.integration.ExternalResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class RegionProviderResultInterpreterTest {

    private final RegionProviderResultInterpreter interpreter = new RegionProviderResultInterpreter();

    @Test
    void preserves_provider_availability_semantics_in_missing_metrics_and_sources() {
        ExternalResult<Object> result = ExternalResult.failure("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH");
        ArrayList<String> missingMetrics = new ArrayList<>();

        interpreter.appendProviderState(missingMetrics, "SOIL_CHEMISTRY", result);
        var source = interpreter.source("농촌진흥청", "농경지화학성 상세조사", "https://soil.rda.go.kr", result);

        assertThat(missingMetrics).containsExactly("SOIL_CHEMISTRY_UNAVAILABLE:SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH");
        assertThat(source.getStatus()).isEqualTo("UNAVAILABLE");
        assertThat(source.getFallbackReason()).isEqualTo("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH");
        assertThat(source.getTransformations()).contains("AREA_DISTRIBUTION_NOT_COERCED_TO_PH");
    }
}
