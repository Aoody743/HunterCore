package org.huntercore.api.gui;

import java.util.Locale;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Resolves the presentation capabilities for an individual GUI render. */
@FunctionalInterface
public interface HunterGuiPresentationResolver {

    /**
     * Resolves the presentation to use for one player and route.
     *
     * @param player viewer
     * @param route route about to be rendered
     * @return a non-null presentation
     */
    @NotNull HunterGuiPresentation resolve(@NotNull Player player, @NotNull HunterGuiRoute route);

    /**
     * Uses the locale reported by the Minecraft client and never assumes a resource pack.
     *
     * @return the default resolver
     */
    static @NotNull HunterGuiPresentationResolver defaultResolver() {
        return (player, route) -> {
            final Locale locale = player.locale();
            return HunterGuiPresentation.vanilla(locale == null ? Locale.ENGLISH : locale);
        };
    }
}
