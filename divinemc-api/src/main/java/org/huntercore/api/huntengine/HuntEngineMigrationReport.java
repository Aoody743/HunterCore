package org.huntercore.api.huntengine;

import java.time.Instant;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Read-only view of the migration journal produced during bundled plugin installation. */
public record HuntEngineMigrationReport(
    @NotNull HuntEngineMigrationState state,
    @NotNull String journalLocation,
    @Nullable Instant startedAt,
    @Nullable Instant completedAt,
    @NotNull List<HuntEngineMigrationEntry> entries,
    @NotNull String message
) {

    public HuntEngineMigrationReport {
        entries = List.copyOf(entries);
        if (journalLocation.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("migration journal location and message must not be blank");
        }
    }

    public static @NotNull HuntEngineMigrationReport unavailable() {
        return new HuntEngineMigrationReport(
            HuntEngineMigrationState.NOT_REQUIRED,
            "unavailable",
            null,
            null,
            List.of(),
            "HuntEngine is not available."
        );
    }
}
