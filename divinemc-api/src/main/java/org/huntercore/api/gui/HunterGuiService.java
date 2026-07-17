package org.huntercore.api.gui;

import java.util.Collection;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Shared, inventory-backed GUI runtime exposed by HunterCore.
 *
 * <p>Every registration is scoped to one Bukkit plugin. The runtime creates and attaches a
 * dedicated listener under that plugin, so there is no server-global listener that outlives the
 * plugin which owns its screens. Register screens from Bukkit's primary server thread; inventory
 * and listener state are intentionally confined there.</p>
 */
public interface HunterGuiService {

    /**
     * Whether this service is backed by the HunterCore server runtime.
     *
     * @return {@code true} when managed inventories can be opened
     */
    boolean available();

    /**
     * Registers screens with the default presentation resolver.
     *
     * @param owner plugin which owns the screens and lifecycle listener
     * @param screens screens with unique stable ids
     * @return a plugin-scoped registration; never {@code null}
     */
    default @NotNull HunterGuiRegistration register(
        @NotNull final Plugin owner,
        @NotNull final Collection<? extends HunterGuiScreen> screens
    ) {
        return this.register(owner, screens, HunterGuiRegistrationOptions.defaults());
    }

    /**
     * Registers screens and creates a listener owned by {@code owner}. The listener is attached
     * automatically and is removed when the registration is closed or the plugin is disabled.
     *
     * @param owner plugin which owns the screens and lifecycle listener
     * @param screens screens with unique stable ids
     * @param options presentation behaviour for views opened by this registration
     * @return a plugin-scoped registration; never {@code null}
     */
    @NotNull HunterGuiRegistration register(
        @NotNull Plugin owner,
        @NotNull Collection<? extends HunterGuiScreen> screens,
        @NotNull HunterGuiRegistrationOptions options
    );

    /**
     * Returns a non-null service that deliberately performs no GUI work. This is used by the API
     * before the HunterCore runtime is installed and is safe for plugin fallback paths.
     *
     * @return the explicit unavailable service
     */
    static @NotNull HunterGuiService unavailable() {
        return UnavailableHunterGuiService.INSTANCE;
    }

    enum UnavailableHunterGuiService implements HunterGuiService {
        INSTANCE;

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public @NotNull HunterGuiRegistration register(
            @NotNull final Plugin owner,
            @NotNull final Collection<? extends HunterGuiScreen> screens,
            @NotNull final HunterGuiRegistrationOptions options
        ) {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(screens, "screens");
            Objects.requireNonNull(options, "options");
            return new UnavailableHunterGuiRegistration(owner);
        }
    }

    final class UnavailableHunterGuiRegistration implements HunterGuiRegistration {
        private static final Listener NO_OP_LISTENER = new Listener() {
        };

        private final Plugin owner;

        private UnavailableHunterGuiRegistration(@NotNull final Plugin owner) {
            this.owner = owner;
        }

        @Override
        public @NotNull Plugin owner() {
            return this.owner;
        }

        @Override
        public @NotNull Listener listener() {
            return NO_OP_LISTENER;
        }

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public @NotNull HunterGuiOpenResult open(@NotNull final Player player, @NotNull final HunterGuiRoute route) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(route, "route");
            return HunterGuiOpenResult.UNAVAILABLE;
        }

        @Override
        public @NotNull HunterGuiOpenResult refresh(@NotNull final Player player) {
            Objects.requireNonNull(player, "player");
            return HunterGuiOpenResult.UNAVAILABLE;
        }

        @Override
        public boolean back(@NotNull final Player player) {
            Objects.requireNonNull(player, "player");
            return false;
        }

        @Override
        public boolean close(@NotNull final Player player) {
            Objects.requireNonNull(player, "player");
            return false;
        }

        @Override
        public int closeAll() {
            return 0;
        }

        @Override
        public void close() {
        }
    }
}
