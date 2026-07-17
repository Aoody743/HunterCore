package org.huntercore.api.gui;

import java.time.Duration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

/**
 * Context passed to a bound GUI action.
 *
 * <p>Actions are only invoked after the runtime has verified the private inventory holder,
 * session, route, action id, and render revision. The context is valid only while that exact
 * rendered revision remains active. Do not retain it for an asynchronous callback: a later
 * navigation, refresh, close, or replacement session makes its state-changing methods fail
 * safely.</p>
 */
public interface HunterGuiActionContext extends HunterGuiRenderContext {

    /** @return stable id of the clicked action */
    @NotNull String actionId();

    /**
     * Gets the original click gesture. The GUI runtime still cancels every inventory mutation;
     * handlers may use this value for intentionally different actions such as a shift-click bulk
     * operation.
     *
     * @return click gesture that activated this action
     */
    @NotNull ClickType clickType();

    /**
     * Pushes the current route onto the per-player navigation stack and opens {@code route}.
     *
     * @param route route to open
     * @return the transition outcome
     */
    @NotNull HunterGuiOpenResult navigate(@NotNull HunterGuiRoute route);

    /**
     * Re-renders the current route without altering navigation history.
     *
     * @return the transition outcome
     */
    @NotNull HunterGuiOpenResult refresh();

    /**
     * Returns to the prior route, if one exists.
     *
     * @return {@code true} when a prior route was opened
     */
    boolean back();

    /** Closes and clears the current managed GUI session. */
    void close();

    /**
     * Arms or replaces a confirmation token for this player's current GUI session.
     *
     * <p>The supplied revalidator is evaluated when the token is consumed, not only when the
     * confirmation screen is opened. Use it to reject a delete, teleport, or permission action
     * whose target changed while the player was deciding.</p>
     *
     * @param confirmationId stable confirmation id
     * @param ttl positive lifetime for this confirmation
     * @param revalidator check run immediately before consumption
     * @return the armed confirmation
     */
    @NotNull HunterGuiConfirmation armConfirmation(
        @NotNull String confirmationId,
        @NotNull Duration ttl,
        @NotNull HunterGuiConfirmationValidator revalidator
    );

    /**
     * Arms a confirmation whose only guard is its TTL.
     *
     * @param confirmationId stable confirmation id
     * @param ttl positive lifetime for this confirmation
     * @return the armed confirmation
     */
    default @NotNull HunterGuiConfirmation armConfirmation(@NotNull final String confirmationId, @NotNull final Duration ttl) {
        return this.armConfirmation(confirmationId, ttl, context -> true);
    }

    /**
     * Consumes one confirmation token exactly once.
     *
     * @param confirmationId token to consume
     * @return whether it was present, timely, and still valid
     */
    @NotNull HunterGuiConfirmationResult consumeConfirmation(@NotNull String confirmationId);

    /**
     * Removes a pending confirmation without consuming it.
     *
     * @param confirmationId token to remove
     */
    void clearConfirmation(@NotNull String confirmationId);
}
