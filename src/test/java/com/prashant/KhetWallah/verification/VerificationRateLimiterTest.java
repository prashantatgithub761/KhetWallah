package com.prashant.KhetWallah.verification;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class VerificationRateLimiterTest {
    private final VerificationServiceTest.MutableClock clock =
            new VerificationServiceTest.MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
    private final VerificationRateLimiter limiter = new VerificationRateLimiter(clock);

    @Test
    void allowsThirtyRequestsPerAddressAndResetsOnlyAfterOneHour() {
        for (int i = 0; i < 30; i++) {
            limiter.check("192.0.2.10");
        }
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> limiter.check("192.0.2.10"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
        assertDoesNotThrow(() -> limiter.check("192.0.2.11"));
        clock.advance(Duration.ofMinutes(59));
        assertThrows(ResponseStatusException.class, () -> limiter.check("192.0.2.10"));
        clock.advance(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.check("192.0.2.10"));
    }

    @Test
    void boundedMapRejectsNewAddressesUntilExpiredKeysCanBeRemoved() {
        for (int i = 0; i < 10_000; i++) {
            limiter.check("address-" + i);
        }

        assertThrows(ResponseStatusException.class, () -> limiter.check("extra-address"));
        assertDoesNotThrow(() -> limiter.check("address-0"));
        clock.advance(Duration.ofHours(1));
        assertDoesNotThrow(() -> limiter.check("extra-address"));
    }
}
