package org.huntercore.api.gui;

import java.time.Instant;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A confirmation token armed for one managed GUI session. */
public record HunterGuiConfirmation(@NotNull String id, @NotNull Instant expiresAt) {
    public HunterGuiConfirmation {
        id = HunterGuiRoute.requireStableId("confirmationId", id);
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
