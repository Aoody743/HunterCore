package org.huntercore.api.huntengine;

import org.jetbrains.annotations.NotNull;

/** Lightweight state used by command, GUI, and web status surfaces. */
public record HuntEngineStatus(
    boolean available,
    @NotNull String version,
    long contentRevision,
    @NotNull HuntEngineLifecycleState lifecycle,
    @NotNull String message
) {

    public HuntEngineStatus {
        if (contentRevision < -1L || message.isBlank()) {
            throw new IllegalArgumentException("invalid HuntEngine status");
        }
    }

    public static @NotNull HuntEngineStatus unavailable() {
        return new HuntEngineStatus(false, "unavailable", -1L, HuntEngineLifecycleState.UNAVAILABLE, "HuntEngine is not available.");
    }
}
