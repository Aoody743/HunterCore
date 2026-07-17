package org.huntercore.api.gui;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Presentation policy for a plugin's managed GUI screens.
 *
 * @param presentationResolver resolves the viewer locale and visual capability on each render
 */
public record HunterGuiRegistrationOptions(@NotNull HunterGuiPresentationResolver presentationResolver) {
    private static final HunterGuiRegistrationOptions DEFAULTS = new HunterGuiRegistrationOptions(
        HunterGuiPresentationResolver.defaultResolver()
    );

    public HunterGuiRegistrationOptions {
        Objects.requireNonNull(presentationResolver, "presentationResolver");
    }

    /**
     * Uses each player's Minecraft locale and the vanilla-safe theme.
     *
     * @return default options
     */
    public static @NotNull HunterGuiRegistrationOptions defaults() {
        return DEFAULTS;
    }
}
