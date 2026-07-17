package org.huntercore.api.gui;

import java.time.Instant;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Context supplied when a confirmation is revalidated immediately before consumption. */
public record HunterGuiConfirmationContext(
    @NotNull Player player,
    @NotNull HunterGuiRoute originRoute,
    @NotNull HunterGuiConfirmation confirmation,
    @NotNull Instant createdAt
) {
    public HunterGuiConfirmationContext {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(originRoute, "originRoute");
        Objects.requireNonNull(confirmation, "confirmation");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
