package com.farmflate.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class ExternalApiConfig {

    @Value("${app.external-api.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${app.external-api.location-timeout-ms:5000}")
    private int locationTimeoutMs;

    @Bean("externalApiRestTemplate")
    public RestTemplate externalApiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Bean("locationApiRestTemplate")
    public RestTemplate locationApiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(locationTimeoutMs))
                .setReadTimeout(Duration.ofMillis(locationTimeoutMs))
                .build();
    }
}
