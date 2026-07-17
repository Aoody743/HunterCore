package org.huntercore.plugins.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Limits online-mode account claims to web records that have not yet been trusted with a player
 * identity. A Mojang-authenticated join proves the current UUID, not ownership of an arbitrary
 * same-name password record.
 */
final class HunterAuthOnlineBindingPolicy {
    private HunterAuthOnlineBindingPolicy() {
    }

    static List<Account> pendingWebAccounts(
        final UUID playerUuid,
        final String playerName,
        final Collection<Account> accounts
    ) {
        if (playerUuid == null || playerName == null || playerName.isBlank()) {
            return List.of();
        }
        final List<Account> pending = new ArrayList<>();
        for (final Account account : accounts) {
            if (isPendingWebAccount(playerUuid, playerName, account)) {
                pending.add(account);
            }
        }
        return List.copyOf(pending);
    }

    static boolean hasConflictingTrustedName(
        final UUID playerUuid,
        final String playerName,
        final Collection<Account> accounts
    ) {
        if (playerUuid == null || playerName == null || playerName.isBlank()) {
            return true;
        }
        final String playerKey = playerUuid.toString();
        for (final Account account : accounts) {
            if (account.trusted()
                && !account.key().equalsIgnoreCase(playerKey)
                && account.name().equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A password rotation in online mode may only touch the UUID record that was already
     * claimed by the Mojang-authenticated player. Name matches are intentionally ignored.
     */
    static Account currentTrustedAccount(final UUID playerUuid, final Collection<Account> accounts) {
        if (playerUuid == null || accounts == null) {
            return null;
        }
        final String playerKey = playerUuid.toString();
        for (final Account account : accounts) {
            if (account.trusted() && account.key().equalsIgnoreCase(playerKey)) {
                return account;
            }
        }
        return null;
    }

    private static boolean isPendingWebAccount(final UUID playerUuid, final String playerName, final Account account) {
        return !account.trusted()
            && account.registeredFrom().equals("web-panel")
            && account.name().equalsIgnoreCase(playerName)
            && account.key().equalsIgnoreCase(HunterAuthAccountResolver.offlineUuid(account.name()).toString())
            && !account.key().equalsIgnoreCase(playerUuid.toString());
    }

    record Account(String key, String name, boolean trusted, String registeredFrom) {
        Account {
            key = key == null ? "" : key.trim();
            name = name == null ? "" : name.trim();
            registeredFrom = registeredFrom == null ? "" : registeredFrom.trim().toLowerCase(Locale.ROOT);
        }
    }
}
