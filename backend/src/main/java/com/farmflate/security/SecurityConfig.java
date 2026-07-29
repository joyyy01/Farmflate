package com.farmflate.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final KakaoOAuthSettings kakaoOAuthSettings;

    @Value("${app.frontend-url}")
    private String frontendOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .requestMatchers("/", "/oauth2/**", "/login/**", "/api/health", "/api/auth/kakao/availability", "/api/internal/**", "/error", "/favicon.ico").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    apiAuthenticationEntryPoint,
                    new AntPathRequestMatcher("/api/**")
                )
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (kakaoOAuthSettings.isConfigured()) {
            http.oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(auth -> auth.baseUri("/oauth2/authorization"))
                    .redirectionEndpoint(redirection -> redirection.baseUri("/login/oauth2/code/*"))
                    .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                    .successHandler(oAuth2SuccessHandler));
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173"));
        if (frontendOrigin != null && !frontendOrigin.isBlank()) {
            allowedOrigins.add(frontendOrigin);
        }
        configuration.setAllowedOrigins(allowedOrigins.stream().distinct().toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
