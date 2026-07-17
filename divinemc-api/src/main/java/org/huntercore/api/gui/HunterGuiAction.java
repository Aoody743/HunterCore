package org.huntercore.api.gui;

import org.jetbrains.annotations.NotNull;

/** Handler bound to one explicit action id in a rendered view. */
@FunctionalInterface
public interface HunterGuiAction {

    /**
     * Handles an accepted click on the action's bound slot.
     *
     * @param context action and navigation context
     */
    void execute(@NotNull HunterGuiActionContext context);
}
