package com.farmflate.security;

import com.farmflate.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalApiAccessGuard {

    private final byte[] expectedToken;

    public InternalApiAccessGuard(@Value("${app.internal-api.mcp-token:}") String expectedToken) {
        this.expectedToken = expectedToken == null ? new byte[0] : expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    public void requireValid(String providedToken) {
        byte[] provided = providedToken == null ? new byte[0] : providedToken.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length == 0 || !MessageDigest.isEqual(expectedToken, provided)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_API_UNAUTHORIZED", "내부 서비스 인증이 필요합니다.");
        }
    }
}
