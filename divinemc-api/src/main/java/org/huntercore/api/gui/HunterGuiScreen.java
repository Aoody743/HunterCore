package org.huntercore.api.gui;

import org.jetbrains.annotations.NotNull;

/** A renderable screen registered with one plugin-scoped GUI registration. */
public interface HunterGuiScreen {

    /**
     * Gets this screen's stable route id.
     *
     * @return a lowercase stable id, for example {@code hunter-tpa:home-list}
     */
    @NotNull String id();

    /**
     * Builds a fresh immutable view for a player and route.
     *
     * @param context render context
     * @return view to display
     */
    @NotNull HunterGuiView render(@NotNull HunterGuiRenderContext context);
}
