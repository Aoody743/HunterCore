package org.huntercore.api.huntengine;

import org.jetbrains.annotations.NotNull;

/**
 * Metadata for a native content package managed by HuntEngine staging.
 */
public record HuntEngineContentPackage(
    @NotNull String id,
    @NotNull String fileName,
    long size,
    @NotNull HuntEngineContentPackageState state,
    @NotNull String message
) {

    public HuntEngineContentPackage {
        if (id.isBlank() || fileName.isBlank() || size < 0L || message.isBlank()) {
            throw new IllegalArgumentException("invalid HuntEngine content package metadata");
        }
    }
}
