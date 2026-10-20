package com.prashant.KhetWallah.verification;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Per-instance additional protection; persistent email limits remain authoritative. */
@Component
public class VerificationRateLimiter {
    private static final int MAX_ATTEMPTS = 30;
    private static final int MAX_KEYS = 10_000;
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    private Instant nextCleanup = Instant.MIN;

    public VerificationRateLimiter() {
        this(Clock.systemUTC());
    }

    VerificationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void check(String ip) {
        Instant now = clock.instant();
        if (!now.isBefore(nextCleanup)) {
            buckets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
            nextCleanup = now.plusSeconds(60);
        }
        String key = ip == null ? "unknown" : ip;
        Bucket bucket = buckets.get(key);
        if (bucket == null || !now.isBefore(bucket.expiresAt)) {
            if (bucket == null && buckets.size() >= MAX_KEYS) {
                throw rateLimited();
            }
            bucket = new Bucket(now.plus(WINDOW));
            buckets.put(key, bucket);
        }
        if (bucket.attempts >= MAX_ATTEMPTS) {
            throw rateLimited();
        }
        bucket.attempts++;
    }

    private ResponseStatusException rateLimited() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many verification requests; please try again after one hour");
    }

    private static final class Bucket {
        private final Instant expiresAt;
        private int attempts;

        private Bucket(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
