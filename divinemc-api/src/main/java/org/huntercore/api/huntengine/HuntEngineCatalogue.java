package org.huntercore.api.huntengine;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of content currently known to HuntEngine.
 *
 * @param revision monotonically increasing content revision; a negative value means unavailable
 * @param contents visible content entries, independent of any upstream engine classes
 */
public record HuntEngineCatalogue(long revision, @NotNull List<HuntEngineContent> contents) {

    public HuntEngineCatalogue {
        contents = List.copyOf(contents);
    }

    public static @NotNull HuntEngineCatalogue unavailable() {
        return new HuntEngineCatalogue(-1L, List.of());
    }
}
