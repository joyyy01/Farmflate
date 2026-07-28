package com.farmflate.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/** Single response shape shared by MVC exception handling and Spring Security. */
public final class ApiErrorResponse {
    private ApiErrorResponse() {
    }

    public static Map<String, Object> body(String code, String message, Object details, boolean retryable) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", details);
        error.put("retryable", retryable);
        return Map.of("error", error);
    }
}
