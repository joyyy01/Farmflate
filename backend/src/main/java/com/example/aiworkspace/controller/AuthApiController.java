package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.user.Role;
import com.example.aiworkspace.domain.user.User;
import com.example.aiworkspace.domain.user.UserRepository;
import com.example.aiworkspace.security.JwtTokenProvider;
import com.example.aiworkspace.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping("/dev-login")
    public void devLoginRedirect(HttpServletResponse response) throws IOException {
        String devEmail = "kakao_farmer@farmflate.com";

        User user = userRepository.findByEmail(devEmail)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(devEmail)
                        .nickname("농부 홍길동님")
                        .provider("kakao")
                        .providerId("kakao_dev_123456789")
                        .role(Role.USER)
                        .build()));

        UserPrincipal userPrincipal = UserPrincipal.create(user, Collections.emptyMap());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String token = tokenProvider.generateToken(authentication);

        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/callback/kakao")
                .queryParam("token", token)
                .build().toUriString();

        response.sendRedirect(targetUrl);
    }

    @PostMapping("/dev-login")
    public ResponseEntity<Map<String, Object>> devLoginJson() {
        String devEmail = "kakao_farmer@farmflate.com";

        User user = userRepository.findByEmail(devEmail)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(devEmail)
                        .nickname("농부 홍길동님")
                        .provider("kakao")
                        .providerId("kakao_dev_123456789")
                        .role(Role.USER)
                        .build()));

        UserPrincipal userPrincipal = UserPrincipal.create(user, Collections.emptyMap());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

        String token = tokenProvider.generateToken(authentication);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("token", token);
        response.put("userEmail", user.getEmail());
        response.put("userName", user.getNickname());

        return ResponseEntity.ok(response);
    }
}
