package org.huntercore.plugins.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class HunterAuthWebAccountStore {
    private static final int HASH_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private final Storage storage;
    private final Object fileLock;
    private final Clock clock;
    private final SecureRandom random;

    HunterAuthWebAccountStore(final Path usersPath) {
        this(new YamlStorage(usersPath), usersPath.toAbsolutePath().normalize().toString().intern(), Clock.systemUTC(), new SecureRandom());
    }

    HunterAuthWebAccountStore(
        final Storage storage,
        final Object fileLock,
        final Clock clock,
        final SecureRandom random
    ) {
        this.storage = storage;
        this.fileLock = fileLock;
        this.clock = clock;
        this.random = random;
    }

    Identity authenticate(final String username, final String password) throws IOException {
        if (!replayableGameInput(username) || !replayableGameInput(password)) {
            return null;
        }
        synchronized (this.fileLock) {
            final Document document = this.storage.load();
            final List<HunterAuthWebIdentityResolver.Account> candidates = HunterAuthWebIdentityResolver.loginCandidates(
                username,
                identities(document.accounts())
            );
            if (candidates.isEmpty()) {
                return null;
            }
            final Account account = accountByKey(document.accounts(), candidates.getFirst().key());
            if (account == null || !passwordMatches(account, password)) {
                return null;
            }
            final UUID recordUuid = parseUuid(account.key());
            final UUID identityUuid = parseUuid(account.identityUuid());
            return new Identity(
                account.name().isBlank() ? username : account.name(),
                recordUuid,
                identityUuid == null ? recordUuid : identityUuid,
                account.trusted()
            );
        }
    }

    RegistrationResult register(final String username, final String password) throws IOException {
        if (!replayableGameInput(username) || !replayableGameInput(password)) {
            return RegistrationResult.INVALID;
        }
        final UUID uuid = offlineUuid(username);
        synchronized (this.fileLock) {
            final Document document = this.storage.load();
            for (final Account account : document.accounts()) {
                if (account.key().equalsIgnoreCase(uuid.toString()) || account.name().equalsIgnoreCase(username)) {
                    return RegistrationResult.CONFLICT;
                }
            }
            final byte[] salt = new byte[SALT_BYTES];
            this.random.nextBytes(salt);
            this.storage.append(document, new Account(
                uuid.toString(),
                username,
                uuid.toString(),
                false,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(pbkdf2(password.toCharArray(), salt)),
                "web-panel",
                Instant.now(this.clock).toString()
            ));
            return RegistrationResult.REGISTERED;
        }
    }

    String trustedIdentityUuid(final String username) throws IOException {
        synchronized (this.fileLock) {
            final HunterAuthWebIdentityResolver.Account identity = HunterAuthWebIdentityResolver.trustedSameName(
                username,
                identities(this.storage.load().accounts())
            );
            final UUID uuid = identity == null ? null : parseUuid(identity.identityUuid());
            return uuid == null ? "" : uuid.toString();
        }
    }

    private static List<HunterAuthWebIdentityResolver.Account> identities(final List<Account> accounts) {
        return accounts.stream()
            .map(account -> new HunterAuthWebIdentityResolver.Account(
                account.key(),
                account.name(),
                account.identityUuid(),
                account.trusted()
            ))
            .toList();
    }

    private static Account accountByKey(final List<Account> accounts, final String key) {
        for (final Account account : accounts) {
            if (account.key().equalsIgnoreCase(key)) {
                return account;
            }
        }
        return null;
    }

    private static boolean passwordMatches(final Account account, final String password) {
        try {
            final byte[] salt = Base64.getDecoder().decode(account.salt());
            final byte[] expected = Base64.getDecoder().decode(account.hash());
            if (salt.length != SALT_BYTES || expected.length != HASH_BITS / 8) {
                return false;
            }
            return MessageDigest.isEqual(expected, pbkdf2(password.toCharArray(), salt));
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private static byte[] pbkdf2(final char[] password, final byte[] salt) {
        try {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            final KeySpec spec = new PBEKeySpec(password, salt, HASH_ITERATIONS, HASH_BITS);
            return factory.generateSecret(spec).getEncoded();
        } catch (final Exception ex) {
            throw new IllegalStateException("PBKDF2 is unavailable", ex);
        }
    }

    private static UUID offlineUuid(final String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean replayableGameInput(final String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        // Game commands and the chat fallback split credentials on whitespace. Rejecting every
        // whitespace and ISO control character keeps a web-created password usable unchanged in
        // both game entry points instead of silently creating an unreplayable credential.
        return value.codePoints().noneMatch(codePoint -> Character.isWhitespace(codePoint)
            || Character.isSpaceChar(codePoint)
            || Character.isISOControl(codePoint));
    }

    private static UUID parseUuid(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }

    enum RegistrationResult {
        REGISTERED,
        CONFLICT,
        INVALID
    }

    record Identity(String name, UUID recordUuid, UUID identityUuid, boolean trustedIdentity) {
    }

    record Account(
        String key,
        String name,
        String identityUuid,
        boolean trusted,
        String salt,
        String hash,
        String registeredFrom,
        String registeredAt
    ) {
    }

    record Document(List<Account> accounts, Object source) {
        Document {
            accounts = List.copyOf(accounts);
        }
    }

    interface Storage {
        Document load() throws IOException;

        void append(Document document, Account account) throws IOException;
    }

    private static final class YamlStorage implements Storage {
        private final Path usersPath;

        private YamlStorage(final Path usersPath) {
            this.usersPath = usersPath;
        }

        @Override
        public Document load() {
            final YamlConfiguration users = Files.isRegularFile(this.usersPath)
                ? YamlConfiguration.loadConfiguration(this.usersPath.toFile())
                : new YamlConfiguration();
            final ConfigurationSection section = users.getConfigurationSection("users");
            if (section == null) {
                return new Document(List.of(), users);
            }
            final List<Account> accounts = new ArrayList<>();
            for (final String key : section.getKeys(false)) {
                final String path = "users." + key;
                if (!users.isString(path + ".hash")) {
                    continue;
                }
                accounts.add(new Account(
                    key,
                    users.getString(path + ".name", ""),
                    users.getString(path + ".identity-uuid", key),
                    users.getBoolean(path + ".identity-trusted", false),
                    users.getString(path + ".salt", ""),
                    users.getString(path + ".hash", ""),
                    users.getString(path + ".registered-from", ""),
                    users.getString(path + ".registered-at", "")
                ));
            }
            return new Document(accounts, users);
        }

        @Override
        public void append(final Document document, final Account account) throws IOException {
            final YamlConfiguration users = (YamlConfiguration) document.source();
            final String path = "users." + account.key();
            users.set("schema-version", 2);
            users.set(path + ".name", account.name());
            users.set(path + ".identity-uuid", account.identityUuid());
            users.set(path + ".identity-trusted", account.trusted());
            users.set(path + ".salt", account.salt());
            users.set(path + ".hash", account.hash());
            users.set(path + ".registered-from", account.registeredFrom());
            users.set(path + ".registered-at", account.registeredAt());
            atomicSave(users, this.usersPath);
        }

        private static void atomicSave(final YamlConfiguration users, final Path usersPath) throws IOException {
            final Path parent = usersPath.getParent();
            Files.createDirectories(parent);
            final Path temporary = Files.createTempFile(parent, usersPath.getFileName().toString(), ".tmp");
            try {
                users.save(temporary.toFile());
                if (Files.isRegularFile(usersPath)) {
                    Files.copy(usersPath, usersPath.resolveSibling(usersPath.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                }
                try {
                    Files.move(temporary, usersPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (final IOException ex) {
                    Files.move(temporary, usersPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (final IOException ex) {
                Files.deleteIfExists(temporary);
                throw ex;
            }
        }
    }
}
