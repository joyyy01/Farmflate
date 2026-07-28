package com.farmflate.integration;

import java.util.List;
import java.util.Objects;

/**
 * The stable boundary between a provider adapter and its consumers.
 *
 * <p>An adapter always returns an instance of this type.  A provider response
 * with no matching records is {@link Status#EMPTY}; invalid payloads, HTTP
 * failures, and provider-side errors are {@link Status#FAILURE}.  Keeping
 * those states separate prevents consumers from silently treating an outage as
 * an absence of regional data.</p>
 */
public record ExternalResult<T>(Status status, T value, String errorCode, List<NormalizedMetric> metrics) {

    public enum Status {
        SUCCESS,
        EMPTY,
        FAILURE
    }

    public ExternalResult {
        status = Objects.requireNonNull(status, "status must not be null");
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        if (status != Status.FAILURE) {
            errorCode = null;
        } else if (errorCode == null || errorCode.isBlank()) {
            errorCode = "UNKNOWN_PROVIDER_FAILURE";
        }
    }

    public static <T> ExternalResult<T> success(T value, List<NormalizedMetric> metrics) {
        return new ExternalResult<>(Status.SUCCESS, value, null, metrics);
    }

    public static <T> ExternalResult<T> success(T value) {
        return success(value, List.of());
    }

    public static <T> ExternalResult<T> empty(List<NormalizedMetric> metrics) {
        return new ExternalResult<>(Status.EMPTY, null, null, metrics);
    }

    public static <T> ExternalResult<T> empty() {
        return empty(List.of());
    }

    public static <T> ExternalResult<T> failure(String errorCode, List<NormalizedMetric> metrics) {
        return new ExternalResult<>(Status.FAILURE, null, errorCode, metrics);
    }

    /**
     * Preserves provider data that was obtained before a sibling request
     * failed. Consumers must still treat this as a failure; a later policy
     * layer may decide whether the partial value is admissible.
     */
    public static <T> ExternalResult<T> failure(String errorCode, T partialValue, List<NormalizedMetric> metrics) {
        return new ExternalResult<>(Status.FAILURE, partialValue, errorCode, metrics);
    }

    public static <T> ExternalResult<T> failure(String errorCode) {
        return failure(errorCode, List.of());
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isEmpty() {
        return status == Status.EMPTY;
    }

    public boolean isFailure() {
        return status == Status.FAILURE;
    }

    public T valueOr(T fallback) {
        return isSuccess() && value != null ? value : fallback;
    }

    /** Returns the same result with all normalized metrics marked as cache hits. */
    public ExternalResult<T> asCached() {
        return new ExternalResult<>(status, value, errorCode,
                metrics.stream().map(NormalizedMetric::asCached).toList());
    }
}
