package org.huntercore.api;

import java.util.Collection;
import org.huntercore.api.fakeplayer.HunterFakePlayerService;
import org.huntercore.api.gui.HunterGuiService;
import org.huntercore.api.huntengine.HuntEngineService;
import org.huntercore.api.huntengine.HuntEngineServices;
import org.huntercore.api.network.HunterConnectionService;
import org.jetbrains.annotations.NotNull;

public interface HunterCoreApi {

    @NotNull String name();

    @NotNull String version();

    @NotNull Collection<HunterBundledPlugin> bundledPlugins();

    @NotNull HunterFakePlayerService fakePlayers();

    /**
     * Gets the shared inventory-GUI runtime.
     *
     * <p>The default is deliberately unavailable rather than {@code null}. This lets plugins
     * safely retain a fallback UI when they are loaded without the HunterCore server runtime.</p>
     *
     * @return the GUI runtime, or an explicit unavailable no-op service
     */
    default @NotNull HunterGuiService gui() {
        return HunterGuiService.unavailable();
    }

    /**
     * Gets the bundled HuntEngine integration, if its plugin has registered a Bukkit service.
     *
     * <p>This default remains safe while HunterCore starts before its bundled plugins. The service
     * is resolved dynamically so it becomes available as soon as HuntEngine enables.</p>
     */
    default @NotNull HuntEngineService huntEngine() {
        return HuntEngineServices.get();
    }

    default @NotNull HunterConnectionService connections() {
        return HunterConnectionService.unavailable();
    }

    default @NotNull String language() {
        return HunterLanguage.DEFAULT;
    }

    void registerCommandExtension(@NotNull HunterCommandExtension extension);

    @NotNull Collection<HunterCommandExtension> commandExtensions();
}
