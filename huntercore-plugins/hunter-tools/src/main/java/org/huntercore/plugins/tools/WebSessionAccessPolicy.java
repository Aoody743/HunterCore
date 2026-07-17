package org.huntercore.plugins.tools;

import java.util.UUID;

final class WebSessionAccessPolicy {
    private WebSessionAccessPolicy() {
    }

    static boolean hasTrustedGameIdentity(final UUID playerUuid, final String authSource) {
        return playerUuid != null || "api-key".equals(authSource);
    }

    static boolean hasDetailedStatusAccess(final UUID playerUuid, final String authSource) {
        return hasTrustedGameIdentity(playerUuid, authSource);
    }

    static boolean mayPerformPlayerOperations(final UUID playerUuid, final String authSource) {
        return hasTrustedGameIdentity(playerUuid, authSource);
    }

    static boolean mayCacheStatusResponse(final boolean authenticated) {
        return !authenticated;
    }

    static boolean mayStartHunterAuthSession(final boolean authEnabled, final boolean webLoginEnabled) {
        return authEnabled && webLoginEnabled;
    }

    static boolean mayUseExistingSession(final String authSource, final boolean authEnabled) {
        return "api-key".equals(authSource) || authEnabled;
    }
}
