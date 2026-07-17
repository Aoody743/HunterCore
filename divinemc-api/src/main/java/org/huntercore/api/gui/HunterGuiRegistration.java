package org.huntercore.api.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Plugin-scoped GUI registration.
 *
 * <p>The runtime attaches {@link #listener()} under {@link #owner()} during registration. Plugins
 * should retain this handle and call {@link #close()} from their own shutdown path when immediate
 * cleanup is desired; plugin disable also closes it automatically. State-changing operations on
 * this handle must run on Bukkit's primary server thread. {@link #open(Player, HunterGuiRoute)}
 * and {@link #refresh(Player)} report {@link HunterGuiOpenResult#WRONG_THREAD} when called from
 * another thread; boolean and count-returning operations fail closed.</p>
 */
public interface HunterGuiRegistration extends AutoCloseable {

    /**
     * Gets the plugin that owns this registration.
     *
     * @return the owning plugin
     */
    @NotNull Plugin owner();

    /**
     * Gets the listener adapter attached under {@link #owner()}.
     *
     * <p>This method exists for lifecycle diagnostics and interoperability. The runtime already
     * registered this listener; do not register it a second time.</p>
     *
     * @return the plugin-owned listener adapter
     */
    @NotNull Listener listener();

    /**
     * Whether this registration can still open and manage views.
     *
     * @return {@code true} while active
     */
    boolean active();

    /**
     * Opens a route and resets this player's GUI navigation stack for this registration.
     *
     * @param player viewer
     * @param route initial route
     * @return the outcome; never {@code null}
     */
    @NotNull HunterGuiOpenResult open(@NotNull Player player, @NotNull HunterGuiRoute route);

    /**
     * Re-renders the current route for this registration.
     *
     * @param player viewer
     * @return the outcome; never {@code null}
     */
    @NotNull HunterGuiOpenResult refresh(@NotNull Player player);

    /**
     * Goes back one route without closing a root screen.
     *
     * @param player viewer
     * @return {@code true} when a prior route was opened
     */
    boolean back(@NotNull Player player);

    /**
     * Closes this registration's active GUI for one player.
     *
     * @param player viewer
     * @return {@code true} when a managed session was closed
     */
    boolean close(@NotNull Player player);

    /**
     * Closes every view owned by this registration and clears their sessions.
     *
     * @return number of sessions closed
     */
    int closeAll();

    /**
     * Closes all views and detaches the plugin-owned listener. This operation is idempotent.
     */
    @Override
    void close();
}
