package com.farmflate.service.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Limits expensive assistant calls per authenticated user in a fixed one-minute window. */
@Service
public class AssistantRateLimiter {
    private static final long WINDOW_MILLIS = 60_000L;

    private final Clock clock;
    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public AssistantRateLimiter(
            Clock clock,
            @Value("${app.assistant.max-requests-per-minute:20}") int maxRequestsPerMinute
    ) {
        this.clock = clock;
        this.maxRequestsPerMinute = Math.max(1, maxRequestsPerMinute);
    }

    public boolean tryAcquire(String userEmail) {
        long now = clock.millis();
        AtomicBoolean allowed = new AtomicBoolean(false);
        counters.compute(userEmail, (ignored, current) -> {
            if (current == null || now - current.windowStartedAtMillis() >= WINDOW_MILLIS) {
                allowed.set(true);
                return new WindowCounter(now, 1);
            }
            if (current.count() >= maxRequestsPerMinute) return current;
            allowed.set(true);
            return new WindowCounter(current.windowStartedAtMillis(), current.count() + 1);
        });
        return allowed.get();
    }

    private record WindowCounter(long windowStartedAtMillis, int count) {
    }
}
