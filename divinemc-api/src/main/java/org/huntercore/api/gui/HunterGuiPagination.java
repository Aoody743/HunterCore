package org.huntercore.api.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Pure pagination helpers for inventory GUI lists. */
public final class HunterGuiPagination {
    private HunterGuiPagination() {
    }

    /**
     * Takes an immutable snapshot and returns a clamped zero-based page.
     *
     * <p>An empty collection still has one empty page, which keeps previous/next button logic
     * simple and gives callers a stable page index of {@code 0}.</p>
     *
     * @param source items to snapshot
     * @param requestedPage zero-based requested page; values outside the range are clamped
     * @param pageSize maximum items on one page; must be positive
     * @param <T> item type
     * @return immutable page snapshot
     */
    public static <T> @NotNull HunterGuiPage<T> page(
        @NotNull final Collection<? extends T> source,
        final int requestedPage,
        final int pageSize
    ) {
        Objects.requireNonNull(source, "source");
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be positive");
        }

        final List<T> snapshot = new ArrayList<>(source.size());
        snapshot.addAll(source);
        final int totalItems = snapshot.size();
        final int pageCount = Math.max(1, totalItems / pageSize + (totalItems % pageSize == 0 ? 0 : 1));
        final int pageIndex = Math.min(Math.max(0, requestedPage), pageCount - 1);
        final int fromIndex = Math.min(totalItems, pageIndex * pageSize);
        final int toIndex = Math.min(totalItems, fromIndex + pageSize);
        return new HunterGuiPage<>(snapshot.subList(fromIndex, toIndex), pageIndex, pageCount, totalItems, pageSize);
    }
}
