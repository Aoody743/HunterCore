package org.huntercore.plugins.auth;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

final class HunterAuthSkinManager {
    private final JavaPlugin plugin;
    private final AtomicLong generations = new AtomicLong();
    private final ConcurrentHashMap<UUID, Long> pending = new ConcurrentHashMap<>();

    HunterAuthSkinManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void applyAfterAuthentication(final Player player) {
        if (!this.plugin.getConfig().getBoolean("apply-skin-on-login", true)) {
            return;
        }
        final Plugin skinsRestorer = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
        if (skinsRestorer == null || !skinsRestorer.isEnabled()) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        final String playerName = player.getName();
        final boolean onlineAuthenticated = org.huntercore.api.HunterCoreProvider.get().connections().onlineAuthenticated(player);
        final long generation = this.generations.incrementAndGet();
        this.pending.put(playerId, generation);
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                final Object api = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider")
                    .getMethod("get")
                    .invoke(null);
                Object property = this.resolveOfficialProperty(api, playerId, playerName, onlineAuthenticated);
                if (property == null) {
                    property = this.resolveSteveProperty(api);
                }
                if (property == null) {
                    return;
                }
                final Object resolvedProperty = property;
                Bukkit.getScheduler().runTask(this.plugin, () -> this.applyIfCurrent(playerId, generation, api, resolvedProperty));
            } catch (final Throwable ex) {
                this.plugin.getLogger().fine("SkinsRestorer could not resolve a skin for " + playerName + ": " + ex.getMessage());
            }
        });
    }

    void cancel(final UUID playerId) {
        this.pending.remove(playerId);
    }

    void clear() {
        this.pending.clear();
    }

    private Object resolveOfficialProperty(
        final Object api,
        final UUID playerId,
        final String playerName,
        final boolean onlineAuthenticated
    ) throws Exception {
        final Object mojang = api.getClass().getMethod("getMojangAPI").invoke(api);
        if (onlineAuthenticated) {
            final Optional<?> profile = optional(invoke(mojang, "getProfile", new Class<?>[] {UUID.class}, playerId));
            if (profile.isPresent()) {
                return profile.get();
            }
        }
        final Optional<?> skin = optional(invoke(mojang, "getSkin", new Class<?>[] {String.class}, playerName));
        if (skin.isEmpty()) {
            return null;
        }
        return skin.get().getClass().getMethod("getSkinProperty").invoke(skin.get());
    }

    private Object resolveSteveProperty(final Object api) throws Exception {
        final Object storage = api.getClass().getMethod("getSkinStorage").invoke(api);
        final Optional<?> result = optional(invoke(storage, "findSkinData", new Class<?>[] {String.class}, "steve"));
        return result.isEmpty() ? null : result.get().getClass().getMethod("getProperty").invoke(result.get());
    }

    private void applyIfCurrent(final UUID playerId, final long generation, final Object api, final Object property) {
        if (!Long.valueOf(generation).equals(this.pending.get(playerId))) {
            return;
        }
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            this.pending.remove(playerId, generation);
            return;
        }
        try {
            final Object applier = api.getClass().getMethod("getSkinApplier", Class.class).invoke(api, Player.class);
            Method apply = null;
            for (final Method method : applier.getClass().getMethods()) {
                if (method.getName().equals("applySkin") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isAssignableFrom(player.getClass())
                    && method.getParameterTypes()[1].isAssignableFrom(property.getClass())) {
                    apply = method;
                    break;
                }
            }
            if (apply != null) {
                apply.invoke(applier, player, property);
            }
        } catch (final Throwable ex) {
            this.plugin.getLogger().fine("SkinsRestorer could not apply a skin for " + player.getName() + ": " + ex.getMessage());
        } finally {
            this.pending.remove(playerId, generation);
        }
    }

    private static Object invoke(final Object target, final String method, final Class<?>[] types, final Object... args) throws Exception {
        return target.getClass().getMethod(method, types).invoke(target, args);
    }

    private static Optional<?> optional(final Object value) {
        return value instanceof final Optional<?> optional ? optional : Optional.empty();
    }
}
