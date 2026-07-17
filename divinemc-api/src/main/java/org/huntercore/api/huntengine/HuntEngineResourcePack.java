package org.huntercore.api.huntengine;

import org.jetbrains.annotations.NotNull;

/**
 * Published resource-pack state as owned by HuntEngine.
 *
 * @param sha1 lowercase hexadecimal SHA-1 when known, otherwise an empty string
 * @param revision immutable published hash or version label, otherwise an empty string
 */
public record HuntEngineResourcePack(
    boolean configured,
    boolean published,
    boolean required,
    @NotNull String url,
    @NotNull String sha1,
    @NotNull String revision,
    @NotNull String message
) {

    public HuntEngineResourcePack {
        if (message.isBlank()) {
            throw new IllegalArgumentException("resource pack message must not be blank");
        }
    }

    public static @NotNull HuntEngineResourcePack unavailable() {
        return new HuntEngineResourcePack(false, false, false, "", "", "", "HuntEngine is not available.");
    }
}
