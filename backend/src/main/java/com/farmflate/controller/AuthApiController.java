package com.farmflate.controller;

import com.farmflate.security.AuthCookie;
import com.farmflate.security.KakaoOAuthSettings;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {
    private final KakaoOAuthSettings kakaoOAuthSettings;

    @Value("${app.auth.cookie-secure:true}")
    private boolean cookieSecure;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, AuthCookie.expired(cookieSecure).toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/kakao/availability")
    public Map<String, Boolean> kakaoAvailability() {
        return Map.of("configured", kakaoOAuthSettings.isConfigured());
    }
}
