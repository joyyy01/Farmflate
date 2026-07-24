package com.example.aiworkspace.service.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Package-private mechanics shared by provider adapters. */
final class ExternalAdapterSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExternalAdapterSupport() {
    }

    static ExternalResult<Map<String, Object>> parseJsonObject(String body, String contentType) {
        if (body == null || body.isBlank()) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String trimmed = body.trim();
        if (normalizedContentType.contains("html") || trimmed.startsWith("<")) {
            return ExternalResult.failure("UNEXPECTED_HTML_RESPONSE");
        }
        if (!normalizedContentType.isBlank() && !normalizedContentType.contains("json")) {
            return ExternalResult.failure("UNEXPECTED_CONTENT_TYPE");
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(trimmed, new TypeReference<>() { });
            return parsed == null ? ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE")
                    : ExternalResult.success(parsed);
        } catch (Exception exception) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : null;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mapList(Object value) {
        if (value instanceof Map<?, ?> single) {
            return List.of((Map<String, Object>) single);
        }
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = map(item);
            if (map != null) {
                result.add(map);
            }
        }
        return result;
    }

    static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    static NormalizedMetric metric(
            String metric,
            Double numericValue,
            String textValue,
            String unit,
            String provider,
            String service,
            String spatialLevel,
            String regionCode,
            String dataDate,
            boolean fallback,
            boolean replay,
            String quality,
            List<String> validationFlags) {
        return new NormalizedMetric(metric, numericValue, textValue, unit, provider, service, spatialLevel,
                regionCode, dataDate, Instant.now(), false, fallback, replay, quality, validationFlags,
                fallback ? "C" : "B", null, null, List.of(), null, null,
                fallback ? "PROVIDER_FALLBACK" : null);
    }

    /**
     * Executes one provider request plus the configured number of retries.
     * Only transient transport failures and HTTP 5xx responses are retried;
     * client errors and malformed payloads are terminal at their own boundary.
     */
    static <T> ExternalResult<T> executeRequest(
            int retryCount,
            String failureCode,
            Supplier<T> request) {
        int retries = Math.max(0, retryCount);
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                return ExternalResult.success(request.get());
            } catch (HttpServerErrorException | ResourceAccessException retryableException) {
                if (attempt == retries) {
                    return ExternalResult.failure(failureCode);
                }
            } catch (RuntimeException terminalException) {
                return ExternalResult.failure(failureCode);
            }
        }
        return ExternalResult.failure(failureCode);
    }

    static <T> ExternalResult<T> executeOnce(
            ConcurrentHashMap<String, CompletableFuture<ExternalResult<T>>> inFlight,
            String key,
            Supplier<ExternalResult<T>> supplier) {
        CompletableFuture<ExternalResult<T>> created = new CompletableFuture<>();
        CompletableFuture<ExternalResult<T>> existing = inFlight.putIfAbsent(key, created);
        if (existing == null) {
            try {
                ExternalResult<T> result = supplier.get();
                created.complete(result == null ? ExternalResult.failure("NULL_ADAPTER_RESULT") : result);
                return created.join();
            } catch (RuntimeException exception) {
                created.complete(ExternalResult.failure("ADAPTER_REQUEST_FAILED"));
                return created.join();
            } finally {
                inFlight.remove(key, created);
            }
        }
        try {
            return existing.join();
        } catch (CompletionException exception) {
            return ExternalResult.failure("DEDUPLICATED_REQUEST_FAILED");
        }
    }
}
