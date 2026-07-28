package com.farmflate.service.analysis;

import com.farmflate.dto.region.RegionReportResponseDto;
import com.farmflate.integration.ExternalResult;
import com.farmflate.integration.MidTermForecastAdapter;
import com.farmflate.integration.NormalizedMetric;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Interprets normalized provider results without performing I/O or changing analysis state. */
final class RegionProviderResultInterpreter {

    void applyQuality(CropScoringEngine.AnalysisInput input, String key, ExternalResult<?> result) {
        input.dataQualityScores.put(key, result.isSuccess() ? 100.0 : result.isEmpty() ? 35.0 : 0.0);
    }

    CropScoringEngine.ForecastDay toForecastDay(MidTermForecastAdapter.DailyForecast source) {
        CropScoringEngine.ForecastDay day = new CropScoringEngine.ForecastDay();
        day.date = source.date;
        day.minTemp = source.minTemp;
        day.maxTemp = source.maxTemp;
        return day;
    }

    void appendProviderState(List<String> missingMetrics, String metric, ExternalResult<?> result) {
        if (result.isFailure()) {
            String state = isAvailabilityLimitation(result) ? "_UNAVAILABLE:" : "_PROVIDER_FAILURE:";
            missingMetrics.add(metric + state + result.errorCode());
        } else if (result.isEmpty()) {
            missingMetrics.add(metric + "_NO_RECORDS");
        }
    }

    String providerFailureSummary(Collection<ExternalResult<?>> results) {
        return results.stream().map(ExternalResult::errorCode).filter(this::hasText).collect(Collectors.joining(", "));
    }

    RegionReportResponseDto.SourceDto source(String provider, String service, String url, ExternalResult<?> result) {
        boolean availabilityLimitation = isAvailabilityLimitation(result);
        String fallback = result.isFailure() ? result.errorCode() : result.isEmpty() ? "NO_RECORDS" : null;
        return RegionReportResponseDto.SourceDto.builder()
                .provider(provider).service(service).sourceUrl(url).dataDate(LocalDate.now().toString())
                .status(availabilityLimitation ? "UNAVAILABLE" : result.status().name())
                .evidenceLevel(result.isSuccess() ? "PROVIDER_NORMALIZED" : "UNAVAILABLE")
                .isFallback(false).fallbackReason(fallback).transformations(providerTransformations(result))
                .build();
    }

    private boolean isAvailabilityLimitation(ExternalResult<?> result) {
        String errorCode = result.errorCode();
        return errorCode != null && (errorCode.endsWith("_UNSUPPORTED_FOR_PH")
                || errorCode.contains("_LOCATION_NOT_RESOLVED")
                || errorCode.contains("_LOCATION_LOOKUP_FAILED"));
    }

    private List<String> providerTransformations(ExternalResult<?> result) {
        List<String> transformations = new ArrayList<>();
        if ("SOIL_CHEMISTRY_UNSUPPORTED_FOR_PH".equals(result.errorCode())) {
            transformations.add("AREA_DISTRIBUTION_NOT_COERCED_TO_PH");
        }
        if (result.errorCode() != null && result.errorCode().contains("_LOCATION_")) {
            transformations.add("LEGAL_DONG_NOT_RESOLVED");
        }
        if (result.errorCode() != null && result.errorCode().contains("CROP_NAME_")) {
            transformations.add("SOIL_FIT_CROP_NAME_VALIDATION_FAILED");
        }
        if (result.isEmpty()) {
            transformations.add("OFFICIAL_NO_RECORDS");
        }
        appendLegalDongSampleCoverage(transformations, result.metrics());
        return List.copyOf(transformations);
    }

    private void appendLegalDongSampleCoverage(List<String> transformations, List<NormalizedMetric> metrics) {
        Map<String, Double[]> coverageByCrop = new LinkedHashMap<>();
        for (NormalizedMetric metric : metrics) {
            if (metric == null || metric.numericValue() == null) {
                continue;
            }
            String cropCode = null;
            int position = switch (metric.metric()) {
                case "soil.eligible_legal_dongs" -> 0;
                case "soil.sampled_legal_dongs" -> 1;
                case "soil.data_backed_legal_dongs" -> 2;
                case "soil.suitability.eligible_legal_dongs" -> {
                    cropCode = metric.textValue();
                    yield 0;
                }
                case "soil.suitability.sampled_legal_dongs" -> {
                    cropCode = metric.textValue();
                    yield 1;
                }
                case "soil.suitability.data_backed_legal_dongs" -> {
                    cropCode = metric.textValue();
                    yield 2;
                }
                default -> -1;
            };
            if (position < 0) {
                continue;
            }
            String key = cropCode == null ? "" : cropCode;
            Double[] counts = coverageByCrop.computeIfAbsent(key, ignored -> new Double[3]);
            counts[position] = metric.numericValue();
        }
        coverageByCrop.forEach((cropCode, counts) -> {
            if (counts[0] == null || counts[1] == null || counts[2] == null) {
                return;
            }
            String cropQualifier = cropCode.isBlank() ? "" : "[" + cropCode + "]";
            transformations.add("LEGAL_DONG_SAMPLE_COVERAGE" + cropQualifier + ":"
                    + countText(counts[2]) + "/" + countText(counts[1]) + "_OF_" + countText(counts[0]));
        });
    }

    private String countText(double value) {
        return Math.rint(value) == value ? Long.toString((long) value) : Double.toString(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
