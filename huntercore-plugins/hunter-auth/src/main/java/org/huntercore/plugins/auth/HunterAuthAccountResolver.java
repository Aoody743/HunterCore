package org.huntercore.plugins.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Resolves a shared HunterAuth account without trusting a player name as proof of ownership.
 */
final class HunterAuthAccountResolver {
    private HunterAuthAccountResolver() {
    }

    static List<Account> candidates(
        final UUID playerUuid,
        final String playerName,
        final Collection<Account> accounts
    ) {
        final String uuid = playerUuid.toString();
        for (final Account account : accounts) {
            if (account.key().equalsIgnoreCase(uuid)) {
                // An existing UUID binding always wins; a same-name record must not override it.
                return List.of(account);
            }
        }

        if (playerName == null || playerName.isBlank()) {
            return List.of();
        }
        final String normalizedName = playerName.toLowerCase(Locale.ROOT);
        final List<Account> matches = new ArrayList<>();
        for (final Account account : accounts) {
            if (account.name().toLowerCase(Locale.ROOT).equals(normalizedName)) {
                matches.add(account);
            }
        }
        matches.sort(
            Comparator.comparing((Account account) -> !account.trusted())
                .thenComparing(account -> !isOfflineUuidForStoredName(account))
                .thenComparing(Account::key, String.CASE_INSENSITIVE_ORDER)
        );
        return List.copyOf(matches);
    }

    static UUID offlineUuid(final String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isOfflineUuidForStoredName(final Account account) {
        return account.key().equalsIgnoreCase(offlineUuid(account.name()).toString());
    }

    record Account(String key, String name, boolean trusted) {
        Account {
            key = key == null ? "" : key.trim();
            name = name == null ? "" : name.trim();
        }
    }
}
