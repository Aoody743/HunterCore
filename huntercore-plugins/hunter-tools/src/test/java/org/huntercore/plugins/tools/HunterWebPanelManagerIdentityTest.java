package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HunterWebPanelManagerIdentityTest {
    @Test
    void trustedIdentityBindingWinsOverTheLoginName() {
        final UUID identityUuid = UUID.randomUUID();
        final HunterAuthWebAccountStore.Identity identity = identity("RenamedPlayer", identityUuid, true);

        assertTrue(WebIdentityAuthorization.matchesTrustedIdentity(identityUuid.toString(), identity));
        assertFalse(WebIdentityAuthorization.matchesTrustedIdentity(UUID.randomUUID().toString(), identity));
        assertEquals(identityUuid, WebIdentityAuthorization.trustedSessionPlayerUuid(identity));
    }

    @Test
    void trustedIdentityRequiresAnExactUuidBindingForWebRoles() {
        final UUID identityUuid = UUID.randomUUID();
        final HunterAuthWebAccountStore.Identity identity = identity("Player", identityUuid, true);

        assertFalse(WebIdentityAuthorization.matchesTrustedIdentity("", identity));
        assertFalse(WebIdentityAuthorization.matchesTrustedIdentity(UUID.randomUUID().toString(), identity));
    }

    @Test
    void untrustedWebRegistrationCannotReceiveWebRolesOrAPlayerBinding() {
        final UUID webOnlyUuid = UUID.randomUUID();
        final HunterAuthWebAccountStore.Identity webOnly = identity("Player", webOnlyUuid, false);

        assertFalse(WebIdentityAuthorization.matchesTrustedIdentity(webOnlyUuid.toString(), webOnly));
        assertNull(WebIdentityAuthorization.trustedSessionPlayerUuid(webOnly));
    }

    @Test
    void trustedIdentityUsesItsBoundUuidForPlayerData() {
        final HunterAuthWebAccountStore.Identity trusted = identity("Player", UUID.randomUUID(), true);

        assertEquals(trusted.identityUuid(), WebIdentityAuthorization.trustedSessionPlayerUuid(trusted));
    }

    private static HunterAuthWebAccountStore.Identity identity(
        final String name,
        final UUID identityUuid,
        final boolean trusted
    ) {
        return new HunterAuthWebAccountStore.Identity(name, UUID.randomUUID(), identityUuid, trusted);
    }
}
