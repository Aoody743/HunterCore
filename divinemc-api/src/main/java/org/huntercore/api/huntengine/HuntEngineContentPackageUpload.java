package org.huntercore.api.huntengine;

import org.jetbrains.annotations.NotNull;

/**
 * An in-memory package upload supplied to HuntEngine-owned staging.
 *
 * <p>Both construction and access clone the payload so an HTTP handler cannot mutate data while
 * the engine validates it.</p>
 */
public record HuntEngineContentPackageUpload(@NotNull String fileName, @NotNull byte[] contents) {

    public HuntEngineContentPackageUpload {
        if (fileName.isBlank() || contents.length == 0) {
            throw new IllegalArgumentException("content package upload must have a file name and contents");
        }
        contents = contents.clone();
    }

    @Override
    public byte @NotNull [] contents() {
        return this.contents.clone();
    }
}
