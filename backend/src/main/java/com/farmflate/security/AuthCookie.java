package com.farmflate.security;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

/** Cookie settings shared by the OAuth success and logout paths. */
public final class AuthCookie {
    public static final String ACCESS_TOKEN_NAME = "farmflate_access_token";

    private AuthCookie() {
    }

    public static ResponseCookie accessToken(String value, boolean secure, Duration maxAge) {
        return ResponseCookie.from(ACCESS_TOKEN_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie expired(boolean secure) {
        return accessToken("", secure, Duration.ZERO);
    }
}
