package org.huntercore.api.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Immutable inventory view returned by a {@link HunterGuiScreen}. */
public final class HunterGuiView {
    private final Component title;
    private final int rows;
    private final Map<Integer, HunterGuiSlot> slots;

    private HunterGuiView(@NotNull final Component title, final int rows, @NotNull final Map<Integer, HunterGuiSlot> slots) {
        this.title = Objects.requireNonNull(title, "title");
        this.rows = validateRows(rows);
        final Map<Integer, HunterGuiSlot> copied = new LinkedHashMap<>();
        for (final Map.Entry<Integer, HunterGuiSlot> entry : slots.entrySet()) {
            final int slot = requireSlot(entry.getKey(), this.rows);
            copied.put(slot, Objects.requireNonNull(entry.getValue(), "slot"));
        }
        this.slots = Map.copyOf(copied);
    }

    /**
     * Starts a view definition.
     *
     * @param title inventory title
     * @param rows number of chest rows, from 1 through 6
     * @return mutable builder that produces an immutable view
     */
    public static @NotNull Builder builder(@NotNull final Component title, final int rows) {
        return new Builder(title, rows);
    }

    /** @return inventory title */
    public @NotNull Component title() {
        return this.title;
    }

    /** @return chest rows */
    public int rows() {
        return this.rows;
    }

    /** @return top-inventory size */
    public int size() {
        return this.rows * 9;
    }

    /**
     * Gets immutable slot bindings indexed by top-inventory slot.
     *
     * @return immutable slot bindings
     */
    public @NotNull Map<Integer, HunterGuiSlot> slots() {
        return this.slots;
    }

    /** Builder for a {@link HunterGuiView}. */
    public static final class Builder {
        private final Component title;
        private final int rows;
        private final Map<Integer, HunterGuiSlot> slots = new LinkedHashMap<>();

        private Builder(@NotNull final Component title, final int rows) {
            this.title = Objects.requireNonNull(title, "title");
            this.rows = validateRows(rows);
        }

        /**
         * Adds a decorative item.
         *
         * @param slot top-inventory slot
         * @param item display item
         * @return this builder
         */
        public @NotNull Builder item(final int slot, @NotNull final ItemStack item) {
            return this.slot(slot, HunterGuiSlot.item(item));
        }

        /**
         * Adds an explicitly bound button.
         *
         * @param slot top-inventory slot
         * @param item display item
         * @param actionId stable action id
         * @param action handler for that action
         * @return this builder
         */
        public @NotNull Builder button(
            final int slot,
            @NotNull final ItemStack item,
            @NotNull final String actionId,
            @NotNull final HunterGuiAction action
        ) {
            return this.slot(slot, HunterGuiSlot.button(item, actionId, action));
        }

        /**
         * Adds a prebuilt slot definition. A slot can only be assigned once per view.
         *
         * @param slot top-inventory slot
         * @param definition immutable slot definition
         * @return this builder
         */
        public @NotNull Builder slot(final int slot, @NotNull final HunterGuiSlot definition) {
            requireSlot(slot, this.rows);
            if (this.slots.putIfAbsent(slot, Objects.requireNonNull(definition, "definition")) != null) {
                throw new IllegalArgumentException("slot " + slot + " is already defined");
            }
            return this;
        }

        /** @return immutable view */
        public @NotNull HunterGuiView build() {
            return new HunterGuiView(this.title, this.rows, this.slots);
        }
    }

    private static int validateRows(final int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6");
        }
        return rows;
    }

    private static int requireSlot(final Integer slot, final int rows) {
        if (slot == null || slot < 0 || slot >= rows * 9) {
            throw new IllegalArgumentException("slot must be within the top inventory");
        }
        return slot;
    }
}
