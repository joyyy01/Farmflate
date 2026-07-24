package com.example.aiworkspace.service.external;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * Boundary for an explicitly configured official parcel identifier service.
 * No PNU is inferred when the provider is absent or cannot support a lookup.
 */
@Component
public class ParcelIdentifierAdapter {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String identifierUrl;
    private final int retryCount;

    public ParcelIdentifierAdapter(
            @Qualifier("locationApiRestTemplate") RestTemplate restTemplate,
            @Value("${app.external.parcel.api-key:}") String apiKey,
            @Value("${app.external.parcel.identifier-url:}") String identifierUrl,
            @Value("${app.external-api.retry-count:1}") int retryCount) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.identifierUrl = identifierUrl;
        this.retryCount = retryCount;
    }

    public Optional<ParcelIdentifier> resolvePnu(double latitude, double longitude) {
        return request("latitude", latitude, "longitude", longitude)
                .flatMap(response -> findString(response, "pnu", "pnuCode", "parcelIdentifier"))
                .filter(value -> value.matches("\\d{19}"))
                .map(value -> new ParcelIdentifier(value, "OFFICIAL_PARCEL_IDENTIFIER"));
    }

    public Optional<AddressLocationAdapter.Coordinate> resolveCoordinate(String pnu) {
        if (pnu == null || !pnu.matches("\\d{19}")) {
            return Optional.empty();
        }
        return request("pnu", pnu, null, null)
                .flatMap(response -> {
                    Optional<Double> latitude = findDouble(response, "latitude", "lat", "y");
                    Optional<Double> longitude = findDouble(response, "longitude", "lon", "lng", "x");
                    if (latitude.isEmpty() || longitude.isEmpty() || latitude.get() < -90d || latitude.get() > 90d
                            || longitude.get() < -180d || longitude.get() > 180d) {
                        return Optional.empty();
                    }
                    return Optional.of(new AddressLocationAdapter.Coordinate(null, latitude.get(), longitude.get(),
                            "OFFICIAL_PARCEL_IDENTIFIER"));
                });
    }

    private Optional<Map<String, Object>> request(String firstName, Object firstValue, String secondName, Object secondValue) {
        if (!hasUsableConfiguration()) {
            return Optional.empty();
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(identifierUrl)
                .queryParam("apiKey", apiKey)
                .queryParam(firstName, firstValue);
        if (secondName != null) {
            builder.queryParam(secondName, secondValue);
        }
        ExternalResult<Map<String, Object>> response = ExternalAdapterSupport.executeRequest(
                retryCount,
                "PARCEL_IDENTIFIER_REQUEST_FAILED",
                () -> restTemplate.getForObject(builder.build(false).toUriString(), Map.class));
        return response.isSuccess() ? Optional.ofNullable(response.value()) : Optional.empty();
    }

    private boolean hasUsableConfiguration() {
        return identifierUrl != null && !identifierUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && !apiKey.startsWith("local_");
    }

    private Optional<String> findString(Map<String, Object> response, String... keys) {
        for (String key : keys) {
            Object value = response.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return Optional.of(String.valueOf(value).trim());
            }
        }
        for (String container : new String[]{"response", "result", "data", "item"}) {
            Map<String, Object> nested = ExternalAdapterSupport.map(response.get(container));
            if (nested != null) {
                Optional<String> value = findString(nested, keys);
                if (value.isPresent()) {
                    return value;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Double> findDouble(Map<String, Object> response, String... keys) {
        return findString(response, keys).flatMap(value -> {
            try {
                return Optional.of(Double.valueOf(value));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        });
    }

    public record ParcelIdentifier(String pnu, String sourceRef) {
    }
}
