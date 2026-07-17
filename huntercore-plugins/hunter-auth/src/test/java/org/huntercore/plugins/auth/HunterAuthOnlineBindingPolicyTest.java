package org.huntercore.plugins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HunterAuthOnlineBindingPolicyTest {
    @Test
    void onlyANameMatchedUntrustedWebRecordCanBeClaimed() {
        final UUID onlineUuid = UUID.randomUUID();
        final HunterAuthOnlineBindingPolicy.Account pending = pending("Player");
        final HunterAuthOnlineBindingPolicy.Account trusted = new HunterAuthOnlineBindingPolicy.Account(
            UUID.randomUUID().toString(), "Player", true, "game"
        );
        final HunterAuthOnlineBindingPolicy.Account legacy = new HunterAuthOnlineBindingPolicy.Account(
            UUID.randomUUID().toString(), "Player", false, "legacy"
        );

        assertEquals(List.of(pending), HunterAuthOnlineBindingPolicy.pendingWebAccounts(
            onlineUuid, "player", List.of(pending, trusted, legacy)
        ));
    }

    @Test
    void currentPlayerCanNeverClaimAnotherUuidTrustedRecord() {
        final UUID onlineUuid = UUID.randomUUID();
        final HunterAuthOnlineBindingPolicy.Account trusted = new HunterAuthOnlineBindingPolicy.Account(
            UUID.randomUUID().toString(), "Player", true, "game"
        );

        assertTrue(HunterAuthOnlineBindingPolicy.hasConflictingTrustedName(onlineUuid, "Player", List.of(trusted)));
        assertTrue(HunterAuthOnlineBindingPolicy.pendingWebAccounts(onlineUuid, "Player", List.of(trusted)).isEmpty());
    }

    @Test
    void malformedOrForeignWebRecordsCannotBeClaimed() {
        final UUID onlineUuid = UUID.randomUUID();
        final HunterAuthOnlineBindingPolicy.Account forged = new HunterAuthOnlineBindingPolicy.Account(
            UUID.randomUUID().toString(), "Player", false, "web-panel"
        );
        final HunterAuthOnlineBindingPolicy.Account differentName = pending("Other");

        assertTrue(HunterAuthOnlineBindingPolicy.pendingWebAccounts(
            onlineUuid, "Player", List.of(forged, differentName)
        ).isEmpty());
        assertFalse(HunterAuthOnlineBindingPolicy.hasConflictingTrustedName(onlineUuid, "Player", List.of(forged)));
    }

    @Test
    void unclaimedPlayerCannotRotateANameMatchedPendingOrTrustedAccount() {
        final UUID onlineUuid = UUID.randomUUID();
        final HunterAuthOnlineBindingPolicy.Account pending = pending("Player");
        final HunterAuthOnlineBindingPolicy.Account trusted = new HunterAuthOnlineBindingPolicy.Account(
            UUID.randomUUID().toString(), "Player", true, "game"
        );

        assertNull(HunterAuthOnlineBindingPolicy.currentTrustedAccount(onlineUuid, List.of(pending, trusted)));
    }

    @Test
    void passwordRotationSelectsOnlyTheClaimedCurrentUuidRecord() {
        final UUID onlineUuid = UUID.randomUUID();
        final HunterAuthOnlineBindingPolicy.Account current = new HunterAuthOnlineBindingPolicy.Account(
            onlineUuid.toString(), "Player", true, "game"
        );
        final HunterAuthOnlineBindingPolicy.Account pending = pending("Player");
        final HunterAuthOnlineBindingPolicy.Account otherTrusted = new HunterAuthOnlineBindingPolicy.Account(
            UUID.randomUUID().toString(), "Player", true, "game"
        );

        assertEquals(
            current,
            HunterAuthOnlineBindingPolicy.currentTrustedAccount(onlineUuid, List.of(pending, otherTrusted, current))
        );
    }

    private static HunterAuthOnlineBindingPolicy.Account pending(final String name) {
        return new HunterAuthOnlineBindingPolicy.Account(
            HunterAuthAccountResolver.offlineUuid(name).toString(), name, false, "web-panel"
        );
    }
}
