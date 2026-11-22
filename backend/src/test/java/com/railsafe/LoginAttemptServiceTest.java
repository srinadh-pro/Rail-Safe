package com.railsafe;

import com.railsafe.security.LoginAttemptService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies per-client login lockout, expiry and success reset. */
class LoginAttemptServiceTest {

    private static final String CLIENT = "203.0.113.7";
    private static final String OTHER_CLIENT = "203.0.113.8";

    @Test
    void locksAfterThresholdAndClearsAfterWindow() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock, 3, 15);

        service.recordFailure(CLIENT);
        service.recordFailure(CLIENT);
        assertFalse(service.isLocked(CLIENT), "Two failures are below the threshold");

        service.recordFailure(CLIENT);
        assertTrue(service.isLocked(CLIENT), "Third failure triggers the lockout");

        clock.advance(Duration.ofMinutes(14));
        assertTrue(service.isLocked(CLIENT), "Still locked before the window ends");

        clock.advance(Duration.ofMinutes(2));
        assertFalse(service.isLocked(CLIENT), "Lock expires after the window");

        service.recordFailure(CLIENT);
        assertFalse(service.isLocked(CLIENT), "Counter restarts cleanly after expiry");
    }

    @Test
    void tracksClientsIndependentlyAndResetsOnSuccess() {
        MutableClock clock = new MutableClock();
        LoginAttemptService service = new LoginAttemptService(clock, 2, 15);

        service.recordFailure(CLIENT);
        service.recordFailure(OTHER_CLIENT);
        assertFalse(service.isLocked(CLIENT));
        assertFalse(service.isLocked(OTHER_CLIENT));

        service.recordFailure(CLIENT);
        assertTrue(service.isLocked(CLIENT));
        assertFalse(service.isLocked(OTHER_CLIENT), "Another client is unaffected");

        service.recordSuccess(CLIENT);
        assertFalse(service.isLocked(CLIENT), "Successful login clears the counter");
    }

    /** Minimal controllable clock so expiry can be tested without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
