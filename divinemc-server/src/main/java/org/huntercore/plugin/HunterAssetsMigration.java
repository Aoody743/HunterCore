package org.huntercore.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Material;

/**
 * One-way, conservative pre-install migration from the legacy HunterAssets plugin.
 *
 * <p>This class never attempts to guess a CraftEngine package schema. It snapshots legacy content
 * for HuntEngine's importer, writes an audit journal, and removes the legacy plugin jar only after
 * every source artifact has been backed up successfully. A failed migration leaves the original
 * files in place and blocks HuntEngine installation for that invocation.</p>
 */
final class HunterAssetsMigration {
    static final String HUNT_ENGINE_ID = "hunt-engine";
    static final String LEGACY_PLUGIN_ID = "hunter-assets";
    static final String LEGACY_PLUGIN_NAME = "HunterAssets";
    static final String HUNT_ENGINE_PLUGIN_NAME = "HuntEngine";
    private static final String MIGRATION_DIRECTORY = "migrations/hunterassets-to-huntengine";
    private static final String JOURNAL_FILE = "journal.yml";
    private static final String COMPLETION_FILE = "completed.yml";
    private static final DateTimeFormatter ATTEMPT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
        .withLocale(Locale.ROOT)
        .withZone(ZoneOffset.UTC);

    private HunterAssetsMigration() {
    }

