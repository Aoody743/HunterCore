package org.huntercore.api.gui;

import org.jetbrains.annotations.NotNull;

/** Revalidates a pending dangerous action immediately before it is accepted. */
@FunctionalInterface
public interface HunterGuiConfirmationValidator {

    /**
     * Checks whether the pending action may still proceed.
     *
     * @param context immutable confirmation context
     * @return {@code true} when the action is still valid
     */
    boolean validate(@NotNull HunterGuiConfirmationContext context);
}
