package org.huntercore.plugins.tpa;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.HunterLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HunterTpaPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final long REQUEST_TTL_MILLIS = 60_000L;
    private static final String DEFAULT_HOME = "home";
    private static final String TELEPORT_GUI_TITLE = ChatColor.DARK_AQUA + "HunterTPA · Teleport";
    private static final String HOMES_GUI_TITLE = ChatColor.DARK_GREEN + "HunterTPA · 我的家";
    private static final String DELETE_HOME_GUI_TITLE = ChatColor.DARK_RED + "HunterTPA · Delete Home";

    private final Map<UUID, TeleportRequest> incoming = new HashMap<>();
    private final Map<UUID, UUID> outgoing = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, String> pendingHomeDeletes = new HashMap<>();
    private File homesFile;
    private YamlConfiguration homes;

    @Override
    public void onEnable() {
        this.getConfig().addDefault("cooldown-seconds", 5);
        this.getConfig().addDefault("warmup-seconds", 3);
        this.getConfig().addDefault("cancel-on-damage", true);
        this.getConfig().addDefault("safe-landing", true);
        this.getConfig().addDefault("rtp-radius", 5000);
        this.getConfig().addDefault("rtp-min-radius", 128);
        this.getConfig().addDefault("rtp-attempts", 16);
        this.getConfig().addDefault("gui-sounds", true);
        this.getConfig().addDefault("warmup-actionbar", true);
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();

        this.getDataFolder().mkdirs();
        this.homesFile = new File(this.getDataFolder(), "homes.yml");
        this.homes = YamlConfiguration.loadConfiguration(this.homesFile);
        for (final String command : List.of("tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "tptoggle", "tpgui", "sethome", "home", "delhome", "homes", "homegui", "rtp")) {
            final org.bukkit.command.PluginCommand pluginCommand = this.getCommand(command);
            if (pluginCommand != null) {
                pluginCommand.setExecutor(this);
                pluginCommand.setTabCompleter(this);
            }
        }
        this.getServer().getPluginManager().registerEvents(this, this);
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
            case "tptoggle" -> this.toggleRequests(player);
            case "tpgui" -> this.openTeleportGui(player);
            case "sethome" -> this.setHome(player, args);
            case "home" -> args.length == 0 ? this.openHomesGui(player) : this.home(player, args);
            case "delhome" -> this.deleteHome(player, args);
            case "homes" -> args.length > 0 && (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("text")) ? this.listHomes(player) : this.openHomesGui(player);
            case "homegui" -> this.openHomesGui(player);
            case "rtp" -> this.randomTeleport(player);
            default -> false;
        };
    }

    private boolean openTeleportGui(final Player player) {
        final Inventory inventory = Bukkit.createInventory(player, 54, TELEPORT_GUI_TITLE);
        inventory.setItem(4, item(Material.ENDER_PEARL, this.text("传送中心", "Teleport Center"), List.of(this.text("左键玩家：传送到对方", "Left-click player: teleport to them"), this.text("右键玩家：让对方传送到你", "Right-click player: invite them to you"))));
        inventory.setItem(45, item(Material.RED_BED, this.text("我的家", "My Homes"), List.of("/homes")));
        inventory.setItem(46, item(Material.COMPASS, "Spawn", List.of("/spawn")));
        inventory.setItem(47, item(Material.CLOCK, "Back", List.of("/back")));
        inventory.setItem(49, item(this.requestsDisabled(player) ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK, this.requestsDisabled(player) ? this.text("TPA 已关闭", "TPA disabled") : this.text("TPA 已开启", "TPA enabled"), List.of(this.text("点击切换是否接收传送请求。", "Click to toggle incoming teleport requests."))));
        inventory.setItem(51, item(Material.GRASS_BLOCK, "RTP", List.of(this.text("随机传送到当前世界的安全位置。", "Random teleport to a safe location in this world."))));
        inventory.setItem(53, item(Material.BARRIER, this.text("关闭", "Close"), List.of()));
        int slot = 9;
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            inventory.setItem(slot++, item(Material.PLAYER_HEAD, online.getName(), List.of(
                this.text("世界：", "World: ") + online.getWorld().getName(),
                this.text("左键：/tpa ", "Left: /tpa ") + online.getName(),
                this.text("右键：/tpahere ", "Right: /tpahere ") + online.getName()
            )));
        }
        player.openInventory(inventory);
        return true;
    }

    private boolean openHomesGui(final Player player) {
        final Inventory inventory = Bukkit.createInventory(player, 54, HOMES_GUI_TITLE);
        inventory.setItem(4, item(Material.RED_BED, this.text("我的家", "My Homes"), List.of(
            this.text("左键传送，右键重命名提示，Shift 右键删除。", "Left-click to teleport, right-click rename hint, shift-right-click to delete."),
            this.text("/homes list 可输出文字列表。", "/homes list prints a text list.")
        )));
        int slot = 9;
        for (final String home : this.homeNames(player)) {
            if (slot >= 45) {
                break;
            }
            final Location location = this.loadHome(player, home);
            final World world = location == null ? null : location.getWorld();
            final Material material = home.equals(DEFAULT_HOME) ? Material.RED_BED : world == null ? Material.BARRIER : switch (world.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            final boolean currentWorld = world != null && world.equals(player.getWorld());
            inventory.setItem(slot++, item(material, home, List.of(
                world == null ? this.text("世界：未知或已删除", "World: unknown or deleted") : this.text("世界：", "World: ") + world.getName(),
                location == null ? this.text("坐标：未知", "Coords: unknown") : this.text("坐标：", "Coords: ") + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(),
                currentWorld ? this.text("当前世界 Home", "Home in your current world") : "",
                this.text("左键：传送", "Left-click: teleport"),
                this.text("右键：重命名提示", "Right-click: rename hint"),
                this.text("Shift 右键：删除", "Shift-right-click: delete")
            ), currentWorld));
        }
        inventory.setItem(45, item(Material.LIME_BED, this.text("新建 Home", "New Home"), List.of(this.text("保存当前位置为下一个 home。", "Save this location as the next home."))));
        inventory.setItem(46, item(Material.ENDER_PEARL, this.text("传送中心", "Teleport Center"), List.of("/tpgui")));
        inventory.setItem(47, item(Material.NAME_TAG, this.text("设置 default home", "Set default home"), List.of("/sethome home")));
        inventory.setItem(48, item(Material.NAME_TAG, this.text("设置 home1", "Set home1"), List.of("/sethome home1")));
        inventory.setItem(49, item(Material.NAME_TAG, this.text("刷新", "Refresh"), List.of(this.text("重新加载 Home GUI。", "Refresh this homes GUI."))));
        inventory.setItem(50, item(Material.NAME_TAG, this.text("设置 home2", "Set home2"), List.of("/sethome home2")));
        inventory.setItem(51, item(Material.NAME_TAG, this.text("设置 home3", "Set home3"), List.of("/sethome home3")));
        inventory.setItem(52, item(Material.NAME_TAG, this.text("设置 home4", "Set home4"), List.of("/sethome home4")));
        inventory.setItem(53, item(Material.BARRIER, this.text("关闭", "Close"), List.of()));
        player.openInventory(inventory);
        return true;
    }

    private void openDeleteHomeGui(final Player player, final String home) {
        this.pendingHomeDeletes.put(player.getUniqueId(), home);
        final Inventory inventory = Bukkit.createInventory(player, 27, DELETE_HOME_GUI_TITLE);
        inventory.setItem(11, item(Material.LIME_WOOL, this.text("确认删除 ", "Confirm delete ") + home, List.of(this.text("这个操作不可撤销。", "This cannot be undone."))));
        inventory.setItem(13, item(Material.RED_BED, home, List.of(this.text("即将删除这个家。", "This home will be deleted."))));
        inventory.setItem(15, item(Material.RED_WOOL, this.text("取消", "Cancel"), List.of(this.text("返回家列表。", "Return to homes."))));
        player.openInventory(inventory);
    }

    private boolean requestTeleport(final Player requester, final String[] args, final TeleportType type) {
        if (args.length != 1) {
            if (args.length == 0) {
                return this.openTeleportGui(requester);
            }
            requester.sendMessage(type == TeleportType.TO_TARGET ? "/tpa <player>" : "/tpahere <player>");
            return true;
        }
        if (!this.checkCooldown(requester)) {
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
        if (this.requestsDisabled(target)) {
            requester.sendMessage(this.text("该玩家已关闭传送请求。", "That player is not accepting teleport requests."));
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
        this.markCooldown(requester);

        requester.sendMessage(this.text("传送请求已发送给 ", "Teleport request sent to ") + target.getName() + ".");
        this.playGuiSound(requester, Sound.UI_BUTTON_CLICK);
        this.playGuiSound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
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
        this.startWarmup(
            teleporting,
            destination.getLocation(),
            this.text("已传送到 ", "Teleported to ") + destination.getName() + ".",
            () -> destination.sendMessage(this.text("已同意来自 ", "Accepted teleport request from ") + requester.getName() + ".")
        );
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

    private boolean toggleRequests(final Player player) {
        final String path = togglePath(player);
        final boolean disabled = !this.homes.getBoolean(path, false);
        this.homes.set(path, disabled);
        this.saveHomes();
        player.sendMessage(disabled
            ? this.text("你已关闭传送请求。", "Teleport requests are now disabled.")
            : this.text("你已开启传送请求。", "Teleport requests are now enabled."));
        this.playGuiSound(player, disabled ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
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
        if (!this.checkCooldown(player)) {
            return true;
        }
        final String name = homeName(args.length == 0 ? DEFAULT_HOME : args[0]);
        final Location location = this.loadHome(player, name);
        if (location == null) {
            player.sendMessage(this.text("没有找到家：", "Home not found: ") + name + ".");
            return true;
        }
        this.markCooldown(player);
        this.startWarmup(player, location, this.text("已传送到家：", "Teleported home: ") + name + ".", () -> {
        });
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(final PlayerMoveEvent event) {
        final PendingTeleport pending = this.pendingTeleports.get(event.getPlayer().getUniqueId());
        if (pending == null || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            this.cancelWarmup(event.getPlayer(), this.text("移动后传送已取消。", "Teleport cancelled because you moved."));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(final EntityDamageEvent event) {
        if (this.getConfig().getBoolean("cancel-on-damage", true)
            && event.getEntity() instanceof final Player player
            && this.pendingTeleports.containsKey(player.getUniqueId())) {
            this.cancelWarmup(player, this.text("受到伤害后传送已取消。", "Teleport cancelled because you took damage."));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        this.pendingTeleports.remove(uuid);
        this.pendingHomeDeletes.remove(uuid);
        this.cooldowns.remove(uuid);
        this.removeOutgoing(uuid);
        final TeleportRequest incomingRequest = this.incoming.remove(uuid);
        if (incomingRequest != null) {
            this.outgoing.remove(incomingRequest.requester());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        final String title = event.getView().getTitle();
        if (!title.equals(TELEPORT_GUI_TITLE) && !title.equals(HOMES_GUI_TITLE) && !title.equals(DELETE_HOME_GUI_TITLE)) {
            return;
        }
        event.setCancelled(true);
        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) {
            return;
        }
        final String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (title.equals(TELEPORT_GUI_TITLE)) {
            this.handleTeleportGuiClick(player, clicked, name, event.getClick());
        } else if (title.equals(DELETE_HOME_GUI_TITLE)) {
            this.handleDeleteHomeGuiClick(player, clicked);
        } else {
            this.handleHomesGuiClick(player, clicked, name, event.getClick());
        }
    }

    private void handleTeleportGuiClick(final Player player, final ItemStack clicked, final String name, final ClickType click) {
        switch (clicked.getType()) {
            case RED_BED -> this.openHomesGui(player);
            case COMPASS -> player.performCommand("spawn");
            case CLOCK -> player.performCommand("back");
            case REDSTONE_BLOCK, EMERALD_BLOCK -> {
                this.toggleRequests(player);
                this.openTeleportGui(player);
            }
            case GRASS_BLOCK -> this.randomTeleport(player);
            case BARRIER -> {
                this.playGuiSound(player, Sound.UI_BUTTON_CLICK);
                player.closeInventory();
            }
            case PLAYER_HEAD -> {
                if (click.isRightClick()) {
                    this.requestTeleport(player, new String[] {name}, TeleportType.TARGET_TO_REQUESTER);
                } else {
                    this.requestTeleport(player, new String[] {name}, TeleportType.TO_TARGET);
                }
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    private void handleHomesGuiClick(final Player player, final ItemStack clicked, final String name, final ClickType click) {
        switch (clicked.getType()) {
            case ENDER_PEARL -> this.openTeleportGui(player);
            case NAME_TAG -> {
                if (name.equals(this.text("刷新", "Refresh"))) {
                    this.openHomesGui(player);
                    return;
                }
                this.setHome(player, new String[] {homeNameFromSetButton(name)});
                this.openHomesGui(player);
            }
            case LIME_BED -> {
                this.setHome(player, new String[] {this.nextHomeName(player)});
                this.openHomesGui(player);
            }
            case BARRIER -> {
                if (name.equals(this.text("关闭", "Close"))) {
                    player.closeInventory();
                    return;
                }
                player.sendMessage(this.text("这个家不可用。", "That home is not available."));
            }
            default -> {
                if (click.isShiftClick() && click.isRightClick()) {
                    this.openDeleteHomeGui(player, name);
                    return;
                }
                if (click.isRightClick()) {
                    player.sendMessage(this.text("重命名 Home 请使用：/sethome <新名字> 后删除旧 Home。", "Rename homes by using /sethome <newName> and deleting the old home."));
                    this.playGuiSound(player, Sound.UI_BUTTON_CLICK);
                    return;
                }
                this.home(player, new String[] {name});
                player.closeInventory();
            }
        }
    }

    private void handleDeleteHomeGuiClick(final Player player, final ItemStack clicked) {
        final String home = this.pendingHomeDeletes.get(player.getUniqueId());
        if (home == null) {
            this.openHomesGui(player);
            return;
        }
        switch (clicked.getType()) {
            case LIME_WOOL -> {
                this.deleteHome(player, new String[] {home});
                this.pendingHomeDeletes.remove(player.getUniqueId());
                this.playGuiSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                this.openHomesGui(player);
            }
            case RED_WOOL, BARRIER -> {
                this.pendingHomeDeletes.remove(player.getUniqueId());
                this.playGuiSound(player, Sound.UI_BUTTON_CLICK);
                this.openHomesGui(player);
            }
            default -> {
            }
        }
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

    private boolean randomTeleport(final Player player) {
        if (!this.checkCooldown(player)) {
            return true;
        }
        final World world = player.getWorld();
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final int radius = Math.max(1, this.getConfig().getInt("rtp-radius", 5000));
        final int minRadius = Math.max(0, Math.min(radius, this.getConfig().getInt("rtp-min-radius", 128)));
        final int attempts = Math.max(1, this.getConfig().getInt("rtp-attempts", 16));
        for (int attempt = 0; attempt < attempts; attempt++) {
            final int distance = random.nextInt(minRadius, radius + 1);
            final double angle = random.nextDouble(Math.PI * 2.0D);
            final int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            final int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            final Location candidate = world.getHighestBlockAt(x, z).getLocation().add(0.5D, 1.0D, 0.5D);
            if (this.safeDestination(candidate) != null) {
                this.markCooldown(player);
                this.startWarmup(player, candidate, this.text("已随机传送。", "Randomly teleported."), () -> {
                });
                return true;
            }
        }
        player.sendMessage(this.text("没有找到安全的随机传送位置。", "Could not find a safe random teleport location."));
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

    private String nextHomeName(final Player player) {
        final List<String> names = this.homeNames(player);
        if (!names.contains(DEFAULT_HOME)) {
            return DEFAULT_HOME;
        }
        for (int index = 1; index <= 99; index++) {
            final String candidate = "home" + index;
            if (!names.contains(candidate)) {
                return candidate;
            }
        }
        return "home" + (names.size() + 1);
    }

    private static String homeNameFromSetButton(final String displayName) {
        final String lower = displayName.toLowerCase(Locale.ROOT);
        for (final String candidate : List.of("home1", "home2", "home3", "home4")) {
            if (lower.contains(candidate)) {
                return candidate;
            }
        }
        return DEFAULT_HOME;
    }

    private ItemStack item(final Material material, final String name, final List<String> lore) {
        return item(material, name, lore, false);
    }

    private ItemStack item(final Material material, final String name, final List<String> lore, final boolean highlighted) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((highlighted ? ChatColor.GOLD : ChatColor.AQUA) + name);
            meta.setLore(lore.stream().filter(line -> !line.isBlank()).map(line -> ChatColor.GRAY + line).toList());
            if (highlighted) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                try {
                    meta.getClass().getMethod("setEnchantmentGlintOverride", Boolean.class).invoke(meta, Boolean.TRUE);
                } catch (final ReflectiveOperationException ignored) {
                    // Older Bukkit APIs do not expose glint override; the gold name/lore still marks current-world homes.
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void playGuiSound(final Player player, final Sound sound) {
        if (this.getConfig().getBoolean("gui-sounds", true)) {
            player.playSound(player.getLocation(), sound, 0.7F, 1.15F);
        }
    }

    private boolean checkCooldown(final Player player) {
        final long until = this.cooldowns.getOrDefault(player.getUniqueId(), 0L);
        final long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            this.cooldowns.remove(player.getUniqueId());
            return true;
        }
        player.sendMessage(this.text("传送冷却中，请等待 ", "Teleport is on cooldown. Wait ") + Math.max(1L, (remaining + 999L) / 1000L) + "s.");
        return false;
    }

    private void markCooldown(final Player player) {
        final int cooldownSeconds = Math.max(0, this.getConfig().getInt("cooldown-seconds", 5));
        if (cooldownSeconds > 0) {
            this.cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
    }

    private void startWarmup(final Player player, final Location destination, final String successMessage, final Runnable afterSuccess) {
        final int warmupSeconds = Math.max(0, this.getConfig().getInt("warmup-seconds", 3));
        if (warmupSeconds <= 0) {
            this.finishTeleport(player, destination, successMessage, afterSuccess);
            return;
        }
        final PendingTeleport pending = new PendingTeleport(player.getLocation(), destination.clone(), successMessage, afterSuccess);
        this.pendingTeleports.put(player.getUniqueId(), pending);
        player.sendMessage(this.text("传送准备中，请不要移动或受伤：", "Teleport warmup started. Do not move or take damage: ") + warmupSeconds + "s.");
        if (this.getConfig().getBoolean("warmup-actionbar", true)) {
            for (int second = 1; second <= warmupSeconds; second++) {
                final int remaining = warmupSeconds - second + 1;
                this.getServer().getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline() && this.pendingTeleports.get(player.getUniqueId()) == pending) {
                        player.sendActionBar(Component.text(this.text("传送倒计时：", "Teleport in: ") + remaining + "s", NamedTextColor.AQUA));
                    }
                }, (long) (second - 1) * 20L);
            }
        }
        this.getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || this.pendingTeleports.get(player.getUniqueId()) != pending) {
                return;
            }
            this.pendingTeleports.remove(player.getUniqueId());
            this.finishTeleport(player, pending.destination(), pending.successMessage(), pending.afterSuccess());
        }, warmupSeconds * 20L);
    }

    private void finishTeleport(final Player player, final Location destination, final String successMessage, final Runnable afterSuccess) {
        final Location target = this.getConfig().getBoolean("safe-landing", true) ? this.safeDestination(destination) : destination;
        if (target == null) {
            player.sendMessage(this.text("目标位置不安全，传送已取消。", "Destination is unsafe; teleport cancelled."));
            return;
        }
        player.teleportAsync(target).thenAccept(success -> this.getServer().getScheduler().runTask(this, () -> {
            if (success) {
                player.sendMessage(successMessage);
                this.playGuiSound(player, Sound.ENTITY_ENDERMAN_TELEPORT);
                afterSuccess.run();
            } else {
                player.sendMessage(this.text("传送失败。", "Teleport failed."));
            }
        }));
    }

    private void cancelWarmup(final Player player, final String message) {
        if (this.pendingTeleports.remove(player.getUniqueId()) != null) {
            player.sendMessage(message);
        }
    }

    private Location safeDestination(final Location destination) {
        if (this.isSafeDestination(destination)) {
            return destination;
        }
        for (int radius = 1; radius <= 2; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    final Location candidate = destination.clone().add(x, 0, z);
                    if (this.isSafeDestination(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeDestination(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final int y = location.getBlockY();
        if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        return !location.getBlock().getType().isSolid()
            && !location.clone().add(0, 1, 0).getBlock().getType().isSolid()
            && location.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }

    private void saveHomes() {
        try {
            this.homes.set("schema-version", 1);
            atomicSave(this.homes, this.homesFile);
        } catch (final IOException ex) {
            this.getLogger().severe("Failed to save homes.yml: " + ex.getMessage());
        }
    }

    private boolean requestsDisabled(final Player player) {
        return this.homes.getBoolean(togglePath(player), false);
    }

    private static void atomicSave(final YamlConfiguration configuration, final File file) throws IOException {
        final File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        final File tempFile = File.createTempFile(file.getName(), ".tmp", parent);
        try {
            configuration.save(tempFile);
            if (file.exists()) {
                Files.copy(file.toPath(), file.toPath().resolveSibling(file.getName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException ex) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException ex) {
            Files.deleteIfExists(tempFile.toPath());
            throw ex;
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

    private static String togglePath(final Player player) {
        return "settings." + player.getUniqueId() + ".requests-disabled";
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

    private record PendingTeleport(Location origin, Location destination, String successMessage, Runnable afterSuccess) {
    }
}
