package org.huntercore.plugins.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HunterAuthAccountResolverTest {
    @Test
    void exactUuidBindingCannotBeOverriddenByANameMatch() {
        final UUID joinedUuid = UUID.randomUUID();
        final HunterAuthAccountResolver.Account bound = new HunterAuthAccountResolver.Account(joinedUuid.toString(), "OldName", true);
        final HunterAuthAccountResolver.Account sameName = new HunterAuthAccountResolver.Account(UUID.randomUUID().toString(), "Player", true);

        assertEquals(List.of(bound), HunterAuthAccountResolver.candidates(joinedUuid, "Player", List.of(sameName, bound)));
    }

    @Test
    void webRegisteredOfflineUuidFallsBackByCaseInsensitiveName() {
        final HunterAuthAccountResolver.Account webAccount = new HunterAuthAccountResolver.Account(
            HunterAuthAccountResolver.offlineUuid("WebPlayer").toString(),
            "WebPlayer",
            false
        );

        assertEquals(
            List.of(webAccount),
            HunterAuthAccountResolver.candidates(UUID.randomUUID(), "webplayer", List.of(webAccount))
        );
    }

    @Test
    void unrelatedIdentityHasNoCandidates() {
        final HunterAuthAccountResolver.Account account = new HunterAuthAccountResolver.Account(UUID.randomUUID().toString(), "SomeoneElse", false);
        assertTrue(HunterAuthAccountResolver.candidates(UUID.randomUUID(), "Player", List.of(account)).isEmpty());
    }

    @Test
    void offlineWebRecordIsTriedBeforeLegacyDuplicateRecords() {
        final HunterAuthAccountResolver.Account legacy = new HunterAuthAccountResolver.Account(UUID.randomUUID().toString(), "Player", false);
        final HunterAuthAccountResolver.Account web = new HunterAuthAccountResolver.Account(
            HunterAuthAccountResolver.offlineUuid("Player").toString(),
            "Player",
            false
        );

        assertEquals(
            List.of(web, legacy),
            HunterAuthAccountResolver.candidates(UUID.randomUUID(), "PLAYER", List.of(legacy, web))
        );
    }

    @Test
    void previouslyGameVerifiedRecordWinsOverAnUntrustedWebDuplicate() {
        final HunterAuthAccountResolver.Account web = new HunterAuthAccountResolver.Account(
            HunterAuthAccountResolver.offlineUuid("Player").toString(),
            "Player",
            false
        );
        final HunterAuthAccountResolver.Account verified = new HunterAuthAccountResolver.Account(
            UUID.randomUUID().toString(),
            "Player",
            true
        );

        assertEquals(
            List.of(verified, web),
            HunterAuthAccountResolver.candidates(UUID.randomUUID(), "player", List.of(web, verified))
        );
    }
}
