package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebAuthThrottleTest {
    @Test
    void failedAttemptsBackOffAndSuccessClearsTheIdentity() {
        final WebAuthThrottle throttle = throttle();
        final WebAuthThrottle.Decision first = throttle.acquire("login", "127.0.0.1", "Player", 1_000L);
        assertTrue(first.allowed());
        throttle.complete(first.ticket(), false, 1_010L);

        assertFalse(throttle.acquire("login", "127.0.0.1", "player", 1_100L).allowed());
        final WebAuthThrottle.Decision second = throttle.acquire("login", "127.0.0.1", "PLAYER", 1_510L);
        assertTrue(second.allowed());
        throttle.complete(second.ticket(), true, 1_520L);

        assertTrue(throttle.acquire("login", "127.0.0.1", "player", 1_521L).allowed());
    }

    @Test
    void limitsAggregateIpEvenWhenUsernamesChange() {
        final WebAuthThrottle throttle = new WebAuthThrottle(32, 5, 2, 4, 60_000L, 500L, 8_000L);
        final WebAuthThrottle.Decision first = throttle.acquire("login", "10.0.0.4", "one", 0L);
        assertTrue(first.allowed());
        throttle.complete(first.ticket(), true, 1L);
        final WebAuthThrottle.Decision second = throttle.acquire("login", "10.0.0.4", "two", 2L);
        assertTrue(second.allowed());
        throttle.complete(second.ticket(), true, 3L);

        assertFalse(throttle.acquire("login", "10.0.0.4", "three", 4L).allowed());
        assertTrue(throttle.acquire("login", "10.0.0.4", "three", 60_001L).allowed());
    }

    @Test
    void boundsTrackedIdentityKeys() {
        final WebAuthThrottle throttle = throttle();
        for (int index = 0; index < 100; index++) {
            final WebAuthThrottle.Decision decision = throttle.acquire("login", "10.0.0." + index, "user" + index, index * 10L);
            assertTrue(decision.allowed());
            throttle.complete(decision.ticket(), false, index * 10L + 1L);
        }
        assertEquals(16, throttle.trackedKeyCount());
    }

    @Test
    void capsConcurrentPasswordWork() {
        final WebAuthThrottle throttle = new WebAuthThrottle(32, 5, 20, 1, 60_000L, 500L, 8_000L);
        final WebAuthThrottle.Decision first = throttle.acquire("login", "10.0.0.1", "one", 0L);
        assertTrue(first.allowed());
        assertFalse(throttle.acquire("login", "10.0.0.2", "two", 1L).allowed());
        throttle.complete(first.ticket(), false, 2L);
        assertTrue(throttle.acquire("login", "10.0.0.2", "two", 3L).allowed());
    }

    private static WebAuthThrottle throttle() {
        return new WebAuthThrottle(16, 5, 100, 4, 60_000L, 500L, 8_000L);
    }
}
