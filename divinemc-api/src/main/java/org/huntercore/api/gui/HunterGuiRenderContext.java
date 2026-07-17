package org.huntercore.api.gui;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Immutable context supplied while a screen creates a view. */
public interface HunterGuiRenderContext {

    /** @return player who will see the view */
    @NotNull Player player();

    /** @return route being rendered */
    @NotNull HunterGuiRoute route();

    /** @return locale and visual capability selected for this render */
    @NotNull HunterGuiPresentation presentation();

    /**
     * Gets the action revision that will be bound to this view.
     *
     * <p>The runtime rejects clicks from any older revision automatically.</p>
     *
     * @return monotonically increasing revision for this player's session
     */
    long actionRevision();
}
