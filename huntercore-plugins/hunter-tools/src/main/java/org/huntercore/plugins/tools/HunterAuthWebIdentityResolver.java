package org.huntercore.plugins.tools;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class HunterAuthWebIdentityResolver {
    private HunterAuthWebIdentityResolver() {
    }

    static List<Account> loginCandidates(final String login, final Collection<Account> accounts) {
        if (login == null || login.isBlank()) {
            return List.of();
        }
        for (final Account account : accounts) {
            if (account.key().equalsIgnoreCase(login)) {
                return List.of(account);
            }
        }
        final String normalized = login.toLowerCase(Locale.ROOT);
        final List<Account> matches = new ArrayList<>();
        for (final Account account : accounts) {
            if (account.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                matches.add(account);
            }
        }
        matches.sort(accountOrder());
        return List.copyOf(matches);
    }

    static Account trustedSameName(final String username, final Collection<Account> accounts) {
        if (username == null || username.isBlank()) {
            return null;
        }
        final String normalized = username.toLowerCase(Locale.ROOT);
        return accounts.stream()
            .filter(Account::trusted)
            .filter(account -> account.name().toLowerCase(Locale.ROOT).equals(normalized))
            .sorted(accountOrder())
            .findFirst()
            .orElse(null);
    }

    private static Comparator<Account> accountOrder() {
        return Comparator.comparing((Account account) -> !account.trusted())
            .thenComparing(account -> !account.key().equalsIgnoreCase(offlineUuid(account.name()).toString()))
            .thenComparing(Account::key, String.CASE_INSENSITIVE_ORDER);
    }

    private static UUID offlineUuid(final String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    record Account(String key, String name, String identityUuid, boolean trusted) {
        Account {
            key = key == null ? "" : key.trim();
            name = name == null ? "" : name.trim();
            identityUuid = identityUuid == null ? "" : identityUuid.trim();
        }
    }
}
