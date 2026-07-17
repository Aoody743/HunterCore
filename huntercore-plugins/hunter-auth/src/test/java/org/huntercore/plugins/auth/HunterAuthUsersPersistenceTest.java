package org.huntercore.plugins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class HunterAuthUsersPersistenceTest {
    @Test
    void successfulSaveKeepsTheCommittedState() {
        final Map<String, String> candidate = Map.of("uuid-player", "new-hash");
        final AtomicReference<Map<String, String>> stored = new AtomicReference<>();

        final HunterAuthUsersPersistence.Result<Map<String, String>> result = HunterAuthUsersPersistence.persist(
            candidate,
            stored::get,
            stored::set
        );

        assertTrue(result.saved());
        assertEquals(candidate, result.state());
        assertEquals(candidate, stored.get());
    }

    @Test
    void failedSaveReloadsTheLastPersistedConfiguration() {
        final Map<String, String> persisted = Map.of("legacy-uuid", "persisted-hash");
        final Map<String, String> candidate = new HashMap<>(persisted);
        candidate.remove("legacy-uuid");
        candidate.put("verified-uuid", "unpersisted-hash");

        final HunterAuthUsersPersistence.Result<Map<String, String>> result = HunterAuthUsersPersistence.persist(
            candidate,
            () -> persisted,
            ignored -> {
                throw new IOException("simulated write failure");
            }
        );

        assertFalse(result.saved());
        assertNotNull(result.failure());
        assertEquals(persisted, result.state());
        assertFalse(result.state().containsKey("verified-uuid"));
    }
}
