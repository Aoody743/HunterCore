package org.huntercore.api.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HunterGuiPaginationTest {

    @Test
    void clampsRequestedPagesAndKeepsNavigationMetadataConsistent() {
        final HunterGuiPage<Integer> first = HunterGuiPagination.page(List.of(1, 2, 3, 4, 5), -4, 2);
        final HunterGuiPage<Integer> last = HunterGuiPagination.page(List.of(1, 2, 3, 4, 5), 99, 2);

        assertEquals(List.of(1, 2), first.items());
        assertEquals(0, first.pageIndex());
        assertEquals(3, first.pageCount());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());

        assertEquals(List.of(5), last.items());
        assertEquals(2, last.pageIndex());
        assertEquals(3, last.pageCount());
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());
    }

    @Test
    void keepsOneEmptyPageAndCopiesTheInputCollection() {
        final List<String> source = new ArrayList<>(List.of("alpha"));
        final HunterGuiPage<String> page = HunterGuiPagination.page(source, 0, 9);
        source.set(0, "changed");

        assertEquals(List.of("alpha"), page.items());
        assertEquals(0, page.pageIndex());
        assertEquals(1, page.pageCount());
        assertEquals(1, page.totalItems());

        final HunterGuiPage<String> empty = HunterGuiPagination.page(List.of(), 42, 9);
        assertTrue(empty.items().isEmpty());
        assertEquals(0, empty.pageIndex());
        assertEquals(1, empty.pageCount());
    }

    @Test
    void rejectsInvalidPageSizes() {
        assertThrows(IllegalArgumentException.class, () -> HunterGuiPagination.page(List.of("a"), 0, 0));
    }
}
