package org.huntercore.api.gui;

import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Immutable item and optional action binding for one top-inventory slot. */
public final class HunterGuiSlot {
    private final ItemStack item;
    private final String actionId;
    private final HunterGuiAction action;

    private HunterGuiSlot(@NotNull final ItemStack item, @Nullable final String actionId, @Nullable final HunterGuiAction action) {
        this.item = Objects.requireNonNull(item, "item").clone();
        this.actionId = actionId;
        this.action = action;
    }

    /**
     * Creates a decorative, non-interactive slot.
     *
     * @param item item to display
     * @return an immutable slot
     */
    public static @NotNull HunterGuiSlot item(@NotNull final ItemStack item) {
        return new HunterGuiSlot(item, null, null);
    }

    /**
     * Creates an explicitly bound action slot.
     *
     * @param item item to display
     * @param actionId stable action id; it is never inferred from item material or display name
     * @param action click handler
     * @return an immutable slot
     */
    public static @NotNull HunterGuiSlot button(
        @NotNull final ItemStack item,
        @NotNull final String actionId,
        @NotNull final HunterGuiAction action
    ) {
        return new HunterGuiSlot(item, HunterGuiRoute.requireStableId("actionId", actionId), Objects.requireNonNull(action, "action"));
    }

    /**
     * Gets a defensive copy of the display item.
     *
     * @return display item copy
     */
    public @NotNull ItemStack item() {
        return this.item.clone();
    }

    /** @return stable action id, or {@code null} for a decorative slot */
    public @Nullable String actionId() {
        return this.actionId;
    }

    /** @return bound action, or {@code null} for a decorative slot */
    public @Nullable HunterGuiAction action() {
        return this.action;
    }

    /** @return whether this slot has an action binding */
    public boolean interactive() {
        return this.action != null;
    }
}
