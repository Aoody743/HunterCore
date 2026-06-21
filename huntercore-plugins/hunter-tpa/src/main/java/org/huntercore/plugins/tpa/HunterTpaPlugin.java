package org.huntercore.plugins.tpa;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.HunterLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HunterTpaPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private static final long REQUEST_TTL_MILLIS = 60_000L;
    private static final String DEFAULT_HOME = "home";

    private final Map<UUID, TeleportRequest> incoming = new HashMap<>();
    private final Map<UUID, UUID> outgoing = new HashMap<>();
    private File homesFile;
    private YamlConfiguration homes;

    @Override
    public void onEnable() {
        this.getDataFolder().mkdirs();
        this.homesFile = new File(this.getDataFolder(), "homes.yml");
        this.homes = YamlConfiguration.loadConfiguration(this.homesFile);
        for (final String command : List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "sethome", "home", "delhome", "homes")) {
            final org.bukkit.command.PluginCommand pluginCommand = this.getCommand(command);
            if (pluginCommand != null) {
                pluginCommand.setExecutor(this);
                pluginCommand.setTabCompleter(this);
            }
        }
        this.getServer().getScheduler().runTaskTimer(this, this::expireRequests, 20L * 10L, 20L * 10L);
    }

    @Override
    public boolean onCommand(
        @NotNull final CommandSender sender,
        @NotNull final Command command,
        @NotNull final String label,
        @NotNull final String[] args
    ) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage(this.text("只有玩家可以使用这个命令。", "Only players can use this command."));
            return true;
        }

        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "tpa" -> this.requestTeleport(player, args, TeleportType.TO_TARGET);
            case "tpahere" -> this.requestTeleport(player, args, TeleportType.TARGET_TO_REQUESTER);
            case "tpaccept" -> this.answerRequest(player, args, true);
            case "tpdeny" -> this.answerRequest(player, args, false);
            case "tpcancel" -> this.cancelRequest(player);
            case "sethome" -> this.setHome(player, args);
            case "home" -> this.home(player, args);
            case "delhome" -> this.deleteHome(player, args);
            case "homes" -> this.listHomes(player);
            default -> false;
        };
    }

    private boolean requestTeleport(final Player requester, final String[] args, final TeleportType type) {
        if (args.length != 1) {
            requester.sendMessage(type == TeleportType.TO_TARGET ? "/tpa <player>" : "/tpahere <player>");
            return true;
        }

        final Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            requester.sendMessage(this.text("该玩家不在线。", "That player is not online."));
            return true;
        }
        if (target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(this.text("不能给自己发送传送请求。", "You cannot send a teleport request to yourself."));
            return true;
        }

        this.removeOutgoing(requester.getUniqueId());
        final TeleportRequest oldIncoming = this.incoming.remove(target.getUniqueId());
        if (oldIncoming != null) {
            this.outgoing.remove(oldIncoming.requester());
        }

        final TeleportRequest request = new TeleportRequest(requester.getUniqueId(), target.getUniqueId(), type, System.currentTimeMillis() + REQUEST_TTL_MILLIS);
        this.incoming.put(target.getUniqueId(), request);
        this.outgoing.put(requester.getUniqueId(), target.getUniqueId());

        requester.sendMessage(this.text("传送请求已发送给 ", "Teleport request sent to ") + target.getName() + ".");
        target.sendMessage(Component.text(requester.getName(), NamedTextColor.YELLOW)
            .append(Component.text(type == TeleportType.TO_TARGET
                ? this.text(" 想传送到你这里。", " wants to teleport to you.")
                : this.text(" 想让你传送到他那里。", " wants you to teleport to them."), NamedTextColor.GRAY)));
        target.sendMessage(this.requestButtons(requester));
        return true;
    }

    private Component requestButtons(final Player requester) {
        return Component.text()
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text(this.text("同意", "Accept"), NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tpaccept " + requester.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("/tpaccept " + requester.getName(), NamedTextColor.GREEN))))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text("[", NamedTextColor.DARK_GRAY))
            .append(Component.text(this.text("拒绝", "Deny"), NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/tpdeny " + requester.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("/tpdeny " + requester.getName(), NamedTextColor.RED))))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .append(Component.text(" " + this.text("也可以输入 /tpaccept 或 /tpdeny。", "You can also type /tpaccept or /tpdeny."), NamedTextColor.GRAY))
            .build();
    }

    private boolean answerRequest(final Player target, final String[] args, final boolean accept) {
        TeleportRequest request = this.incoming.get(target.getUniqueId());
        if (args.length == 1) {
            final Player requester = Bukkit.getPlayerExact(args[0]);
            if (requester == null || request == null || !request.requester().equals(requester.getUniqueId())) {
                request = null;
            }
        }

        if (request == null || request.isExpired()) {
            this.incoming.remove(target.getUniqueId());
            target.sendMessage(this.text("你没有待处理的传送请求。", "You do not have a pending teleport request."));
            return true;
        }

        this.incoming.remove(target.getUniqueId());
        this.outgoing.remove(request.requester());

        final Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null || !requester.isOnline()) {
            target.sendMessage(this.text("请求传送的玩家已离线。", "The requesting player is no longer online."));
            return true;
        }

        if (!accept) {
            target.sendMessage(this.text("已拒绝传送请求。", "Teleport request denied."));
            requester.sendMessage(target.getName() + this.text(" 拒绝了你的传送请求。", " denied your teleport request."));
            return true;
        }

        final Player teleporting = request.type() == TeleportType.TO_TARGET ? requester : target;
        final Player destination = request.type() == TeleportType.TO_TARGET ? target : requester;
        teleporting.teleportAsync(destination.getLocation()).thenAccept(success -> {
            if (success) {
                teleporting.sendMessage(this.text("已传送到 ", "Teleported to ") + destination.getName() + ".");
                destination.sendMessage(this.text("已同意来自 ", "Accepted teleport request from ") + requester.getName() + ".");
            } else {
                teleporting.sendMessage(this.text("传送失败。", "Teleport failed."));
            }
        });
        return true;
    }

    private boolean cancelRequest(final Player requester) {
        final UUID targetId = this.outgoing.remove(requester.getUniqueId());
        if (targetId == null) {
            requester.sendMessage(this.text("你没有发出的传送请求。", "You do not have an outgoing teleport request."));
            return true;
        }
        this.incoming.remove(targetId);
        final Player target = Bukkit.getPlayer(targetId);
        requester.sendMessage(this.text("传送请求已取消。", "Teleport request cancelled."));
        if (target != null) {
            target.sendMessage(requester.getName() + this.text(" 取消了传送请求。", " cancelled their teleport request."));
        }
        return true;
    }

    private boolean setHome(final Player player, final String[] args) {
        if (args.length > 1) {
            player.sendMessage("/sethome [name]");
            return true;
        }
        final String name = homeName(args.length == 0 ? DEFAULT_HOME : args[0]);
        this.saveHome(player, name, player.getLocation());
        player.sendMessage(this.text("家已设置：", "Home set: ") + name + ".");
        return true;
    }

    private boolean home(final Player player, final String[] args) {
        if (args.length > 1) {
            player.sendMessage("/home [name]");
            return true;
        }
        final String name = homeName(args.length == 0 ? DEFAULT_HOME : args[0]);
        final Location location = this.loadHome(player, name);
        if (location == null) {
            player.sendMessage(this.text("没有找到家：", "Home not found: ") + name + ".");
            return true;
        }
        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                player.sendMessage(this.text("已传送到家：", "Teleported home: ") + name + ".");
            }
        });
        return true;
    }

    private boolean deleteHome(final Player player, final String[] args) {
        if (args.length > 1) {
            player.sendMessage("/delhome [name]");
            return true;
        }
        final String name = homeName(args.length == 0 ? DEFAULT_HOME : args[0]);
        final String path = homePath(player, name);
        if (!this.homes.contains(path)) {
            player.sendMessage(this.text("没有找到家：", "Home not found: ") + name + ".");
            return true;
        }
        this.homes.set(path, null);
        this.saveHomes();
        player.sendMessage(this.text("家已删除：", "Home deleted: ") + name + ".");
        return true;
    }

    private boolean listHomes(final Player player) {
        final List<String> names = this.homeNames(player);
        player.sendMessage(this.text("你的家：", "Your homes: ") + (names.isEmpty() ? this.text("无", "none") : String.join(", ", names)));
        return true;
    }

    private void saveHome(final Player player, final String name, final Location location) {
        final String path = homePath(player, name);
        this.homes.set(path + ".world", location.getWorld() == null ? "" : location.getWorld().getName());
        this.homes.set(path + ".x", location.getX());
        this.homes.set(path + ".y", location.getY());
        this.homes.set(path + ".z", location.getZ());
        this.homes.set(path + ".yaw", location.getYaw());
        this.homes.set(path + ".pitch", location.getPitch());
        this.saveHomes();
    }

    private Location loadHome(final Player player, final String name) {
        final String path = homePath(player, name);
        final String worldName = this.homes.getString(path + ".world", "");
        final World world = Bukkit.getWorld(worldName);
        if (world == null || !this.homes.contains(path + ".x")) {
            return null;
        }
        return new Location(
            world,
            this.homes.getDouble(path + ".x"),
            this.homes.getDouble(path + ".y"),
            this.homes.getDouble(path + ".z"),
            (float) this.homes.getDouble(path + ".yaw"),
            (float) this.homes.getDouble(path + ".pitch")
        );
    }

    private List<String> homeNames(final Player player) {
        final ConfigurationSection section = this.homes.getConfigurationSection("homes." + player.getUniqueId());
        return section == null ? List.of() : section.getKeys(false).stream().sorted().toList();
    }

    private void saveHomes() {
        try {
            this.homes.save(this.homesFile);
        } catch (final IOException ex) {
            this.getLogger().severe("Failed to save homes.yml: " + ex.getMessage());
        }
    }

    private void removeOutgoing(final UUID requesterId) {
        final UUID oldTarget = this.outgoing.remove(requesterId);
        if (oldTarget != null) {
            this.incoming.remove(oldTarget);
        }
    }

    private void expireRequests() {
        final long now = System.currentTimeMillis();
        final List<TeleportRequest> expired = this.incoming.values().stream()
            .filter(request -> request.expiresAt() <= now)
            .toList();
        for (final TeleportRequest request : expired) {
            this.incoming.remove(request.target());
            this.outgoing.remove(request.requester());
            final Player requester = Bukkit.getPlayer(request.requester());
            if (requester != null) {
                requester.sendMessage(this.text("你的传送请求已过期。", "Your teleport request expired."));
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull final CommandSender sender,
        @NotNull final Command command,
        @NotNull final String alias,
        @NotNull final String[] args
    ) {
        if (args.length != 1 || !(sender instanceof final Player player)) {
            return List.of();
        }
        final String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("home") || name.equals("delhome")) {
            return matching(args[0], this.homeNames(player));
        }
        return matching(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }

    private String text(final String zhCn, final String enUs) {
        return HunterLanguage.choose(HunterCoreProvider.get().language(), zhCn, enUs);
    }

    private static List<String> matching(final String prefix, final List<String> values) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private static String homeName(final String input) {
        final String normalized = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return normalized.isBlank() ? DEFAULT_HOME : normalized;
    }

    private static String homePath(final Player player, final String name) {
        return "homes." + player.getUniqueId() + "." + homeName(name);
    }

    private enum TeleportType {
        TO_TARGET,
        TARGET_TO_REQUESTER
    }

    private record TeleportRequest(UUID requester, UUID target, TeleportType type, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() >= this.expiresAt;
        }
    }
}
