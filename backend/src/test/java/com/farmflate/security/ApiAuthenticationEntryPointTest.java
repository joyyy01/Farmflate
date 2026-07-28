package com.farmflate.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiAuthenticationEntryPointTest {

    @Test
    void returns_the_shared_api_error_envelope_for_unauthenticated_api_requests() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest("GET", "/api/assistant/messages"), response, mock(AuthenticationException.class));

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.path("error").path("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("error").path("message").asText()).isEqualTo("인증이 필요합니다.");
        assertThat(body.path("error").path("details").isNull()).isTrue();
        assertThat(body.path("error").path("retryable").asBoolean()).isFalse();
    }
}
