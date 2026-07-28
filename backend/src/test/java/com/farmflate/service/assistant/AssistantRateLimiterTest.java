package com.farmflate.service.assistant;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantRateLimiterTest {

    @Test
    void limits_each_authenticated_user_without_blocking_another_user() {
        AssistantRateLimiter limiter = new AssistantRateLimiter(
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC), 2);

        assertTrue(limiter.tryAcquire("farmer-a@example.com"));
        assertTrue(limiter.tryAcquire("farmer-a@example.com"));
        assertFalse(limiter.tryAcquire("farmer-a@example.com"));
        assertTrue(limiter.tryAcquire("farmer-b@example.com"));
    }
}
