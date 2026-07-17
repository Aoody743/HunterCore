package org.huntercore.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HunterAssetsMigrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void backsUpAndQuarantinesLegacyJarBeforeHuntEngineCanBeInstalled() throws Exception {
        final Path legacyJar = this.temporaryDirectory.resolve("HunterAssets.jar");
        Files.writeString(legacyJar, "legacy-jar", StandardCharsets.UTF_8);
        final Path legacyData = this.temporaryDirectory.resolve("HunterAssets");
        Files.createDirectories(legacyData);
        Files.writeString(legacyData.resolve("config.yml"), "items: {}\n", StandardCharsets.UTF_8);

        final HunterAssetsMigration.Result result = HunterAssetsMigration.migrateIfRequired(
            this.temporaryDirectory, List.of(huntEngine())
        );

        assertFalse(result.blockHuntEngine());
        assertFalse(Files.exists(legacyJar));
        assertTrue(Files.exists(legacyData.resolve("config.yml")), "legacy data remains untouched for a future importer");
        try (var paths = Files.walk(this.temporaryDirectory.resolve("HunterCore/disabled-legacy-plugins"))) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().equals("HunterAssets.jar")));
        }
        final Path journal = this.temporaryDirectory.resolve("HunterCore/migrations/hunterassets-to-huntengine/journal.yml");
        assertTrue(Files.isRegularFile(journal));
        assertTrue(YamlConfiguration.loadConfiguration(journal.toFile()).getString("state", "").startsWith("completed"));

        final HunterAssetsMigration.Result secondAttempt = HunterAssetsMigration.migrateIfRequired(
            this.temporaryDirectory, List.of(huntEngine())
        );
        assertFalse(secondAttempt.blockHuntEngine(), "the completion marker makes an already-migrated install idempotent");
    }

    @Test
    void keepsLegacyJarAndQuarantinesExistingEngineWhenLegacyDataCannotBeSafelySnapshotted() throws Exception {
        final Path legacyJar = this.temporaryDirectory.resolve("HunterAssets.jar");
        final Path existingEngine = this.temporaryDirectory.resolve("HuntEngine.jar");
        Files.writeString(legacyJar, "legacy-jar", StandardCharsets.UTF_8);
        Files.writeString(existingEngine, "engine-jar", StandardCharsets.UTF_8);
        Files.writeString(this.temporaryDirectory.resolve("HunterAssets"), "not-a-directory", StandardCharsets.UTF_8);

        final HunterAssetsMigration.Result result = HunterAssetsMigration.migrateIfRequired(
            this.temporaryDirectory, List.of(huntEngine())
        );

        assertTrue(result.blockHuntEngine());
        assertTrue(Files.isRegularFile(legacyJar), "a failed migration never removes the legacy plugin");
        assertFalse(Files.exists(existingEngine), "an existing engine jar is quarantined so both plugins cannot load together");
        try (var paths = Files.walk(this.temporaryDirectory.resolve("HunterCore/disabled-pending-migration"))) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().equals("HuntEngine.jar")));
        }
    }

    @Test
    void createsDisabledNativeDraftAndMarksCustomModelItemsForManualWork() throws Exception {
        Files.writeString(this.temporaryDirectory.resolve("HunterAssets.jar"), "legacy-jar", StandardCharsets.UTF_8);
        final Path legacyData = this.temporaryDirectory.resolve("HunterAssets");
        Files.createDirectories(legacyData);
        Files.writeString(legacyData.resolve("config.yml"), """
            resource-pack:
              enabled: true
              url: https://example.invalid/legacy.zip
            items:
              safe_item:
                material: STONE
                name-en-us: '&aSafe Item'
                lore-en-us:
                  - '&7Plain metadata'
              model_item:
                material: PAPER
                custom-model-data: 200001
                pack: HunterCore-default-ui.zip
              invalid_material:
                material: DEFINITELY_NOT_A_MATERIAL
            """, StandardCharsets.UTF_8);

        final HunterAssetsMigration.Result result = HunterAssetsMigration.migrateIfRequired(
            this.temporaryDirectory, List.of(huntEngine())
        );

        assertFalse(result.blockHuntEngine());
        final Path draft = this.temporaryDirectory.resolve("HuntEngine/resources/huntercraft-legacy");
        assertFalse(YamlConfiguration.loadConfiguration(draft.resolve("pack.yml").toFile()).getBoolean("enable", true));
        final YamlConfiguration converted = YamlConfiguration.loadConfiguration(
            draft.resolve("configuration/hunterassets-legacy-items.yml").toFile()
        );
        assertEquals("stone", converted.getString("items.huntercraft_legacy:safe_item.material"));
        final YamlConfiguration journal = YamlConfiguration.loadConfiguration(
            this.temporaryDirectory.resolve("HunterCore/migrations/hunterassets-to-huntengine/journal.yml").toFile()
        );
        assertEquals("completed-with-manual-work", journal.getString("state"));
        assertTrue(journal.getMapList("entries").stream().anyMatch(entry -> "manual_required".equals(entry.get("state"))));
        final YamlConfiguration mappings = YamlConfiguration.loadConfiguration(
            draft.resolve("HUNTERCORE_MIGRATION.yml").toFile()
        );
        assertTrue(
            mappings.getMapList("manual-required").stream()
                .anyMatch(entry -> String.valueOf(entry.get("source")).endsWith("items.invalid_material")),
            "unknown Bukkit materials must be retained for manual review instead of being written into a native item draft"
        );
    }

    private static HunterBundledPluginRecord huntEngine() {
        return new HunterBundledPluginRecord(
            "hunt-engine", "HuntEngine", "test", "HuntEngine.jar", "builtin:test", "test.jar", null
        );
    }
}
