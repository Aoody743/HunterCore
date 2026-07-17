package org.huntercore.api.gui;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Immutable zero-based page produced by {@link HunterGuiPagination}. */
public record HunterGuiPage<T>(
    @NotNull List<T> items,
    int pageIndex,
    int pageCount,
    int totalItems,
    int pageSize
) {
    public HunterGuiPage {
        items = List.copyOf(items);
        if (pageIndex < 0 || pageCount < 1 || pageIndex >= pageCount || totalItems < 0 || pageSize < 1) {
            throw new IllegalArgumentException("invalid page metadata");
        }
        Objects.requireNonNull(items, "items");
    }

    /** @return whether an earlier zero-based page exists */
    public boolean hasPrevious() {
        return this.pageIndex > 0;
    }

    /** @return whether a later zero-based page exists */
    public boolean hasNext() {
        return this.pageIndex + 1 < this.pageCount;
    }
}