    static synchronized Result migrateIfRequired(
        final Path pluginDirectory,
        final List<HunterBundledPluginRecord> bundledPlugins
    ) {
        Objects.requireNonNull(pluginDirectory, "pluginDirectory");
        Objects.requireNonNull(bundledPlugins, "bundledPlugins");
        if (bundledPlugins.stream().noneMatch(plugin -> HUNT_ENGINE_ID.equals(plugin.id()))) {
            return Result.notRequired("HuntEngine is not bundled by this server manifest.");
        }

        try {
            Files.createDirectories(pluginDirectory);
            final List<Path> legacyJars = legacyJars(pluginDirectory);
            final Path legacyData = pluginDirectory.resolve(LEGACY_PLUGIN_NAME);
            final boolean legacyDataExists = Files.exists(legacyData, LinkOption.NOFOLLOW_LINKS);
            final Path migrationRoot = pluginDirectory.resolve("HunterCore").resolve(MIGRATION_DIRECTORY);
            final Path journal = migrationRoot.resolve(JOURNAL_FILE);
            final Path completion = migrationRoot.resolve(COMPLETION_FILE);

            if (legacyJars.isEmpty() && Files.isRegularFile(completion, LinkOption.NOFOLLOW_LINKS)) {
                return Result.completed(journal, "HunterAssets migration was already completed.");
            }
            if (legacyJars.isEmpty() && !legacyDataExists) {
                return Result.notRequired("No legacy HunterAssets files were found.");
            }

            Files.createDirectories(migrationRoot);
            final String attempt = ATTEMPT_FORMAT.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
            final Path attemptRoot = migrationRoot.resolve("attempts").resolve(attempt);
            final Path backupRoot = attemptRoot.resolve("backup");
            final Path dataSnapshot = backupRoot.resolve("data").resolve(LEGACY_PLUGIN_NAME);
            final Path jarSnapshotDirectory = backupRoot.resolve("jars");
            final Path draftRoot = attemptRoot.resolve("draft").resolve("huntercraft-legacy");
            final Path huntEngineDraft = pluginDirectory.resolve(HUNT_ENGINE_PLUGIN_NAME).resolve("resources").resolve("huntercraft-legacy");
            final Path quarantineDirectory = pluginDirectory.resolve("HunterCore").resolve("disabled-legacy-plugins")
                .resolve(LEGACY_PLUGIN_ID).resolve(attempt);

            final List<Entry> entries = new ArrayList<>();
            for (final Path jar : legacyJars) {
                entries.add(new Entry(relative(pluginDirectory, jar), null, null, State.DETECTED, "Legacy HunterAssets jar detected."));
            }
            if (legacyDataExists) {
                entries.add(new Entry(relative(pluginDirectory, legacyData), null, null, State.DETECTED, "Legacy HunterAssets data directory detected."));
            }
            writeJournal(journal, attempt, OverallState.RUNNING, entries, "Backing up HunterAssets before installing HuntEngine.");

            for (final Path jar : legacyJars) {
                final Path snapshot = jarSnapshotDirectory.resolve(jar.getFileName().toString());
                final String hash = copyAndHash(jar, snapshot);
                replaceEntry(entries, jar, pluginDirectory, snapshot, hash, State.BACKED_UP, "Legacy jar backed up before isolation.");
            }
            final DraftConversion conversion;
            if (legacyDataExists) {
                final DirectorySnapshot snapshot = snapshotDirectory(legacyData, dataSnapshot);
                replaceEntry(
                    entries,
                    legacyData,
                    pluginDirectory,
                    dataSnapshot,
                    snapshot.directoryHash(),
                    State.PRESERVED,
                    "Legacy data snapshot preserved for HuntEngine import; native conversion is required."
                );
                conversion = createDraft(dataSnapshot, draftRoot, huntEngineDraft);
                entries.addAll(conversion.entries());
            } else {
                conversion = DraftConversion.empty();
            }
            writeJournal(journal, attempt, OverallState.RUNNING, entries, "Legacy artifacts backed up; preparing HunterCraft legacy draft and isolating HunterAssets jars.");

            final List<Move> completedMoves = new ArrayList<>();
            try {
                for (final Path jar : legacyJars) {
                    final Path isolated = quarantineDirectory.resolve(jar.getFileName().toString());
                    move(jar, isolated);
                    completedMoves.add(new Move(jar, isolated));
                    final String hash = sha256(isolated);
                    replaceEntry(entries, jar, pluginDirectory, isolated, hash, State.ISOLATED, "Legacy jar isolated; it will not load with HuntEngine.");
                }
                if (legacyDataExists) {
                    promoteDraft(draftRoot, huntEngineDraft);
                }
                final OverallState finalState = conversion.hasManualWork() ? OverallState.COMPLETED_WITH_MANUAL_WORK : OverallState.COMPLETED;
                final String message = legacyDataExists
                    ? conversion.message()
                    : "Legacy HunterAssets jars isolated; no legacy data directory was present.";
                writeJournal(journal, attempt, finalState, entries, message);
                writeCompletionMarker(completion, attempt, journal, finalState, entries);
                return Result.completed(journal, message);
            } catch (final IOException ex) {
                restoreMoves(completedMoves);
                writeFailureJournalSafely(journal, attempt, entries, "Failed to isolate HunterAssets jars: " + conciseMessage(ex));
                return blockExistingHuntEngine(
                    pluginDirectory,
                    journal,
                    "HunterAssets was preserved because isolation failed: " + conciseMessage(ex)
                );
            }
        } catch (final IOException | RuntimeException ex) {
            final Path fallbackJournal = pluginDirectory.resolve("HunterCore").resolve(MIGRATION_DIRECTORY).resolve(JOURNAL_FILE);
            writeFailureJournalSafely(fallbackJournal, "unavailable", List.of(), "Migration preparation failed: " + conciseMessage(ex));
            return blockExistingHuntEngine(
                pluginDirectory,
                fallbackJournal,
                "HunterAssets was preserved because migration failed: " + conciseMessage(ex)
            );
        }
    }

