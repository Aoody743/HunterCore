package org.huntercore.api.huntengine;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One source artifact recorded by the HunterAssets to HuntEngine migration journal. */
public record HuntEngineMigrationEntry(
    @NotNull String source,
    @Nullable String target,
    @Nullable String sha256,
    @NotNull HuntEngineMigrationEntryState state,
    @NotNull String message
) {

    public HuntEngineMigrationEntry {
        if (source.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("migration entry source and message must not be blank");
        }
    }
}
