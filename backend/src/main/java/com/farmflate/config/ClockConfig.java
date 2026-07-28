package com.farmflate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * All "today" business logic (daily reports, cultivation day, task
 * acknowledgement dates) must use KST regardless of the server host's default
 * timezone, so a single shared Clock bean is injected everywhere instead of
 * each service calling Clock.systemDefaultZone().
 */
@Configuration
public class ClockConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(KST);
    }
}
