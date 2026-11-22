package com.railsafe.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks failed login attempts per client and temporarily locks abusive clients
 * out before their credentials ever reach the AuthenticationManager.
 *
 * State is deliberately in-memory: this is a lightweight abuse control, not an
 * invasive or persistent ban system. A restart clears the counters. The filter
 * records which client key the current request belongs to, and the Spring
 * authentication event listener reports the outcome against that same key.
 */
@Service
public class LoginAttemptService {

    private final Clock clock;
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final ThreadLocal<String> currentKey = new ThreadLocal<>();

    public LoginAttemptService(Clock clock,
                               @Value("${railsafe.security.login-max-attempts:10}") int maxAttempts,
                               @Value("${railsafe.security.login-lock-minutes:15}") long lockMinutes) {
        this.clock = clock;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockDuration = Duration.ofMinutes(Math.max(1, lockMinutes));
    }

    /** Remembers which client key the current request's authentication events belong to. */
    public void beginAttempt(String key) {
        currentKey.set(key);
    }

    public void endAttempt() {
        currentKey.remove();
    }

    /** True while the client is inside its temporary lockout window. */
    public boolean isLocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (attempt.lockedUntil() != null && attempt.lockedUntil().isAfter(clock.instant())) {
            return true;
        }
        // Lock expired or no lock active: give the client a clean counter. If a
        // stale non-locked record exists (e.g. a few failures below the limit),
        // keep it so the threshold still applies across requests.
        if (attempt.lockedUntil() != null) {
            attempts.remove(key, attempt);
        }
        return false;
    }

    public void recordFailureForCurrentRequest() {
        recordFailure(currentKey.get());
    }

    public void recordSuccessForCurrentRequest() {
        recordSuccess(currentKey.get());
    }

    public void recordFailure(String key) {
        if (key == null) {
            return;
        }
        attempts.compute(key, (ignored, existing) -> {
            if (existing != null && existing.lockedUntil() != null
                    && existing.lockedUntil().isAfter(clock.instant())) {
                return existing; // still locked; no need to extend
            }
            int failures = existing == null ? 1 : existing.failures() + 1;
            Instant lockedUntil = failures >= maxAttempts
                    ? clock.instant().plus(lockDuration)
                    : null;
            return new Attempt(failures, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    private record Attempt(int failures, Instant lockedUntil) {
    }
}
