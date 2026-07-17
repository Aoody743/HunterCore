package org.huntercore.api.huntengine;

import org.jetbrains.annotations.NotNull;

/**
 * Result of a synchronous HuntEngine request.
 *
 * <p>The result deliberately contains text only. Callers must not infer an engine implementation
 * detail from a message; use the typed catalogue, pack, migration, and operation APIs instead.</p>
 */
public record HuntEngineActionResult(boolean success, @NotNull String message) {

    public HuntEngineActionResult {
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
    }

    public static @NotNull HuntEngineActionResult ok(@NotNull final String message) {
        return new HuntEngineActionResult(true, message);
    }

    public static @NotNull HuntEngineActionResult fail(@NotNull final String message) {
        return new HuntEngineActionResult(false, message);
    }
}
