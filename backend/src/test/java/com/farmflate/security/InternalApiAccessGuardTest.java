package com.farmflate.security;

import com.farmflate.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalApiAccessGuardTest {

    @Test
    void rejectsMissingOrWrongToken() {
        InternalApiAccessGuard guard = new InternalApiAccessGuard("expected-token");

        assertThatThrownBy(() -> guard.requireValid(null))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        assertThatThrownBy(() -> guard.requireValid("wrong-token"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
}
