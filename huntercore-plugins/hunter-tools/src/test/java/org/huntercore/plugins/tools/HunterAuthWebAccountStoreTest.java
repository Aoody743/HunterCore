package org.huntercore.plugins.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HunterAuthWebAccountStoreTest {
    @Test
    void registrationRejectsCaseInsensitiveNameConflict() throws Exception {
        final MemoryStorage storage = new MemoryStorage();
        final HunterAuthWebAccountStore store = store(storage);

        assertEquals(HunterAuthWebAccountStore.RegistrationResult.REGISTERED, store.register("WebPlayer", "correct-password"));
        assertEquals(HunterAuthWebAccountStore.RegistrationResult.CONFLICT, store.register("webplayer", "other-password"));
        assertEquals(1, storage.accounts.size());
        assertFalse(storage.accounts.getFirst().trusted());
    }

    @Test
    void authenticationUsesTheRegisteredHunterAuthPassword() throws Exception {
        final MemoryStorage storage = new MemoryStorage();
        final HunterAuthWebAccountStore store = store(storage);
        store.register("WebPlayer", "correct-password");

        assertNull(store.authenticate("webplayer", "wrong-password"));
        final HunterAuthWebAccountStore.Identity identity = store.authenticate("WEBPLAYER", "correct-password");
        assertNotNull(identity);
        assertEquals("WebPlayer", identity.name());
        assertFalse(identity.trustedIdentity());
    }

    @Test
    void normalPasswordCanBeRegisteredAndAuthenticatedUnchanged() throws Exception {
        final HunterAuthWebAccountStore store = store(new MemoryStorage());
        final String password = "Correct-password_42!";

        assertEquals(HunterAuthWebAccountStore.RegistrationResult.REGISTERED, store.register("WebPlayer", password));
        assertNotNull(store.authenticate("WebPlayer", password));
    }

    @Test
    void registrationAndAuthenticationRejectWhitespaceAndControlPasswords() throws Exception {
        final MemoryStorage storage = new MemoryStorage();
        final HunterAuthWebAccountStore store = store(storage);
        assertEquals(HunterAuthWebAccountStore.RegistrationResult.REGISTERED, store.register("WebPlayer", "correct-password"));

        final String[] invalidPasswords = {
            null,
            "",
            " ",
            " leading",
            "trailing ",
            "contains space",
            "line\rbreak",
            "line\nbreak",
            "tab\tcharacter",
            "null\u0000character",
            "delete\u007Fcharacter",
            "c1\u009Fcharacter"
        };
        for (int index = 0; index < invalidPasswords.length; index++) {
            final String password = invalidPasswords[index];
            assertEquals(HunterAuthWebAccountStore.RegistrationResult.INVALID, store.register("Other" + index, password));
            assertNull(store.authenticate("WebPlayer", password));
        }

        assertEquals(1, storage.accounts.size());
    }

    @Test
    void registrationAndAuthenticationSafelyRejectNullOrBlankUsernames() throws Exception {
        final MemoryStorage storage = new MemoryStorage();
        final HunterAuthWebAccountStore store = store(storage);
        final String[] invalidUsernames = {null, "", " ", "\t"};

        for (final String username : invalidUsernames) {
            assertEquals(HunterAuthWebAccountStore.RegistrationResult.INVALID, store.register(username, "correct-password"));
            assertNull(store.authenticate(username, "correct-password"));
        }

        assertTrue(storage.accounts.isEmpty());
    }

    @Test
    void trustedIdentityLookupIgnoresWebOnlyAccounts() throws Exception {
        final MemoryStorage storage = new MemoryStorage();
        final HunterAuthWebAccountStore store = store(storage);
        store.register("Player", "correct-password");
        assertEquals("", store.trustedIdentityUuid("player"));

        final HunterAuthWebAccountStore.Account registered = storage.accounts.getFirst();
        final String realUuid = UUID.randomUUID().toString();
        storage.accounts.set(0, new HunterAuthWebAccountStore.Account(
            realUuid,
            registered.name(),
            realUuid,
            true,
            registered.salt(),
            registered.hash(),
            "game",
            registered.registeredAt()
        ));

        assertEquals(realUuid, store.trustedIdentityUuid("PLAYER"));
        assertTrue(store.authenticate("Player", "correct-password").trustedIdentity());
    }

    private static HunterAuthWebAccountStore store(final MemoryStorage storage) {
        return new HunterAuthWebAccountStore(
            storage,
            new Object(),
            Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC),
            new java.security.SecureRandom(new byte[] {1, 2, 3, 4})
        );
    }

    private static final class MemoryStorage implements HunterAuthWebAccountStore.Storage {
        private final List<HunterAuthWebAccountStore.Account> accounts = new ArrayList<>();

        @Override
        public HunterAuthWebAccountStore.Document load() {
            return new HunterAuthWebAccountStore.Document(this.accounts, this);
        }

        @Override
        public void append(
            final HunterAuthWebAccountStore.Document document,
            final HunterAuthWebAccountStore.Account account
        ) {
            this.accounts.add(account);
        }
    }
}
