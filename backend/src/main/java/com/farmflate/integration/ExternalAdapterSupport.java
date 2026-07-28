package com.farmflate.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Package-private mechanics shared by provider adapters. */
final class ExternalAdapterSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ConcurrentHashMap<String, ProviderPacer> PROVIDER_PACERS = new ConcurrentHashMap<>();

    private ExternalAdapterSupport() {
    }

    /** Parses the actual payload format returned by a public provider without guessing from the endpoint name. */
    static ExternalResult<Map<String, Object>> parseProviderObject(String body, String contentType) {
        if (body == null || body.isBlank()) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        return body.trim().startsWith("<") ? parseXmlObject(body, contentType) : parseJsonObject(body, contentType);
    }

    static ExternalResult<Map<String, Object>> parseJsonObject(String body, String contentType) {
        if (body == null || body.isBlank()) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("<")) {
            return ExternalResult.failure("UNEXPECTED_HTML_RESPONSE");
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(trimmed, new TypeReference<>() { });
            return parsed == null ? ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE")
                    : ExternalResult.success(parsed);
        } catch (Exception exception) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
    }

    static ExternalResult<Map<String, Object>> parseXmlObject(String body, String contentType) {
        if (body == null || body.isBlank()) {
            return ExternalResult.failure("EMPTY_PROVIDER_RESPONSE");
        }
        String trimmed = body.trim();
        if (trimmed.regionMatches(true, 0, "<html", 0, 5)) {
            return ExternalResult.failure("UNEXPECTED_HTML_RESPONSE");
        }
        if (!trimmed.startsWith("<")) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getDocumentElement();
            if (root == null || "html".equalsIgnoreCase(root.getTagName())) {
                return ExternalResult.failure("UNEXPECTED_HTML_RESPONSE");
            }
            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put(root.getTagName(), elementValue(root));
            return ExternalResult.success(parsed);
        } catch (Exception exception) {
            return ExternalResult.failure("MALFORMED_PROVIDER_RESPONSE");
        }
    }

    static String providerResultCode(Map<String, Object> response) {
        Map<String, Object> envelope = map(response == null ? null : response.get("response"));
        Map<String, Object> header = map(envelope == null ? null : envelope.get("header"));
        if (header == null) {
            header = map(response == null ? null : response.get("header"));
        }
        return string(header, "resultCode", "result_Code", "result_code", "ResultCode", "Result_Code", "RESULT_CODE", "code");
    }

    static boolean isProviderSuccessCode(String code) {
        return "00".equals(code) || "200".equals(code);
    }

    static boolean isProviderNoDataCode(String code) {
        // Public-data providers use both their legacy 301 code and the
        // standard OpenAPI 03 code for an otherwise valid no-data response.
        // Treating the latter as a provider failure prevents adapters from
        // trying their documented fallback publication or reporting a clean
        // missing-data state to the decision engine.
        return "301".equals(code) || "03".equals(code);
    }

    /**
     * True when a provider's own parsed envelope reports a transient
     * condition -- data.go.kr's common result codes "05" (SERVICETIMEOUT_ERROR)
     * and "22" (LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR) -- rather
     * than a structural, authorization, or parameter problem.  These arrive as
     * ordinary HTTP 200 responses with an error body, so {@link #executeRequest}
     * never sees them as a retryable transport failure: only the caller, after
     * inspecting the parsed envelope's result code, can retry them. Terminal
     * codes (bad service key, invalid parameters, etc.) intentionally do not
     * match so a retry is never attempted where it cannot help.
     */
    static boolean isProviderTransientFailureCode(String errorCode) {
        return errorCode != null && (errorCode.endsWith("_05") || errorCode.endsWith("_22"));
    }

    /** Bounded linear backoff between provider-transient-failure retries. */
    static void backoffSleep(int attempt) {
        try {
            Thread.sleep(Math.min(2000L, 300L * (attempt + 1)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : null;
    }

    private static Object elementValue(Element element) {
        Map<String, List<Object>> childValues = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                childValues.computeIfAbsent(childElement.getTagName(), ignored -> new ArrayList<>())
                        .add(elementValue(childElement));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getTextContent());
            }
        }
        if (childValues.isEmpty()) {
            return text.toString().trim();
        }
        Map<String, Object> value = new LinkedHashMap<>();
        childValues.forEach((name, values) -> value.put(name, values.size() == 1 ? values.get(0) : values));
        return value;
    }

    private static String string(Map<String, Object> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            Object value = values.get(key);
            if (value != null) {
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
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

    /**
     * Serializes requests for a provider scope and backs off after HTTP 429.
     * This deliberately treats rate limiting as retryable transport behavior,
     * never as an authoritative empty response.
     */
    static <T> ExternalResult<T> executePacedRequest(
            String providerScope, int minIntervalMs, int retryCount, String failureCode, Supplier<T> request) {
        String scope = providerScope == null || providerScope.isBlank() ? "default" : providerScope;
        ProviderPacer pacer = PROVIDER_PACERS.computeIfAbsent(scope, ignored -> new ProviderPacer());
        int retries = Math.max(0, retryCount);
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (!pacer.awaitTurn(Math.max(0, minIntervalMs))) {
                return ExternalResult.failure(failureCode);
            }
            try {
                return ExternalResult.success(request.get());
            } catch (HttpClientErrorException clientException) {
                if (clientException.getStatusCode().value() == 429) {
                    if (attempt < retries) {
                        pacer.defer(retryAfterMillis(clientException));
                        continue;
                    }
                    return ExternalResult.failure(failureCode + "_RATE_LIMITED");
                }
                return ExternalResult.failure(failureCode);
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

    private static long retryAfterMillis(HttpClientErrorException exception) {
        String retryAfter = exception.getResponseHeaders() == null
                ? null : exception.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                if (seconds > 0) {
                    return Math.min(seconds * 1000L, 30_000L);
                }
            } catch (NumberFormatException ignored) {
                // Fall through to a bounded default when the header is a date.
            }
        }
        return 1_000L;
    }

    private static final class ProviderPacer {
        private long nextAllowedAtMillis;

        private boolean awaitTurn(int minIntervalMs) {
            long delay;
            synchronized (this) {
                long now = System.currentTimeMillis();
                long scheduled = Math.max(now, nextAllowedAtMillis);
                nextAllowedAtMillis = scheduled + minIntervalMs;
                delay = scheduled - now;
            }
            if (delay <= 0) {
                return true;
            }
            try {
                Thread.sleep(delay);
                return true;
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private synchronized void defer(long delayMillis) {
            nextAllowedAtMillis = Math.max(nextAllowedAtMillis, System.currentTimeMillis() + delayMillis);
        }
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
