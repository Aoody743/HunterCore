package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HunterAuthWebIdentityResolverTest {
    @Test
    void explicitAdminBindingOnlyFindsGameTrustedIdentity() {
        final HunterAuthWebIdentityResolver.Account webOnly = account("Player", false);
        assertNull(HunterAuthWebIdentityResolver.trustedSameName("player", List.of(webOnly)));

        final HunterAuthWebIdentityResolver.Account gameVerified = account("Player", true);
        assertEquals(
            gameVerified,
            HunterAuthWebIdentityResolver.trustedSameName("PLAYER", List.of(webOnly, gameVerified))
        );
    }

    @Test
    void exactAccountKeyCannotBeOverriddenBySameName() {
        final HunterAuthWebIdentityResolver.Account exact = account("OldName", true);
        final HunterAuthWebIdentityResolver.Account sameName = account("Player", true);

        assertEquals(
            List.of(exact),
            HunterAuthWebIdentityResolver.loginCandidates(exact.key(), List.of(sameName, exact))
        );
    }

    @Test
    void deterministicCandidateMakesOnlyOneLegacyPasswordCanonical() {
        final HunterAuthWebIdentityResolver.Account trusted = account("Player", true);
        final HunterAuthWebIdentityResolver.Account untrusted = account("Player", false);

        assertEquals(
            List.of(trusted, untrusted),
            HunterAuthWebIdentityResolver.loginCandidates("player", List.of(untrusted, trusted))
        );
    }

    private static HunterAuthWebIdentityResolver.Account account(final String name, final boolean trusted) {
        final String uuid = UUID.randomUUID().toString();
        return new HunterAuthWebIdentityResolver.Account(uuid, name, uuid, trusted);
    }
}
