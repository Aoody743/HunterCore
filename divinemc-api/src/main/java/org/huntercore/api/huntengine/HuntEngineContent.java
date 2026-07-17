package org.huntercore.api.huntengine;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A display-safe content descriptor. It is intentionally not an upstream item or block object.
 */
public record HuntEngineContent(
    @NotNull String id,
    @NotNull HuntEngineContentKind kind,
    @NotNull String displayName,
    @NotNull String description,
    @NotNull List<String> categories,
    @Nullable String permission,
    boolean enabled
) {

    public HuntEngineContent {
        requireIdentifier(id, "id");
        Objects.requireNonNull(kind, "kind");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        categories = List.copyOf(categories);
        for (final String category : categories) {
            requireIdentifier(category, "category");
        }
        if (permission != null && permission.isBlank()) {
            permission = null;
        }
    }

    private static void requireIdentifier(final String value, final String name) {
        if (value == null || value.isBlank() || !value.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalArgumentException(name + " must be a stable lower-case identifier");
        }
    }
}
