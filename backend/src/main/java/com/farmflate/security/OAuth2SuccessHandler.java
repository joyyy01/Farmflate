package com.farmflate.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenProvider tokenProvider;
    
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${app.auth.cookie-secure:true}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, 
                                        Authentication authentication) throws IOException {
        String token = tokenProvider.generateToken(authentication);
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/callback/kakao")
                .build().toUriString();
        response.addHeader(HttpHeaders.SET_COOKIE,
                AuthCookie.accessToken(token, cookieSecure, Duration.ofMillis(jwtExpirationMs)).toString());
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
