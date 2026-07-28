package com.farmflate.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthSettingsTest {

    @Test
    void rejects_blank_or_placeholder_client_ids_before_oauth_can_start() {
        KakaoOAuthSettings settings = new KakaoOAuthSettings();
        ReflectionTestUtils.setField(settings, "clientId", "test-client-id");
        ReflectionTestUtils.setField(settings, "redirectUri", "http://127.0.0.1:8080/login/oauth2/code/kakao");

        assertThat(settings.isConfigured()).isFalse();
    }

    @Test
    void accepts_only_a_kakao_rest_api_key_shape_with_the_callback_path() {
        KakaoOAuthSettings settings = new KakaoOAuthSettings();
        ReflectionTestUtils.setField(settings, "clientId", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(settings, "redirectUri", "http://127.0.0.1:8080/login/oauth2/code/kakao");

        assertThat(settings.isConfigured()).isTrue();
    }
}
