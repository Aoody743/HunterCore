package org.huntercore.plugins.tools;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bounds expensive web authentication work before password hashing begins.
 */
final class WebAuthThrottle {
    private final int maxTrackedKeys;
    private final int maxAttemptsPerKey;
    private final int maxAttemptsPerIp;
    private final int maxConcurrent;
    private final long windowMillis;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;
    private final Map<String, AttemptState> attempts = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<String, WindowState> ipWindows = new LinkedHashMap<>(16, 0.75F, true);
    private int inFlight;

    WebAuthThrottle(
        final int maxTrackedKeys,
        final int maxAttemptsPerKey,
        final int maxAttemptsPerIp,
        final int maxConcurrent,
        final long windowMillis,
        final long baseBackoffMillis,
        final long maxBackoffMillis
    ) {
        this.maxTrackedKeys = Math.max(16, maxTrackedKeys);
        this.maxAttemptsPerKey = Math.max(1, maxAttemptsPerKey);
        this.maxAttemptsPerIp = Math.max(1, maxAttemptsPerIp);
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.windowMillis = Math.max(1_000L, windowMillis);
        this.baseBackoffMillis = Math.max(100L, baseBackoffMillis);
        this.maxBackoffMillis = Math.max(this.baseBackoffMillis, maxBackoffMillis);
    }

    synchronized Decision acquire(final String operation, final String remoteIp, final String username, final long nowMillis) {
        this.removeExpired(nowMillis);
        if (this.inFlight >= this.maxConcurrent) {
            return Decision.denied(Math.max(250L, this.baseBackoffMillis));
        }

        final String ip = normalize(remoteIp, 128);
        final String key = normalize(operation, 32) + '\n' + ip + '\n' + normalize(username, 64);
        final AttemptState state = this.attempts.computeIfAbsent(key, ignored -> new AttemptState(nowMillis));
        state.resetWindowIfNeeded(nowMillis, this.windowMillis);
        if (state.blockedUntilMillis > nowMillis) {
            return Decision.denied(state.blockedUntilMillis - nowMillis);
        }
        if (state.attempts >= this.maxAttemptsPerKey) {
            return Decision.denied(Math.max(1L, state.windowStartedMillis + this.windowMillis - nowMillis));
        }

        final WindowState ipWindow = this.ipWindows.computeIfAbsent(ip, ignored -> new WindowState(nowMillis));
        ipWindow.resetIfNeeded(nowMillis, this.windowMillis);
        if (ipWindow.attempts >= this.maxAttemptsPerIp) {
            return Decision.denied(Math.max(1L, ipWindow.windowStartedMillis + this.windowMillis - nowMillis));
        }

        state.attempts++;
        // Prevent parallel double-clicks from multiplying PBKDF2 work for one identity.
        state.blockedUntilMillis = nowMillis + this.baseBackoffMillis;
        ipWindow.attempts++;
        this.inFlight++;
        this.trimToBounds();
        return Decision.allowed(new Ticket(key));
    }

    synchronized void complete(final Ticket ticket, final boolean success, final long nowMillis) {
        if (ticket == null || ticket.completed) {
            return;
        }
        ticket.completed = true;
        this.inFlight = Math.max(0, this.inFlight - 1);
        if (success) {
            this.attempts.remove(ticket.key);
            return;
        }
        final AttemptState state = this.attempts.computeIfAbsent(ticket.key, ignored -> new AttemptState(nowMillis));
        state.failures = Math.min(30, state.failures + 1);
        final int shift = Math.min(20, state.failures - 1);
        final long scaled = this.baseBackoffMillis > Long.MAX_VALUE >> shift
            ? this.maxBackoffMillis
            : this.baseBackoffMillis << shift;
        state.blockedUntilMillis = nowMillis + Math.min(this.maxBackoffMillis, scaled);
        this.trimToBounds();
    }

    synchronized int trackedKeyCount() {
        return this.attempts.size();
    }

    private void removeExpired(final long nowMillis) {
        final Iterator<AttemptState> attemptsIterator = this.attempts.values().iterator();
        while (attemptsIterator.hasNext()) {
            final AttemptState state = attemptsIterator.next();
            if (state.blockedUntilMillis <= nowMillis && nowMillis - state.windowStartedMillis >= this.windowMillis) {
                attemptsIterator.remove();
            }
        }
        final Iterator<WindowState> ipIterator = this.ipWindows.values().iterator();
        while (ipIterator.hasNext()) {
            final WindowState state = ipIterator.next();
            if (nowMillis - state.windowStartedMillis >= this.windowMillis) {
                ipIterator.remove();
            }
        }
    }

    private void trimToBounds() {
        trim(this.attempts, this.maxTrackedKeys);
        trim(this.ipWindows, this.maxTrackedKeys);
    }

    private static void trim(final Map<String, ?> values, final int maximumSize) {
        final Iterator<String> iterator = values.keySet().iterator();
        while (values.size() > maximumSize && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static String normalize(final String value, final int maximumLength) {
        final String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }

    static final class Ticket {
        private final String key;
        private boolean completed;

        private Ticket(final String key) {
            this.key = key;
        }
    }

    record Decision(Ticket ticket, long retryAfterMillis) {
        static Decision allowed(final Ticket ticket) {
            return new Decision(ticket, 0L);
        }

        static Decision denied(final long retryAfterMillis) {
            return new Decision(null, Math.max(1L, retryAfterMillis));
        }

        boolean allowed() {
            return this.ticket != null;
        }
    }

    private static final class AttemptState {
        private long windowStartedMillis;
        private long blockedUntilMillis;
        private int attempts;
        private int failures;

        private AttemptState(final long nowMillis) {
            this.windowStartedMillis = nowMillis;
        }

        private void resetWindowIfNeeded(final long nowMillis, final long windowMillis) {
            if (nowMillis - this.windowStartedMillis < windowMillis) {
                return;
            }
            this.windowStartedMillis = nowMillis;
            this.attempts = 0;
            this.failures = 0;
            this.blockedUntilMillis = 0L;
        }
    }

    private static final class WindowState {
        private long windowStartedMillis;
        private int attempts;

        private WindowState(final long nowMillis) {
            this.windowStartedMillis = nowMillis;
        }

        private void resetIfNeeded(final long nowMillis, final long windowMillis) {
            if (nowMillis - this.windowStartedMillis >= windowMillis) {
                this.windowStartedMillis = nowMillis;
                this.attempts = 0;
            }
        }
    }
}
