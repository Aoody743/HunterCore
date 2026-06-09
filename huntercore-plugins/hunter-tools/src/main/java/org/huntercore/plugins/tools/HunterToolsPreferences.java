package org.huntercore.plugins.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

final class HunterToolsPreferences {
    private final JavaPlugin plugin;
    private final Path path;
    private final Object lock = new Object();
    private YamlConfiguration config;

    private HunterToolsPreferences(final JavaPlugin plugin, final Path path, final YamlConfiguration config) {
        this.plugin = plugin;
        this.path = path;
        this.config = config;
    }

    static HunterToolsPreferences loadOrCreate(final JavaPlugin plugin) {
        final Path path = Bukkit.getPluginsFolder().toPath().resolve("HunterCore").resolve("preferences.yml");
        final YamlConfiguration config = Files.exists(path) ? YamlConfiguration.loadConfiguration(path.toFile()) : new YamlConfiguration();
        final HunterToolsPreferences preferences = new HunterToolsPreferences(plugin, path, config);
        if (preferences.applyDefaults() || !Files.exists(path)) {
            preferences.saveNow();
        }
        return preferences;
    }

    void reload() {
        synchronized (this.lock) {
            this.config = Files.exists(this.path) ? YamlConfiguration.loadConfiguration(this.path.toFile()) : new YamlConfiguration();
            if (this.applyDefaults()) {
                this.saveNow();
            }
        }
    }

    boolean moduleEnabled(final String module) {
        synchronized (this.lock) {
            return this.config.getBoolean("modules." + normalize(module) + ".enabled", true);
        }
    }

    boolean commandEnabled(final String module, final String command) {
        synchronized (this.lock) {
            return this.config.getBoolean("modules." + normalize(module) + ".commands." + normalize(command), true);
        }
    }

    boolean booleanValue(final String path, final boolean fallback) {
        synchronized (this.lock) {
            return this.config.getBoolean(path, fallback);
        }
    }

    int intValue(final String path, final int fallback) {
        synchronized (this.lock) {
            return this.config.getInt(path, fallback);
        }
    }

    String stringValue(final String path, final String fallback) {
        synchronized (this.lock) {
            return this.config.getString(path, fallback);
        }
    }

    void setModuleEnabled(final String module, final boolean enabled) {
        synchronized (this.lock) {
            this.config.set("modules." + normalize(module) + ".enabled", enabled);
        }
    }

    void setCommandEnabled(final String module, final String command, final boolean enabled) {
        synchronized (this.lock) {
            this.config.set("modules." + normalize(module) + ".commands." + normalize(command), enabled);
        }
    }

    void setSpawn(final Location location) {
        synchronized (this.lock) {
            this.config.set("modules.essentials.spawn.world", location.getWorld().getName());
            this.config.set("modules.essentials.spawn.x", location.getX());
            this.config.set("modules.essentials.spawn.y", location.getY());
            this.config.set("modules.essentials.spawn.z", location.getZ());
            this.config.set("modules.essentials.spawn.yaw", location.getYaw());
            this.config.set("modules.essentials.spawn.pitch", location.getPitch());
        }
    }

    Location spawn() {
        synchronized (this.lock) {
            final String worldName = this.config.getString("modules.essentials.spawn.world", "");
            final World world = worldName == null || worldName.isBlank() ? defaultWorld() : Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }
            if (!this.config.contains("modules.essentials.spawn.x")) {
                return world.getSpawnLocation();
            }
            return new Location(
                world,
                this.config.getDouble("modules.essentials.spawn.x"),
                this.config.getDouble("modules.essentials.spawn.y"),
                this.config.getDouble("modules.essentials.spawn.z"),
                (float) this.config.getDouble("modules.essentials.spawn.yaw"),
                (float) this.config.getDouble("modules.essentials.spawn.pitch")
            );
        }
    }

    CompletableFuture<Void> save(final Executor executor) {
        if (!this.booleanValue("optimizations.hunter-tools.async-save", true)) {
            this.saveNow();
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(this::saveNow, executor);
    }

    void saveNow() {
        synchronized (this.lock) {
            try {
                Files.createDirectories(this.path.getParent());
                final Path temp = Files.createTempFile(this.path.getParent(), "preferences-", ".yml.tmp");
                try {
                    this.config.save(temp.toFile());
                    try {
                        Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (final IOException ex) {
                        Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temp);
                }
            } catch (final IOException ex) {
                this.plugin.getLogger().severe("Failed to save HunterCore preferences: " + ex.getMessage());
            }
        }
    }

    File file() {
        return this.path.toFile();
    }

    private boolean applyDefaults() {
        boolean changed = false;
        changed |= this.setDefault("modules.tps-display.enabled", true);
        changed |= this.setDefault("modules.tps-display.actionbar", true);
        changed |= this.setDefault("modules.tps-display.interval-ticks", 40);
        changed |= this.setDefault("modules.sidebar.enabled", true);
        changed |= this.setDefault("modules.sidebar.interval-ticks", 40);
        changed |= this.setDefault("modules.sidebar.dirty-updates-only", true);
        changed |= this.setDefault("modules.essentials.enabled", true);
        for (final String command : essentialsCommands()) {
            changed |= this.setDefault("modules.essentials.commands." + command, true);
        }
        changed |= this.setDefault("modules.management.enabled", true);
        for (final String command : managementCommands()) {
            changed |= this.setDefault("modules.management.commands." + command, true);
        }
        changed |= this.setDefault("optimizations.enabled", true);
        changed |= this.setDefault("optimizations.hunter-tools.async-rendering", true);
        changed |= this.setDefault("optimizations.hunter-tools.async-save", true);
        changed |= this.setDefault("optimizations.hunter-tools.player-cache", true);
        changed |= this.setDefault("optimizations.hunter-tools.render-workers", Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors())));
        return changed;
    }

    private boolean setDefault(final String path, final Object value) {
        if (this.config.contains(path)) {
            return false;
        }
        this.config.set(path, value);
        return true;
    }

    static List<String> essentialsCommands() {
        return List.of("heal", "feed", "fly", "gm", "day", "night", "sun", "rain", "thunder", "broadcast", "clearchat", "speed", "spawn", "setspawn", "back");
    }

    static List<String> managementCommands() {
        return List.of("reload", "modules", "plugins", "memory", "gc", "threads", "command", "module");
    }

    static String normalize(final String id) {
        return id.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static World defaultWorld() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }
}
