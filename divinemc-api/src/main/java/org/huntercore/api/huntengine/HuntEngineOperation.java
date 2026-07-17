package org.huntercore.api.huntengine;

import java.time.Instant;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Snapshot of an asynchronous engine operation. */
public record HuntEngineOperation(
    @NotNull UUID id,
    @NotNull HuntEngineOperationType type,
    @NotNull HuntEngineOperationState state,
    long contentRevision,
    @NotNull String message,
    @NotNull Instant startedAt,
    @Nullable Instant completedAt
) {

    public HuntEngineOperation {
        if (message.isBlank()) {
            throw new IllegalArgumentException("operation message must not be blank");
        }
        if (contentRevision < -1L) {
            throw new IllegalArgumentException("contentRevision must be >= -1");
        }
    }
}
