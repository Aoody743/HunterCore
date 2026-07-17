package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HunterWebPanelManagerStatusSecurityTest {
    @Test
    void onlyBoundPlayersAndTheServerApiKeyCanReceiveDetailedStatus() {
        assertFalse(WebSessionAccessPolicy.hasDetailedStatusAccess(null, "hunterauth"));
        assertTrue(WebSessionAccessPolicy.hasDetailedStatusAccess(UUID.randomUUID(), "hunterauth-bound"));
        assertTrue(WebSessionAccessPolicy.hasDetailedStatusAccess(null, "api-key"));
    }

    @Test
    void authenticatedStatusResponsesAreNeverSharedThroughTheGuestCache() {
        assertTrue(WebSessionAccessPolicy.mayCacheStatusResponse(false));
        assertFalse(WebSessionAccessPolicy.mayCacheStatusResponse(true));
    }

    @Test
    void onlyBoundIdentitiesCanChatOrRunPlayerCommands() {
        assertFalse(WebSessionAccessPolicy.mayPerformPlayerOperations(null, "hunterauth"));
        assertTrue(WebSessionAccessPolicy.mayPerformPlayerOperations(UUID.randomUUID(), "hunterauth-bound"));
        assertTrue(WebSessionAccessPolicy.mayPerformPlayerOperations(null, "api-key"));
    }

    @Test
    void disabledHunterAuthCannotCreateOrKeepBrowserSessions() {
        assertFalse(WebSessionAccessPolicy.mayStartHunterAuthSession(false, true));
        assertFalse(WebSessionAccessPolicy.mayStartHunterAuthSession(true, false));
        assertTrue(WebSessionAccessPolicy.mayStartHunterAuthSession(true, true));
        assertFalse(WebSessionAccessPolicy.mayUseExistingSession("hunterauth-bound", false));
        assertTrue(WebSessionAccessPolicy.mayUseExistingSession("api-key", false));
    }
}
