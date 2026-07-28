package com.farmflate.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    @Test
    void rejects_a_blank_jwt_secret_at_startup() {
        assertThatThrownBy(() -> new JwtTokenProvider("  ", 3_600_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret is not configured");
    }
}