    private static List<Path> legacyJars(final Path pluginDirectory) throws IOException {
        try (var paths = Files.list(pluginDirectory)) {
            return paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> {
                    final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".jar") && (name.equals("hunterassets.jar") || name.startsWith("hunterassets-"));
                })
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    /**
     * A failed migration must not let an already-installed HuntEngine jar load beside HunterAssets.
     * The engine jar is copied and quarantined, never deleted. If the filesystem prevents this
     * final safeguard, the returned message is intentionally explicit so the server operator can
     * stop before plugin scanning rather than silently risking a concurrent load.
     */
    private static Result blockExistingHuntEngine(final Path pluginDirectory, final Path journal, final String failureMessage) {
        try {
            final List<Path> engineJars = huntEngineJars(pluginDirectory);
            if (engineJars.isEmpty()) {
                return Result.failed(journal, failureMessage);
            }
            final String attempt = ATTEMPT_FORMAT.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 8);
            final Path backupDirectory = journal.getParent().resolve("blocked-engine-backups").resolve(attempt);
            final Path quarantineDirectory = pluginDirectory.resolve("HunterCore").resolve("disabled-pending-migration")
                .resolve(HUNT_ENGINE_ID).resolve(attempt);
            for (final Path engineJar : engineJars) {
                copyAndHash(engineJar, backupDirectory.resolve(engineJar.getFileName().toString()));
            }
            for (final Path engineJar : engineJars) {
                move(engineJar, quarantineDirectory.resolve(engineJar.getFileName().toString()));
            }
            return Result.failed(
                journal,
                failureMessage + " Existing HuntEngine jars were safely quarantined to prevent concurrent loading."
            );
        } catch (final IOException ex) {
            return Result.failed(
                journal,
                failureMessage + " Could not quarantine an existing HuntEngine jar; do not start plugin scanning until it is moved: " + conciseMessage(ex)
            );
        }
    }

    private static List<Path> huntEngineJars(final Path pluginDirectory) throws IOException {
        try (var paths = Files.list(pluginDirectory)) {
            return paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> {
                    final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".jar") && (name.equals("huntengine.jar") || name.startsWith("huntengine-"));
                })
                .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    /**
     * Converts only plain, model-free legacy items into a disabled native content package. Every
     * custom-model-data, external pack, image, duplicate, or malformed item stays preserved in the
     * snapshot and receives an explicit manual-required journal entry instead of a guessed model.
     */
    private static DraftConversion createDraft(final Path snapshot, final Path draftRoot, final Path finalDraft) throws IOException {
        deleteDirectoryIfExists(draftRoot);
        Files.createDirectories(draftRoot.resolve("configuration"));
        final List<Entry> entries = new ArrayList<>();
        final YamlConfiguration items = new YamlConfiguration();
        final YamlConfiguration mappings = new YamlConfiguration();
        final List<java.util.Map<String, Object>> manual = new ArrayList<>();
        int converted = 0;

        final Path config = snapshot.resolve("config.yml");
        if (Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS)) {
            final YamlConfiguration legacy = YamlConfiguration.loadConfiguration(config.toFile());
            final ConfigurationSection itemSection = legacy.getConfigurationSection("items");
            if (itemSection != null) {
                for (final String id : itemSection.getKeys(false)) {
                    final ConfigurationSection item = itemSection.getConfigurationSection(id);
                    if (item != null && convertItem(item, id, "HunterAssets/config.yml#items." + id, items, mappings, entries, manual)) {
                        converted++;
                    }
                }
            }
            writeResourcePackMetadata(legacy, mappings);
        }

        final Path standaloneItems = snapshot.resolve("items");
        if (Files.isDirectory(standaloneItems, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.list(standaloneItems)) {
                for (final Path path : paths.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted(Comparator.comparing(file -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList()) {
                    final YamlConfiguration itemFile = YamlConfiguration.loadConfiguration(path.toFile());
                    final String id = itemFile.getString("id", path.getFileName().toString().replaceFirst("(?i)\\.yml$", ""));
                    if (convertItem(itemFile, id, "HunterAssets/items/" + path.getFileName(), items, mappings, entries, manual)) {
                        converted++;
                    }
                }
            }
        }

        appendManualDirectoryEntry(snapshot.resolve("packs"), "HunterAssets/packs", "Legacy resource-pack archives require manual native-pack import.", entries, manual);
        appendManualDirectoryEntry(snapshot.resolve("images"), "HunterAssets/images", "Legacy image assets require manual native-pack placement.", entries, manual);

        final YamlConfiguration pack = new YamlConfiguration();
        pack.set("author", "HunterCore migration");
        pack.set("version", 1);
        pack.set("description", "HunterAssets migration draft. Review and enable only after manual validation.");
        pack.set("namespace", "huntercraft_legacy");
        pack.set("enable", false);
        writeYamlAtomically(pack, draftRoot.resolve("pack.yml"));

        if (converted > 0) {
            writeYamlAtomically(items, draftRoot.resolve("configuration").resolve("hunterassets-legacy-items.yml"));
        }
        mappings.set("migration", "hunterassets-to-huntengine");
        mappings.set("source-snapshot", snapshot.toString());
        mappings.set("final-draft", finalDraft.toString());
        mappings.set("converted-item-count", converted);
        mappings.set("manual-required", manual);
        writeYamlAtomically(mappings, draftRoot.resolve("HUNTERCORE_MIGRATION.yml"));

        final String message = entries.stream().anyMatch(entry -> entry.state() == State.MANUAL_REQUIRED)
            ? "Legacy jars isolated. A disabled HuntEngine draft was created at " + finalDraft + "; review manual-required entries before enabling it."
            : "Legacy jars isolated. A disabled HuntEngine draft with " + converted + " safely converted item(s) was created at " + finalDraft + ".";
        return new DraftConversion(entries, message);
    }

    private static boolean convertItem(
        final ConfigurationSection legacy,
        final String rawId,
        final String source,
        final YamlConfiguration items,
        final YamlConfiguration mappings,
        final List<Entry> entries,
        final List<java.util.Map<String, Object>> manual
    ) {
        final String id = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        final String material = legacy.getString("material", "").trim().toLowerCase(Locale.ROOT);
        final String pack = legacy.getString("pack", "").trim();
        final String icon = legacy.getString("icon", "").trim();
        final int customModelData = Math.max(0, legacy.getInt("custom-model-data", 0));
        if (!stableLegacyId(id) || !material.matches("[a-z0-9_]+")) {
            appendManualEntry(entries, manual, source, "Invalid item id or Bukkit material; no native item was guessed.");
            return false;
        }
        final Material bukkitMaterial = Material.matchMaterial(material);
        // Do not call Material#isItem here. In Paper 26.2 that lazily resolves the live item
        // registry, which is intentionally unavailable while this conservative migration is
        // unit-tested before a server has completed registry bootstrap. A known enum value is
        // sufficient for this disabled draft; the native HuntEngine validation remains the final
        // authority before it can be built or published.
        if (bukkitMaterial == null || bukkitMaterial == Material.AIR) {
            appendManualEntry(entries, manual, source, "Unknown or air Bukkit material; no native item was guessed.");
            return false;
        }
        if (customModelData > 0 || !pack.isBlank() || !icon.isBlank()) {
            appendManualEntry(
                entries,
                manual,
                source,
                "Custom model data, pack, or icon metadata requires a manual HuntEngine model conversion."
            );
            return false;
        }
        final String targetKey = "items.huntercraft_legacy:" + id;
        if (items.contains(targetKey)) {
            appendManualEntry(entries, manual, source, "Duplicate legacy item id; manual merge is required.");
            return false;
        }
        items.set(targetKey + ".material", material);
        final String name = plainText(legacy.getString("name-en-us", legacy.getString("name-zh-cn", "")));
        if (!name.isBlank()) {
            items.set(targetKey + ".data.item_name", name);
        }
        final List<String> lore = legacy.getStringList("lore-en-us").isEmpty()
            ? legacy.getStringList("lore-zh-cn")
            : legacy.getStringList("lore-en-us");
        final List<String> plainLore = lore.stream().map(HunterAssetsMigration::plainText).filter(line -> !line.isBlank()).toList();
        if (!plainLore.isEmpty()) {
            items.set(targetKey + ".data.lore", plainLore);
        }
        mappings.set("converted." + id + ".legacy-amount", Math.max(1, legacy.getInt("amount", 1)));
        mappings.set("converted." + id + ".legacy-permission", legacy.getString("permission", ""));
        mappings.set("converted." + id + ".legacy-category", legacy.getString("category", "items"));
        entries.add(new Entry(
            source,
            "HuntEngine/resources/huntercraft-legacy/configuration/hunterassets-legacy-items.yml#huntercraft_legacy:" + id,
            null,
            State.DRAFTED,
            "Safely converted plain material, name, and lore into a disabled native draft item."
        ));
        return true;
    }

    private static void writeResourcePackMetadata(final YamlConfiguration legacy, final YamlConfiguration mappings) {
        mappings.set("resource-pack.enabled", legacy.getBoolean("resource-pack.enabled", false));
        mappings.set("resource-pack.url", legacy.getString("resource-pack.url", ""));
        mappings.set("resource-pack.sha1", legacy.getString("resource-pack.sha1", ""));
        mappings.set("resource-pack.required", legacy.getBoolean("resource-pack.required", false));
        mappings.set("resource-pack.send-on-join", legacy.getBoolean("resource-pack.send-on-join", false));
        mappings.set("resource-pack.prompt-zh-cn", legacy.getString("resource-pack.prompt-zh-cn", ""));
        mappings.set("resource-pack.prompt-en-us", legacy.getString("resource-pack.prompt-en-us", ""));
    }

    private static void appendManualDirectoryEntry(
        final Path directory,
        final String source,
        final String message,
        final List<Entry> entries,
        final List<java.util.Map<String, Object>> manual
    ) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            if (paths.findAny().isPresent()) {
                appendManualEntry(entries, manual, source, message);
            }
        }
    }

    private static void appendManualEntry(
        final List<Entry> entries,
        final List<java.util.Map<String, Object>> manual,
        final String source,
        final String message
    ) {
        entries.add(new Entry(source, null, null, State.MANUAL_REQUIRED, message));
        final java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("source", source);
        item.put("message", message);
        manual.add(item);
    }

    private static void promoteDraft(final Path draftRoot, final Path finalDraft) throws IOException {
        if (Files.exists(finalDraft, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("refusing to overwrite existing HuntEngine migration draft: " + finalDraft);
        }
        Files.createDirectories(finalDraft.getParent());
        move(draftRoot, finalDraft);
    }

    private static void deleteDirectoryIfExists(final Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (final Path child : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(child);
            }
        }
    }

    private static boolean stableLegacyId(final String id) {
        return id.matches("[a-z0-9_.-]+");
    }

    private static String plainText(final String source) {
        if (source == null) {
            return "";
        }
        return source
            .replaceAll("(?i)&[0-9a-fk-or]", "")
            .replace("<", "")
            .replace(">", "")
            .trim();
    }

    private static DirectorySnapshot snapshotDirectory(final Path source, final Path destination) throws IOException {
        if (Files.isSymbolicLink(source)) {
            throw new IOException("refusing to snapshot a symbolic link: " + source);
        }
        final MessageDigest digest = newDigest();
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("legacy HunterAssets data path is not a directory: " + source);
        }
        try (var paths = Files.walk(source)) {
            for (final Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("refusing to snapshot a symbolic link: " + path);
                }
                final Path relative = source.relativize(path);
                final Path target = destination.resolve(relative).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("unsafe legacy data path: " + relative);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(target);
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("unsupported legacy data entry: " + path);
                }
                final String fileHash = copyAndHash(path, target);
                updateDigest(digest, relative.toString());
                updateDigest(digest, fileHash);
            }
        }
        return new DirectorySnapshot(HexFormat.of().formatHex(digest.digest()));
    }

    private static String copyAndHash(final Path source, final Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        final MessageDigest digest = newDigest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(source), digest);
             OutputStream output = Files.newOutputStream(destination)) {
            input.transferTo(output);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(final Path path) throws IOException {
        final MessageDigest digest = newDigest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static void updateDigest(final MessageDigest digest, final String value) {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static void move(final Path source, final Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException ex) {
            Files.move(source, destination);
        }
    }

    private static void restoreMoves(final List<Move> moves) {
        for (final Move move : moves.reversed()) {
            try {
                if (Files.exists(move.target(), LinkOption.NOFOLLOW_LINKS) && !Files.exists(move.source(), LinkOption.NOFOLLOW_LINKS)) {
                    move(move.target(), move.source());
                }
            } catch (final IOException ignored) {
                // The original copy is always retained in the migration backup. The journal tells the operator exactly where it is.
            }
        }
    }

    private static void replaceEntry(
        final List<Entry> entries,
        final Path source,
        final Path pluginDirectory,
        final Path target,
        final String sha256,
        final State state,
        final String message
    ) {
        final String sourceText = relative(pluginDirectory, source);
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).source().equals(sourceText)) {
                entries.set(index, new Entry(sourceText, target.toString(), sha256, state, message));
                return;
            }
        }
        entries.add(new Entry(sourceText, target.toString(), sha256, state, message));
    }

    private static String relative(final Path pluginDirectory, final Path path) {
        return pluginDirectory.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static void writeJournal(
        final Path journal,
        final String attempt,
        final OverallState state,
        final List<Entry> entries,
        final String message
    ) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("migration", "hunterassets-to-huntengine");
        yaml.set("attempt", attempt);
        yaml.set("state", state.key());
        yaml.set("updated-at", Instant.now().toString());
        yaml.set("message", message);
        final List<java.util.Map<String, Object>> serializedEntries = new ArrayList<>();
        for (final Entry entry : entries) {
            final java.util.Map<String, Object> serialized = new java.util.LinkedHashMap<>();
            serialized.put("source", entry.source());
            serialized.put("target", entry.target());
            serialized.put("sha256", entry.sha256());
            serialized.put("state", entry.state().key());
            serialized.put("message", entry.message());
            serializedEntries.add(serialized);
        }
        yaml.set("entries", serializedEntries);
        writeYamlAtomically(yaml, journal);
    }

    private static void writeCompletionMarker(
        final Path completion,
        final String attempt,
        final Path journal,
        final OverallState state,
        final List<Entry> entries
    ) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("migration", "hunterassets-to-huntengine");
        yaml.set("attempt", attempt);
        yaml.set("state", state.key());
        yaml.set("completed-at", Instant.now().toString());
        yaml.set("journal", journal.toString());
        yaml.set("entry-count", entries.size());
        writeYamlAtomically(yaml, completion);
    }

    private static void writeFailureJournalSafely(
        final Path journal,
        final String attempt,
        final List<Entry> entries,
        final String message
    ) {
        try {
            Files.createDirectories(journal.getParent());
            writeJournal(journal, attempt, OverallState.FAILED, entries, message);
        } catch (final IOException ignored) {
            // No source artifact is deleted before the journal is available. A filesystem failure here still preserves HunterAssets.
        }
    }

    private static void writeYamlAtomically(final YamlConfiguration yaml, final Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        final Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
        try {
            yaml.save(temporary.toFile());
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String conciseMessage(final Exception exception) {
        final String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    static final class Result {
        private final boolean blockHuntEngine;
        private final Path journal;
        private final String message;

        private Result(final boolean blockHuntEngine, final Path journal, final String message) {
            this.blockHuntEngine = blockHuntEngine;
            this.journal = journal;
            this.message = message;
        }

        static Result notRequired(final String message) {
            return new Result(false, null, message);
        }

        static Result completed(final Path journal, final String message) {
            return new Result(false, journal, message);
        }

        static Result failed(final Path journal, final String message) {
            return new Result(true, journal, message);
        }

        boolean blockHuntEngine() {
            return this.blockHuntEngine;
        }

        Path journal() {
            return this.journal;
        }

        String message() {
            return this.message;
        }
    }

    private record Move(Path source, Path target) {
    }

    private record DirectorySnapshot(String directoryHash) {
    }

    private record DraftConversion(List<Entry> entries, String message) {
        private DraftConversion {
            entries = List.copyOf(entries);
        }

        private static DraftConversion empty() {
            return new DraftConversion(List.of(), "Legacy HunterAssets jars isolated; no legacy data directory was present.");
        }

        private boolean hasManualWork() {
            return this.entries.stream().anyMatch(entry -> entry.state() == State.MANUAL_REQUIRED);
        }
    }

    private record Entry(String source, String target, String sha256, State state, String message) {
    }

    private enum State {
        DETECTED,
        BACKED_UP,
        ISOLATED,
        PRESERVED,
        DRAFTED,
        MANUAL_REQUIRED;

        private String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private enum OverallState {
        RUNNING,
        COMPLETED,
        COMPLETED_WITH_MANUAL_WORK,
        FAILED;

        private String key() {
            return this.name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }
}
