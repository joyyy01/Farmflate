package com.example.aiworkspace.service.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        return string(header, "resultCode", "result_Code", "result_code", "code");
    }

    static boolean isProviderSuccessCode(String code) {
        return "00".equals(code) || "200".equals(code);
    }

    static boolean isProviderNoDataCode(String code) {
        return "301".equals(code);
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
