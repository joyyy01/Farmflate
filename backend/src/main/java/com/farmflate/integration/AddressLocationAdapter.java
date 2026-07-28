package com.farmflate.integration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/** Typed boundary for a configured official address-to-coordinate lookup. */
@Component
public class AddressLocationAdapter {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String addressUrl;
    private final int retryCount;

    public AddressLocationAdapter(
            @Qualifier("locationApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.vworld.api-key:}") String apiKey,
            @Value("${app.external.vworld.address-url:}") String addressUrl,
            @Value("${app.external-api.retry-count:1}") int retryCount) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.addressUrl = addressUrl;
        this.retryCount = retryCount;
    }

    public Optional<Coordinate> resolve(String address) {
        if (address == null || address.isBlank() || !hasUsableConfiguration()) {
            return Optional.empty();
        }
        String url = UriComponentsBuilder.fromUriString(addressUrl)
                .queryParam("service", "address")
                .queryParam("request", "getcoord")
                .queryParam("version", "2.0")
                .queryParam("crs", "epsg:4326")
                .queryParam("format", "json")
                .queryParam("type", "road")
                .queryParam("address", address.trim())
                .queryParam("key", apiKey)
                .build(false)
                .toUriString();
        ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.executeRequest(
                retryCount, "ADDRESS_LOCATION_REQUEST_FAILED", () -> restTemplate.getForObject(url, Map.class));
        if (response.isFailure()) {
            return Optional.empty();
        }
        return toCoordinate(response.value(), address.trim());
    }

    private Optional<Coordinate> toCoordinate(Map<String, Object> response, String requestedAddress) {
        Map<String, Object> envelope = nested(response, "response");
        Map<String, Object> result = nested(envelope == null ? response : envelope, "result");
        Map<String, Object> point = nested(result, "point");
        if (point == null) {
            return Optional.empty();
        }
        Double longitude = doubleValue(point.get("x"));
        Double latitude = doubleValue(point.get("y"));
        if (latitude == null || longitude == null || latitude < -90d || latitude > 90d
                || longitude < -180d || longitude > 180d) {
            return Optional.empty();
        }
        String label = stringValue(result.get("text"));
        return Optional.of(new Coordinate(label == null ? requestedAddress : label, latitude, longitude,
                "VWORLD_ADDRESS_GEOCODE"));
    }

    private boolean hasUsableConfiguration() {
        return addressUrl != null && !addressUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && !apiKey.startsWith("local_");
    }

    private Map<String, Object> nested(Map<String, Object> source, String key) {
        return source == null ? null : ExternalAdapterSupport.map(source.get(key));
    }

    private Double doubleValue(Object value) {
        try {
            return value == null ? null : Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    public record Coordinate(String addressLabel, double latitude, double longitude, String sourceRef) {
    }
}
