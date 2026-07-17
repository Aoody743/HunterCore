package org.huntercore.api.gui;

import java.util.Locale;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Locale and visual-capability context supplied to a screen render.
 *
 * <p>{@link HunterGuiTheme#VANILLA} must remain usable without a client resource pack. Screens
 * may render richer components for {@link HunterGuiTheme#RESOURCE_PACK}, but should retain a
 * vanilla-safe route and action layout.</p>
 *
 * @param locale locale selected for this viewer
 * @param theme visual capability selected for this viewer
 */
public record HunterGuiPresentation(@NotNull Locale locale, @NotNull HunterGuiTheme theme) {
    public HunterGuiPresentation {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(theme, "theme");
    }

    public static @NotNull HunterGuiPresentation vanilla(@NotNull final Locale locale) {
        return new HunterGuiPresentation(locale, HunterGuiTheme.VANILLA);
    }

    public static @NotNull HunterGuiPresentation resourcePack(@NotNull final Locale locale) {
        return new HunterGuiPresentation(locale, HunterGuiTheme.RESOURCE_PACK);
    }
}
