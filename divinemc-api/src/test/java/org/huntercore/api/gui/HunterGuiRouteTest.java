package org.huntercore.api.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HunterGuiRouteTest {

    @Test
    void snapshotsArgumentsAndBuildsDerivedRoutesWithoutMutatingTheOrigin() {
        final Map<String, String> source = new LinkedHashMap<>();
        source.put("target", "alex");
        final HunterGuiRoute route = HunterGuiRoute.of("hunt-engine:catalogue", source);
        source.put("target", "changed");

        final HunterGuiRoute next = route.withArgument("page", "2");
        assertEquals("alex", route.argument("target").orElseThrow());
        assertFalse(route.argument("page").isPresent());
        assertEquals("alex", next.argument("target").orElseThrow());
        assertEquals("2", next.argument("page").orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> route.arguments().put("other", "value"));
    }

    @Test
    void rejectsUnstableScreenAndArgumentIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> HunterGuiRoute.of("Assets Menu"));
        assertThrows(IllegalArgumentException.class, () -> HunterGuiRoute.of("hunt-engine:catalogue", Map.of("Page Number", "1")));
    }
}
