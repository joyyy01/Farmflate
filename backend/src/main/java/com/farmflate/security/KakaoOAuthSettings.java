package com.farmflate.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.regex.Pattern;

/** Validates only local configuration; Kakao console state remains external. */
@Component
public class KakaoOAuthSettings {
    private static final Pattern REST_API_KEY = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final String CALLBACK_PATH = "/login/oauth2/code/kakao";

    @Value("${spring.security.oauth2.client.registration.kakao.client-id:}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri:}")
    private String redirectUri;

    public boolean isConfigured() {
        if (clientId == null || !REST_API_KEY.matcher(clientId).matches()) {
            return false;
        }
        try {
            URI uri = URI.create(redirectUri);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && CALLBACK_PATH.equals(uri.getPath());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
