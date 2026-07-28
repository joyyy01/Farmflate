package com.farmflate.security;

import com.farmflate.domain.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JwtAuthenticationFilterTest {

    @Test
    void reads_the_http_only_access_cookie_when_no_bearer_header_is_present() {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(JwtTokenProvider.class), mock(UserRepository.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookie.ACCESS_TOKEN_NAME, "cookie-jwt"));

        String token = ReflectionTestUtils.invokeMethod(filter, "getJwtFromRequest", request);

        assertThat(token).isEqualTo("cookie-jwt");
    }
}
