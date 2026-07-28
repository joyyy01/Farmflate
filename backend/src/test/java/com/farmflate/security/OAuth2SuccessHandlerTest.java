package com.farmflate.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2SuccessHandlerTest {

    @Test
    void stores_the_jwt_only_in_an_http_only_same_site_cookie() throws Exception {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(tokenProvider);
        ReflectionTestUtils.setField(handler, "frontendUrl", "https://app.example.com");
        ReflectionTestUtils.setField(handler, "jwtExpirationMs", 60_000L);
        ReflectionTestUtils.setField(handler, "cookieSecure", true);
        Authentication authentication = mock(Authentication.class);
        when(tokenProvider.generateToken(authentication)).thenReturn("jwt-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.example.com/oauth2/callback/kakao");
        assertThat(response.getRedirectedUrl()).doesNotContain("token");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .contains("farmflate_access_token=jwt-value", "HttpOnly", "SameSite=Lax", "Secure", "Max-Age=60");
    }
}
