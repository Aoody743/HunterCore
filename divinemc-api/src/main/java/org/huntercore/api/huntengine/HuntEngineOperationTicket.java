package org.huntercore.api.huntengine;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;

/**
 * Immediate operation identity plus its eventual completion.
 *
 * <p>The initial operation is normally {@link HuntEngineOperationState#QUEUED}; callers can poll
 * {@link HuntEngineService#operation(java.util.UUID)} without blocking a web or GUI thread.</p>
 */
public record HuntEngineOperationTicket(@NotNull HuntEngineOperation operation, @NotNull CompletionStage<HuntEngineOperation> completion) {

    public HuntEngineOperationTicket {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(completion, "completion");
    }
}
