package com.farmflate.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmflate.integration.ShortForecastAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionEngineSafetyTest {

    private final CropScoringEngine engine = new CropScoringEngine();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void safetyTable_keepsCriticalHazardsAndLowConfidenceHonest() {
        Stream.of(
                new SafetyCase(
                        "pear blossom frost remains red-capped despite high base fitness",
                        highFitnessPearBlossomFrost(),
                        "PEAR_BLOSSOM_FROST",
                        49,
                        CropScoringEngine.DataConfidence.Level.HIGH,
                        false),
                new SafetyCase(
                        "sparse low-quality inputs expose a decision-limited range",
                        lowConfidenceInput(),
                        null,
                        null,
                        CropScoringEngine.DataConfidence.Level.DECISION_LIMITED,
                        true)
        ).forEach(safetyCase -> {
            CropScoringEngine.DecisionOutput decision = engine.analyze(safetyCase.input()).decisionOutput;

            if (safetyCase.expectedRiskCode() != null) {
                assertThat(decision.baseFitness).as(safetyCase.name()).isGreaterThan(85.0);
                assertThat(decision.riskEvents)
                        .extracting(event -> event.code)
                        .contains(safetyCase.expectedRiskCode());
                assertThat(decision.seasonReadiness).isEqualTo(safetyCase.expectedSeasonReadiness());
            }

            assertThat(decision.dataConfidence.level).isEqualTo(safetyCase.expectedConfidence());
            if (safetyCase.expectsRange()) {
                assertThat(decision.dataConfidence.score).isNull();
                assertThat(decision.dataConfidence.scoreRange).isNotNull();
                assertThat(serialize(decision.dataConfidence))
                        .contains("\"scoreRange\"")
                        .contains("\"score\":null");
            }
        });
    }

    @Test
    void soilPhDeviation_scoresSlightlyOutOfRangeHigherThanFarOutOfRange() {
        CropScoringEngine.AnalysisInput slightlyLow = baseLettuceInput();
        slightlyLow.soilPh = 6.0; // LETTUCE optimal 6.6-7.2; ~0.6 below range (just over 1 caution margin of 0.5)

        CropScoringEngine.AnalysisInput farLow = baseLettuceInput();
        farLow.soilPh = 4.5; // ~2.1 below range: much further out

        double slightlyOutScore = engine.analyze(slightlyLow).decisionOutput.cropResults.stream()
                .filter(crop -> "LETTUCE".equals(crop.cropCode)).findFirst().orElseThrow().soilPhScore;
        double farOutScore = engine.analyze(farLow).decisionOutput.cropResults.stream()
                .filter(crop -> "LETTUCE".equals(crop.cropCode)).findFirst().orElseThrow().soilPhScore;

        assertThat(slightlyOutScore).isGreaterThan(farOutScore);
        assertThat(farOutScore).isLessThan(70.0);
    }

    @Test
    void soilEc_isScoredWithCropSpecificSensitivity() {
        CropScoringEngine.AnalysisInput elevatedEc = baseLettuceInput();
        elevatedEc.soilEc = 2.0; // above LETTUCE's tighter 1.0 threshold, within POTATO's 1.8 threshold

        CropScoringEngine.DecisionOutput decision = engine.analyze(elevatedEc).decisionOutput;
        Double lettuceEcContribution = decision.cropResults.stream()
                .filter(crop -> "LETTUCE".equals(crop.cropCode)).findFirst().orElseThrow()
                .contributions.stream().filter(c -> "soilEc".equals(c.metric)).findFirst()
                .map(c -> c.score).orElse(null);
        Double potatoEcContribution = decision.cropResults.stream()
                .filter(crop -> "POTATO".equals(crop.cropCode)).findFirst().orElseThrow()
                .contributions.stream().filter(c -> "soilEc".equals(c.metric)).findFirst()
                .map(c -> c.score).orElse(null);

        assertThat(lettuceEcContribution).isNotNull();
        assertThat(potatoEcContribution).isNotNull();
        assertThat(potatoEcContribution).isGreaterThan(lettuceEcContribution);
    }

    @Test
    void implausibleSoilReadings_areTreatedAsMissingNotScored() {
        CropScoringEngine.AnalysisInput input = baseLettuceInput();
        input.soilPh = 15.0; // impossible pH reading
        input.soilEc = -3.0; // impossible EC reading

        CropScoringEngine.CropResult lettuce = engine.analyze(input).decisionOutput.cropResults.stream()
                .filter(crop -> "LETTUCE".equals(crop.cropCode)).findFirst().orElseThrow();

        assertThat(lettuce.soilPhScore).isNull();
        assertThat(lettuce.contributions.stream().anyMatch(c -> "soilEc".equals(c.metric))).isFalse();
    }

    private CropScoringEngine.AnalysisInput baseLettuceInput() {
        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = 17.0;
        input.soilSuitabilityScores.put("LETTUCE", 90.0);
        input.soilSuitabilityScores.put("POTATO", 90.0);
        return input;
    }

    private CropScoringEngine.AnalysisInput highFitnessPearBlossomFrost() {
        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.meanTemperature30d = 20.0;
        input.soilPh = 6.0;
        input.soilSuitabilityScores.put("PEAR", 96.0);
        input.selectedCropCode = "PEAR";
        input.userEnteredGrowthStage = "BLOSSOM";
        input.shortForecasts = List.of(forecast("20260725", -1.0, 20.0, 0.0, 55.0, 2.0));
        return input;
    }

    private CropScoringEngine.AnalysisInput lowConfidenceInput() {
        CropScoringEngine.AnalysisInput input = new CropScoringEngine.AnalysisInput();
        input.shortForecasts = List.of(forecast("20260725", 15.0, 22.0, 0.0, 55.0, 2.0));
        input.dataQualityScores.put("forecast", 20.0);
        return input;
    }

    private ShortForecastAdapter.DailyForecast forecast(
            String date, double minTemp, double maxTemp, double precipitation, double humidity, double wind) {
        ShortForecastAdapter.DailyForecast forecast = new ShortForecastAdapter.DailyForecast();
        forecast.date = date;
        forecast.minTemp = minTemp;
        forecast.maxTemp = maxTemp;
        forecast.pcpTotal = precipitation;
        forecast.rehAvg = humidity;
        forecast.wsdMax = wind;
        return forecast;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new AssertionError("confidence must be serializable", exception);
        }
    }

    private record SafetyCase(
            String name,
            CropScoringEngine.AnalysisInput input,
            String expectedRiskCode,
            Integer expectedSeasonReadiness,
            CropScoringEngine.DataConfidence.Level expectedConfidence,
            boolean expectsRange) {
    }
}
