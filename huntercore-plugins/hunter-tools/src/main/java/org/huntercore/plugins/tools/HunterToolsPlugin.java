package org.huntercore.plugins.tools;

import java.lang.reflect.Field;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.huntercore.api.HunterCommandExtension;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.HunterHelp;
import org.huntercore.api.HunterLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HunterToolsPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> MODULES = List.of("tps-display", "sidebar", "motd", "command-overrides", "essentials", "management", "fake-players", "real-fake-players", "npcs", "ai", "auth", "web-panel", "titles");
    private static final String MOTD = "motd";
    private static final String COMMAND_OVERRIDES = "command-overrides";
    private static final String ESSENTIALS = "essentials";
    private static final String MANAGEMENT = "management";
    private static final String FAKE_PLAYERS = "fake-players";
    private static final String REAL_FAKE_PLAYERS = "real-fake-players";
    private static final String NPCS = "npcs";
    private static final String AI = "ai";
    private static final String AUTH = "auth";
    private static final String WEB_PANEL = "web-panel";
    private static final String TITLES = "titles";
    private static final List<String> HUNTERCORE_SHORTCUTS = List.of(
        "tps", "heal", "feed", "fly", "gm", "gms", "gmc", "gma", "gmsp",
        "day", "night", "sun", "rain", "thunder", "broadcast", "clearchat", "speed", "spawn", "setspawn", "back",
        "hat", "craft", "enderchest", "trash"
    );
    private static final String MAIN_MENU_TITLE = ChatColor.DARK_AQUA + "HunterCore · 服务器菜单";
    private static final String PROFILE_MENU_TITLE = ChatColor.DARK_AQUA + "HunterCore · 我的资料";
    private static final String INVENTORY_PREVIEW_TITLE = ChatColor.DARK_AQUA + "HunterCore · 背包预览";
    private static final String SETTINGS_MENU_TITLE = ChatColor.DARK_AQUA + "HunterCore · 设置";
    private static final String ADMIN_MENU_TITLE = ChatColor.DARK_RED + "HunterCore · 管理员中心";
    private static final String ADMIN_SYSTEM_TITLE = ChatColor.DARK_RED + "HunterCore Admin · System";
    private static final String ADMIN_MODULES_TITLE = ChatColor.DARK_RED + "HunterCore Admin · Modules";
    private static final String ADMIN_PREFERENCES_TITLE = ChatColor.DARK_RED + "HunterCore Admin · Preferences";
    private static final String ADMIN_OPTIMIZE_TITLE = ChatColor.DARK_RED + "HunterCore Admin · Optimize";
    private static final String[] SIDEBAR_KEYS = {
        "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
    };

    private final Map<UUID, SidebarBoard> sidebars = new HashMap<>();
    private final Map<UUID, Location> backLocations = new HashMap<>();
    private final Map<UUID, GuiChatSession> guiChatSessions = new HashMap<>();
    private final Map<UUID, GuiConfirmSession> guiConfirmSessions = new HashMap<>();
    private HunterToolsPreferences preferences;
    private HunterActorManager actorManager;
    private HunterRealFakePlayerManager realFakePlayerManager;
    private HunterGameplayRuleManager gameplayRuleManager;
    private HunterAiManager aiManager;
    private HunterWebPanelManager webPanelManager;
    private HunterTitleManager titleManager;
    private HunterStoryModeManager storyModeManager;
    private ExecutorService workerExecutor;
    private MetricsSnapshot snapshot = MetricsSnapshot.empty();
    private volatile List<String> cachedPlayerNames = List.of();
    private BukkitTask metricsTask;
    private BukkitTask actionbarTask;
    private BukkitTask sidebarTask;
    private static final String ACTOR_LIST_TITLE_PREFIX = "HC Actors ";
    private static final String ACTOR_DETAIL_TITLE_PREFIX = "HC Actor ";
    private static final String STORY_WORKBENCH_TITLE = "HC Story Workbench";

    @Override
    public void onEnable() {
        this.preferences = HunterToolsPreferences.loadOrCreate(this);
        this.applyServerBrand();
        this.workerExecutor = this.createWorkerExecutor();
        this.actorManager = new HunterActorManager(this, this.preferences, this.workerExecutor);
        this.aiManager = new HunterAiManager(this, this.preferences, this.workerExecutor);
        this.gameplayRuleManager = new HunterGameplayRuleManager(this);
        this.realFakePlayerManager = new HunterRealFakePlayerManager(this, this.preferences, this.aiManager, this.gameplayRuleManager);
        this.storyModeManager = new HunterStoryModeManager(this, this.preferences, this.realFakePlayerManager);
        this.webPanelManager = new HunterWebPanelManager(this, this.preferences);
        this.titleManager = new HunterTitleManager(this, this.preferences);
        this.registerCommands();
        this.registerHunterCoreCommands();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(this.gameplayRuleManager, this);
        this.startTasks();
        this.actorManager.reload();
        this.webPanelManager.start();
        this.getLogger().info("HunterTools enabled with preferences at " + this.preferences.file().getPath());
    }

    @Override
    public void onDisable() {
        this.cancelTasks();
        if (this.actorManager != null) {
            this.actorManager.shutdown();
        }
        if (this.realFakePlayerManager != null) {
            this.realFakePlayerManager.shutdown();
        }
        if (this.storyModeManager != null) {
            this.storyModeManager.shutdown();
        }
        if (this.gameplayRuleManager != null) {
            this.gameplayRuleManager.shutdown();
        }
        if (this.webPanelManager != null) {
            this.webPanelManager.stop();
        }
        this.clearSidebars();
        if (this.preferences != null) {
            this.preferences.flushPendingSaves();
        }
        if (this.workerExecutor != null) {
            this.workerExecutor.shutdownNow();
        }
        if (this.preferences != null) {
            this.preferences.saveNow();
        }
    }

    @Override
    public boolean onCommand(
        @NotNull final CommandSender sender,
        @NotNull final Command command,
        @NotNull final String label,
        @NotNull final String[] args
    ) {
        final String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length > 0 && isHelp(args[0])) {
            this.sendCommandHelp(sender, name, args);
            return true;
        }
        return switch (name) {
            case "htps" -> this.showTps(sender);
            case "heal" -> this.heal(sender, args);
            case "feed" -> this.feed(sender, args);
            case "fly" -> this.fly(sender, args);
            case "gm", "gms", "gmc", "gma", "gmsp" -> this.gameMode(sender, name, args);
            case "day", "night" -> this.time(sender, name, args);
            case "sun", "rain", "thunder" -> this.weather(sender, name, args);
            case "broadcast" -> this.broadcast(sender, args);
            case "clearchat" -> this.clearChat(sender);
            case "speed" -> this.speed(sender, args);
            case "spawn" -> this.spawn(sender, args);
            case "setspawn" -> this.setSpawn(sender);
            case "back" -> this.back(sender);
            case "hat" -> this.hat(sender);
            case "craft" -> this.craft(sender);
            case "enderchest" -> this.enderChest(sender, args);
            case "trash" -> this.trash(sender);
            case "menu" -> this.openMainMenuWorkbench(sender);
            case "profile", "playerinfo", "me" -> this.openProfileWorkbench(sender);
            case "settings" -> this.openSettingsWorkbench(sender);
            case "admin" -> this.admin(sender, args);
            case "player" -> this.realFakePlayer(sender, "player", args);
            case "npc" -> this.npc(sender, "npc", args);
            case "start" -> this.storyModeManager != null && this.storyModeManager.startCommand(sender);
            case "story" -> args.length == 0 && sender instanceof Player ? this.openStoryWorkbench((Player) sender) : this.storyModeManager != null && this.storyModeManager.command(sender, "story", args);
            case "title", "titles" -> this.titleCommand(sender, args);
            default -> false;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull final CommandSender sender,
        @NotNull final Command command,
        @NotNull final String alias,
        @NotNull final String[] args
    ) {
        final String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("player")) {
            return this.realFakePlayerManager == null ? List.of() : this.realFakePlayerManager.completions(args);
        }
        if (name.equals("npc")) {
            return this.actorManager == null ? List.of() : this.actorManager.completions(NPCS, args);
        }
        if (name.equals("admin")) {
            return this.adminCompletions(args);
        }
        if (name.equals("story")) {
            return this.storyModeManager == null ? List.of() : this.storyModeManager.completions(args);
        }
        if (name.equals("title") || name.equals("titles")) {
            return this.titleCompletions(args);
        }
        return this.shortcutCompletions(sender, name, args);
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (this.titleManager != null) {
            this.titleManager.refreshPlayer(event.getPlayer());
        }
        if (this.preferences.moduleEnabled("sidebar")) {
            this.updateSidebarSoon(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.sidebars.remove(event.getPlayer().getUniqueId());
        this.backLocations.remove(event.getPlayer().getUniqueId());
        this.guiChatSessions.remove(event.getPlayer().getUniqueId());
        this.guiConfirmSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (this.preferences.moduleEnabled(ESSENTIALS) && this.preferences.commandEnabled(ESSENTIALS, "back")) {
            this.backLocations.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(final PlayerDeathEvent event) {
        if (this.preferences.moduleEnabled(ESSENTIALS) && this.preferences.commandEnabled(ESSENTIALS, "back")) {
            this.backLocations.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMainMenuClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        final GuiHolder holder = this.guiHolder(event);
        if (holder == null || holder.page() != GuiPage.MAIN) {
            return;
        }
        event.setCancelled(true);
        if (!this.clickingTopInventory(event)) {
            return;
        }
        final ItemStack clicked = event.getCurrentItem();
        if (this.isGuiPlaceholder(clicked)) {
            return;
        }
        this.runGuiAction(player, () -> {
            switch (clicked.getType()) {
                case ENDER_PEARL -> player.performCommand("tpgui");
                case RED_BED -> player.performCommand("homes");
                case GRASS_BLOCK -> player.performCommand("rtp");
                case COMPASS -> player.performCommand("spawn");
                case CLOCK -> player.performCommand("back");
                case CRAFTING_TABLE -> player.performCommand("craft");
                case ENDER_CHEST -> player.performCommand("enderchest");
                case CHEST -> player.performCommand("trash");
                case IRON_SWORD -> this.openToolsWorkbench(player);
                case LEVER -> this.openSettingsWorkbench(player);
                case PLAYER_HEAD -> this.openProfileWorkbench(player);
                case BOOK -> player.performCommand("info");
                case DIAMOND -> player.sendMessage(ChatColor.AQUA + this.text("商店/赞助: ", "Shop/Donate: ") + ChatColor.WHITE + this.preferences.stringValue("modules.menu.shop-url", this.text("未配置", "Not configured")));
                case FILLED_MAP -> player.sendMessage(ChatColor.AQUA + this.text("BlueMap 地图: ", "BlueMap: ") + ChatColor.WHITE + this.preferences.stringValue("modules.web-panel.map-url", this.text("未配置", "Not configured")));
                case ARMOR_STAND -> this.openActorListWorkbench(player, REAL_FAKE_PLAYERS);
                case VILLAGER_SPAWN_EGG -> this.openActorListWorkbench(player, NPCS);
                case ENCHANTED_BOOK -> this.openStoryWorkbench(player);
                case KNOWLEDGE_BOOK -> this.openCommandCenterWorkbench(player, "tools");
                case COMMAND_BLOCK -> this.openAdminWorkbench(player);
                case BARRIER -> player.closeInventory();
                default -> {
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onUtilityMenuClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        final GuiHolder holder = this.guiHolder(event);
        if (holder == null || holder.page() == GuiPage.MAIN) {
            return;
        }
        event.setCancelled(true);
        if (!this.clickingTopInventory(event)) {
            return;
        }
        final ItemStack clicked = event.getCurrentItem();
        if (this.isGuiPlaceholder(clicked)) {
            return;
        }
        this.runGuiAction(player, () -> {
            switch (holder.page()) {
                case PROFILE -> this.handleProfileWorkbenchClick(player, clicked);
                case INVENTORY_PREVIEW -> {
                    if (clicked.getType() == Material.BARRIER) {
                        this.openProfileWorkbench(player);
                    }
                }
                case SETTINGS -> this.handleSettingsWorkbenchClick(player, clicked);
                case ADMIN -> this.handleAdminWorkbenchClick(player, clicked);
                case ADMIN_SYSTEM -> this.handleAdminSystemWorkbenchClick(player, clicked);
                case ADMIN_PLUGINS -> this.handleAdminPluginsWorkbenchClick(player, clicked);
                case ADMIN_MODULES -> this.handleAdminModulesWorkbenchClick(player, clicked, event.getRawSlot());
                case ADMIN_PREFERENCES -> this.handleAdminPreferencesWorkbenchClick(player, clicked);
                case ADMIN_OPTIMIZE -> this.handleAdminOptimizeWorkbenchClick(player, clicked);
                case ADMIN_CHAT_REPORTS -> this.handleAdminChatReportsWorkbenchClick(player, clicked);
                case ADMIN_AI -> this.handleAdminAiWorkbenchClick(player, clicked);
                case ADMIN_WEB -> this.handleAdminWebWorkbenchClick(player, clicked);
                case ADMIN_MOTD -> this.handleAdminMotdWorkbenchClick(player, clicked);
                case ADMIN_COMMANDS -> this.handleAdminCommandsWorkbenchClick(player, clicked, holder, event.getRawSlot());
                case TOOLS -> this.handleToolsWorkbenchClick(player, clicked);
                case COMMAND_CENTER -> this.handleCommandCenterWorkbenchClick(player, clicked, holder, event.getRawSlot());
                case ACTOR_LIST -> this.handleActorListWorkbenchClick(player, clicked, holder);
                case ACTOR_DETAIL -> this.handleActorDetailWorkbenchClick(player, clicked, holder);
                case STORY -> this.handleStoryWorkbenchClick(player, clicked);
                case TITLE_LIST -> this.handleTitleWorkbenchClick(player, clicked, event.getRawSlot());
                case CONFIRM -> this.handleConfirmWorkbenchClick(player, clicked, holder);
                default -> {
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuiDrag(final InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder)) {
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        for (final int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onServerListPing(final ServerListPingEvent event) {
        if (!this.preferences.moduleEnabled(MOTD)) {
            return;
        }
        final String line1 = this.renderMotdLine(this.preferences.stringValue("modules.motd.line-1", "&b\"HunterCraft\" Server &8| &fHunterCore"), event);
        final String line2 = this.renderMotdLine(this.preferences.stringValue("modules.motd.line-2", "&7%online%/%max% players &8- &aTPS %tps% &8- &e%version%"), event);
        event.setMotd(color(line1 + "\n" + line2));
        final int maxPlayers = this.preferences.intValue("modules.motd.max-players", -1);
        if (maxPlayers > 0) {
            event.setMaxPlayers(maxPlayers);
        }
    }

    private boolean openMainMenu(final CommandSender sender) {
        return this.openMainMenuWorkbench(sender);
    }


    private boolean openProfileMenu(final CommandSender sender) {
        return this.openProfileWorkbench(sender);
    }

    private void openInventoryPreview(final Player player) {
        final Inventory inventory = Bukkit.createInventory(player, 54, INVENTORY_PREVIEW_TITLE);
        inventory.setItem(4, this.menuItem(Material.CHEST, "背包预览", List.of("只读展示，不会移动物品。")));
        final ItemStack[] storage = player.getInventory().getStorageContents();
        for (int index = 0; index < Math.min(storage.length, 36); index++) {
            inventory.setItem(9 + index, cloneOrEmpty(storage[index]));
        }
        inventory.setItem(45, cloneOrEmpty(player.getInventory().getHelmet()));
        inventory.setItem(46, cloneOrEmpty(player.getInventory().getChestplate()));
        inventory.setItem(47, cloneOrEmpty(player.getInventory().getLeggings()));
        inventory.setItem(48, cloneOrEmpty(player.getInventory().getBoots()));
        inventory.setItem(50, cloneOrEmpty(player.getInventory().getItemInMainHand()));
        inventory.setItem(51, cloneOrEmpty(player.getInventory().getItemInOffHand()));
        inventory.setItem(53, this.menuItem(Material.BARRIER, "返回资料", List.of()));
        player.openInventory(inventory);
    }

    private void openInventoryPreviewWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.INVENTORY_PREVIEW, null, null, null), 54, this.guiTitle(GuiPage.INVENTORY_PREVIEW, null, null));
        inventory.setItem(4, this.menuItem(Material.CHEST, "背包预览", "Inventory Preview", List.of("只读展示，不会移动物品"), List.of("Read only display; items cannot be moved")));
        final ItemStack[] storage = player.getInventory().getStorageContents();
        for (int index = 0; index < Math.min(storage.length, 36); index++) {
            inventory.setItem(9 + index, cloneOrEmpty(storage[index]));
        }
        inventory.setItem(45, cloneOrEmpty(player.getInventory().getHelmet()));
        inventory.setItem(46, cloneOrEmpty(player.getInventory().getChestplate()));
        inventory.setItem(47, cloneOrEmpty(player.getInventory().getLeggings()));
        inventory.setItem(48, cloneOrEmpty(player.getInventory().getBoots()));
        inventory.setItem(50, cloneOrEmpty(player.getInventory().getItemInMainHand()));
        inventory.setItem(51, cloneOrEmpty(player.getInventory().getItemInOffHand()));
        inventory.setItem(53, this.menuItem(Material.BARRIER, "返回资料", "Back to Profile", List.of(), List.of()));
        player.openInventory(inventory);
    }

    private boolean openSettingsMenu(final CommandSender sender) {
        return this.openSettingsWorkbench(sender);
    }

    private boolean openAdminMenu(final CommandSender sender) {
        return this.openAdminWorkbench(sender);
    }

    private void handleProfileClick(final Player player, final ItemStack clicked) {
        this.handleProfileWorkbenchClick(player, clicked);
    }

    private void handleSettingsClick(final Player player, final ItemStack clicked) {
        this.handleSettingsWorkbenchClick(player, clicked);
    }

    private void handleAdminClick(final Player player, final ItemStack clicked) {
        this.handleAdminWorkbenchClick(player, clicked);
    }

    private boolean openMainMenuWorkbench(final CommandSender sender) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage(ChatColor.RED + this.text("只有玩家可以打开这个界面。", "Only players can open this GUI."));
            return true;
        }
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.MAIN, null, null, null), 54, this.guiTitle(GuiPage.MAIN, null, null));
        inventory.setItem(10, this.menuItem(Material.ENDER_PEARL, "传送中心", "Teleport", List.of("/tpgui", "在线玩家、TPA、TPHere"), List.of("HunterTPA workbench", "Online players, TPA and TPHere")));
        inventory.setItem(11, this.menuItem(Material.RED_BED, "我的家", "Homes", List.of("/homes", "多个家、传送、删除"), List.of("Home list, delete, and travel")));
        inventory.setItem(12, this.menuItem(Material.COMPASS, "出生点", "Spawn", List.of("点击执行 /spawn"), List.of("Teleport to spawn")));
        inventory.setItem(13, this.menuItem(Material.CLOCK, "返回", "Back", List.of("/back", "回到上一个位置"), List.of("Return to previous location")));
        inventory.setItem(14, this.menuItem(Material.CRAFTING_TABLE, "随身工作台", "Crafting", List.of("/craft"), List.of("Portable crafting table")));
        inventory.setItem(15, this.menuItem(Material.ENDER_CHEST, "末影箱", "Ender Chest", List.of("/enderchest"), List.of("Open your ender chest")));
        inventory.setItem(16, this.menuItem(Material.CHEST, "垃圾桶", "Trash", List.of("/trash"), List.of("Disposable inventory")));
        inventory.setItem(27, this.menuItem(Material.IRON_SWORD, "常用工具", "Tools", List.of("治疗、飞行、速度、时间、天气"), List.of("Heal, fly, speed, time and weather")));
        inventory.setItem(28, this.menuItem(Material.PLAYER_HEAD, "个人资料", "Profile", List.of("状态、背包预览、常用入口"), List.of("Stats, inventory preview, utility")));
        inventory.setItem(29, this.menuItem(Material.LEVER, "玩家设置", "Settings", List.of("TPA、语言、面板入口"), List.of("TPA toggle, language, panel")));
        inventory.setItem(30, this.menuItem(Material.ARMOR_STAND, "假人", "PlayerBots", List.of("查看并管理假人"), List.of("List and control fake players")));
        inventory.setItem(31, this.menuItem(Material.VILLAGER_SPAWN_EGG, "NPC", "NPCs", List.of("查看并管理 NPC"), List.of("List and control NPCs")));
        inventory.setItem(32, this.menuItem(Material.ENCHANTED_BOOK, "故事模式", "Story Mode", List.of("状态、启动、停止、实验能力"), List.of("Status, start, stop, experimental actions")));
        inventory.setItem(33, this.menuItem(Material.BOOK, "服务器信息", "Server Info", List.of("/info"), List.of("/info")));
        inventory.setItem(34, this.menuItem(Material.FILLED_MAP, "网页面板", "Web Panel", List.of(this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088")), List.of(this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088"))));
        inventory.setItem(35, this.menuItem(Material.KNOWLEDGE_BOOK, "全部指令", "Command Center", List.of("所有指令都有 GUI 入口", "需要参数时会引导聊天输入"), List.of("GUI entry for every command", "Prompts in chat when arguments are needed")));
        if (player.hasPermission("huntertools.command.admin")) {
            inventory.setItem(40, this.menuItem(Material.COMMAND_BLOCK, "管理中心", "Admin", List.of("状态、模块、广播"), List.of("Runtime, modules, broadcast")));
        }
        inventory.setItem(49, this.menuItem(Material.BARRIER, "关闭", "Close", List.of(), List.of()));
        player.openInventory(inventory);
        return true;
    }

    private void openToolsWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.TOOLS, null, null, null), 54, this.guiTitle(GuiPage.TOOLS, null, null));
        inventory.setItem(4, this.menuItem(Material.IRON_SWORD, "常用工具", "Tools", List.of(
            "一屏覆盖 HunterTools 常用指令",
            "需要权限的项目会沿用原指令权限"
        ), List.of(
            "GUI shortcuts for common HunterTools commands",
            "Permission checks still use the underlying commands"
        )));
        inventory.setItem(10, this.menuItem(Material.GOLDEN_APPLE, "治疗", "Heal", List.of("/heal"), List.of("/heal")));
        inventory.setItem(11, this.menuItem(Material.COOKED_BEEF, "饱食", "Feed", List.of("/feed"), List.of("/feed")));
        inventory.setItem(12, this.menuItem(Material.FEATHER, "飞行开关", "Toggle Fly", List.of("/fly"), List.of("/fly")));
        inventory.setItem(13, this.menuItem(Material.SUGAR, "速度", "Speed", List.of("点击后在聊天栏输入 1-10"), List.of("Type 1-10 in chat after clicking")));
        inventory.setItem(14, this.menuItem(Material.LEATHER_HELMET, "生存模式", "Survival", List.of("/gms"), List.of("/gms")));
        inventory.setItem(15, this.menuItem(Material.DIAMOND_BLOCK, "创造模式", "Creative", List.of("/gmc"), List.of("/gmc")));
        inventory.setItem(16, this.menuItem(Material.ELYTRA, "旁观模式", "Spectator", List.of("/gmsp"), List.of("/gmsp")));
        inventory.setItem(19, this.menuItem(Material.SUNFLOWER, "白天", "Day", List.of("/day"), List.of("/day")));
        inventory.setItem(20, this.menuItem(Material.BLACK_BED, "夜晚", "Night", List.of("/night"), List.of("/night")));
        inventory.setItem(21, this.menuItem(Material.YELLOW_DYE, "晴天", "Sun", List.of("/sun"), List.of("/sun")));
        inventory.setItem(22, this.menuItem(Material.WATER_BUCKET, "下雨", "Rain", List.of("/rain"), List.of("/rain")));
        inventory.setItem(23, this.menuItem(Material.LIGHTNING_ROD, "雷暴", "Thunder", List.of("/thunder"), List.of("/thunder")));
        inventory.setItem(24, this.menuItem(Material.CHAINMAIL_HELMET, "戴帽子", "Hat", List.of("/hat"), List.of("/hat")));
        inventory.setItem(25, this.menuItem(Material.COMPASS, "出生点", "Spawn", List.of("/spawn"), List.of("/spawn")));
        inventory.setItem(28, this.menuItem(Material.RED_BED, "设置出生点", "Set Spawn", List.of("/setspawn"), List.of("/setspawn")));
        inventory.setItem(29, this.menuItem(Material.CLOCK, "返回上一位置", "Back", List.of("/back"), List.of("/back")));
        inventory.setItem(30, this.menuItem(Material.CRAFTING_TABLE, "随身工作台", "Craft", List.of("/craft"), List.of("/craft")));
        inventory.setItem(31, this.menuItem(Material.ENDER_CHEST, "末影箱", "Ender Chest", List.of("/enderchest"), List.of("/enderchest")));
        inventory.setItem(32, this.menuItem(Material.CHEST, "垃圾桶", "Trash", List.of("/trash"), List.of("/trash")));
        inventory.setItem(33, this.menuItem(Material.BELL, "广播", "Broadcast", List.of("点击后在聊天栏输入广播内容"), List.of("Type broadcast text in chat after clicking")));
        inventory.setItem(34, this.menuItem(Material.BARRIER, "清屏", "Clear Chat", List.of("/clearchat"), List.of("/clearchat")));
        inventory.setItem(39, this.menuItem(Material.NETHER_STAR, "称号", "Titles", List.of("/title"), List.of("/title")));
        inventory.setItem(40, this.menuItem(Material.PLAYER_HEAD, "个人资料", "Profile", List.of("/profile"), List.of("/profile")));
        inventory.setItem(41, this.menuItem(Material.LEVER, "设置", "Settings", List.of("/settings"), List.of("/settings")));
        inventory.setItem(42, this.menuItem(Material.KNOWLEDGE_BOOK, "全部指令", "Command Center", List.of("打开完整指令 GUI"), List.of("Open the full command GUI")));
        if (player.hasPermission("huntertools.command.admin")) {
            inventory.setItem(43, this.menuItem(Material.COMMAND_BLOCK, "管理中心", "Admin", List.of("/admin"), List.of("/admin")));
        }
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/menu"), List.of("/menu")));
        player.openInventory(inventory);
    }

    private boolean openProfileWorkbench(final CommandSender sender) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage(ChatColor.RED + this.text("只有玩家可以打开个人资料界面。", "Only players can open the profile GUI."));
            return true;
        }
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.PROFILE, null, null, null), 54, this.guiTitle(GuiPage.PROFILE, null, null));
        final Location location = player.getLocation();
        final int playTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        final long playMinutes = Math.max(0L, playTicks / 20L / 60L);
        inventory.setItem(4, this.menuItem(Material.PLAYER_HEAD, player.getName(), player.getName(), List.of(
            "等级 " + player.getLevel() + " | 经验 " + Math.round(player.getExp() * 100.0F) + "%",
            "生命 " + Math.round(player.getHealth()) + "/" + Math.round(this.maxHealth(player)),
            "饱食 " + player.getFoodLevel() + "/20",
            "世界 " + player.getWorld().getName(),
            "坐标 " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(),
            "在线时长 " + playMinutes + " 分钟"
        ), List.of(
            "Level " + player.getLevel() + " | Exp " + Math.round(player.getExp() * 100.0F) + "%",
            "Health " + Math.round(player.getHealth()) + "/" + Math.round(this.maxHealth(player)),
            "Food " + player.getFoodLevel() + "/20",
            "World " + player.getWorld().getName(),
            "XYZ " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(),
            "Play time " + playMinutes + "m"
        )));
        inventory.setItem(20, this.menuItem(Material.EXPERIENCE_BOTTLE, "进度", "Progress", List.of("延迟 " + player.getPing() + "ms", "跳跃 " + player.getStatistic(Statistic.JUMP)), List.of("Ping " + player.getPing() + "ms", "Jump " + player.getStatistic(Statistic.JUMP))));
        inventory.setItem(21, this.menuItem(Material.TOTEM_OF_UNDYING, "生存状态", "Survival", List.of("击杀 " + player.getStatistic(Statistic.PLAYER_KILLS), "死亡 " + player.getStatistic(Statistic.DEATHS)), List.of("Kills " + player.getStatistic(Statistic.PLAYER_KILLS), "Deaths " + player.getStatistic(Statistic.DEATHS))));
        inventory.setItem(22, this.menuItem(Material.MAP, "位置", "Position", List.of(player.getWorld().getName(), location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()), List.of(player.getWorld().getName(), location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ())));
        inventory.setItem(37, this.menuItem(Material.CHEST, "背包预览", "Inventory Preview", List.of("只读预览"), List.of("Read only inventory preview")));
        inventory.setItem(38, this.menuItem(Material.ENDER_CHEST, "末影箱", "Ender Chest", List.of("打开末影箱"), List.of("Open ender chest")));
        inventory.setItem(39, this.menuItem(Material.RED_BED, "我的家", "Homes", List.of("打开 Home 工作台"), List.of("Open homes workbench")));
        inventory.setItem(40, this.menuItem(Material.ENDER_PEARL, "传送中心", "Teleport", List.of("打开传送工作台"), List.of("Open teleport workbench")));
        inventory.setItem(41, this.menuItem(Material.ARMOR_STAND, "假人", "PlayerBots", List.of("打开假人工作台"), List.of("Open fake player workbench")));
        inventory.setItem(42, this.menuItem(Material.VILLAGER_SPAWN_EGG, "NPC", "NPCs", List.of("打开 NPC 工作台"), List.of("Open NPC workbench")));
        inventory.setItem(43, this.menuItem(Material.LEVER, "设置", "Settings", List.of("打开设置工作台"), List.of("Open settings workbench")));
        inventory.setItem(44, this.menuItem(Material.NETHER_STAR, "称号", "Titles", List.of("切换和查看当前称号", "聊天 / 头顶 / TAB"), List.of("Manage your active title", "Chat / nametag / tab")));
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/menu"), List.of("/menu")));
        player.openInventory(inventory);
        return true;
    }

    private boolean openSettingsWorkbench(final CommandSender sender) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage(ChatColor.RED + this.text("只有玩家可以打开设置界面。", "Only players can open the settings GUI."));
            return true;
        }
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.SETTINGS, null, null, null), 27, this.guiTitle(GuiPage.SETTINGS, null, null));
        inventory.setItem(10, this.menuItem(Material.ENDER_PEARL, "TPA 开关", "Toggle TPA", List.of("开启或关闭接收传送请求"), List.of("Enable or disable incoming requests")));
        inventory.setItem(11, this.menuItem(Material.NETHER_STAR, "称号", "Titles", List.of("查看和切换当前称号"), List.of("Browse and switch your active title")));
        inventory.setItem(12, this.menuItem(Material.NAME_TAG, "语言", "Language", List.of("切换 HunterCore 双语界面"), List.of("Toggle HunterCore UI language")));
        inventory.setItem(14, this.menuItem(Material.FILLED_MAP, "网页面板", "Web Panel", List.of(this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088")), List.of(this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088"))));
        inventory.setItem(16, this.menuItem(Material.ENCHANTED_BOOK, "故事模式", "Story Mode", List.of("打开故事模式工作台"), List.of("Open story workbench")));
        inventory.setItem(22, this.menuItem(Material.ARROW, "返回", "Back", List.of("/menu"), List.of("/menu")));
        player.openInventory(inventory);
        return true;
    }

    private boolean openAdminWorkbench(final CommandSender sender) {
        if (!(sender instanceof final Player player)) {
            return this.admin(sender, new String[0]);
        }
        if (!this.require(player, "huntertools.command.admin")) {
            return true;
        }
        final Runtime runtime = Runtime.getRuntime();
        final long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        final long maxMb = runtime.maxMemory() / 1024L / 1024L;
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN, null, null, null), 54, this.guiTitle(GuiPage.ADMIN, null, null));
        inventory.setItem(4, this.menuItem(Material.COMMAND_BLOCK, "运行状态", "Runtime", List.of(
            "在线 " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(),
            "TPS " + String.format(Locale.ROOT, "%.2f", this.snapshot.tps1()),
            "MSPT " + String.format(Locale.ROOT, "%.2f", this.snapshot.mspt()),
            "内存 " + usedMb + "/" + maxMb + " MB"
        ), List.of(
            "Online " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(),
            "TPS " + String.format(Locale.ROOT, "%.2f", this.snapshot.tps1()),
            "MSPT " + String.format(Locale.ROOT, "%.2f", this.snapshot.mspt()),
            "Memory " + usedMb + "/" + maxMb + " MB"
        )));
        inventory.setItem(19, this.menuItem(Material.PLAYER_HEAD, "假人", "PlayerBots", List.of("打开假人工作台"), List.of("Open fake player workbench")));
        inventory.setItem(20, this.menuItem(Material.VILLAGER_SPAWN_EGG, "NPC", "NPCs", List.of("打开 NPC 工作台"), List.of("Open NPC workbench")));
        inventory.setItem(21, this.menuItem(Material.ENCHANTED_BOOK, "故事模式", "Story", List.of("打开故事模式工作台"), List.of("Open story workbench")));
        inventory.setItem(22, this.menuItem(Material.PAPER, "插件状态", "Plugins", List.of("/hc admin plugins"), List.of("/hc admin plugins")));
        inventory.setItem(23, this.menuItem(Material.BEACON, "AI 设置", "AI Settings", List.of("模型、Key、聊天、NPC、测试"), List.of("Model, key, chat, NPC and test")));
        inventory.setItem(24, this.menuItem(Material.REDSTONE, "TPS / 内存", "TPS / Memory", List.of("/htps", "/hc admin memory"), List.of("/htps", "/hc admin memory")));
        inventory.setItem(25, this.menuItem(Material.TARGET, "系统", "System", List.of("运行时、内存、线程、工作器"), List.of("Runtime, memory, workers, threads")));
        inventory.setItem(26, this.menuItem(Material.COMPARATOR, "模块", "Modules", List.of("切换 HunterCore 模块"), List.of("Toggle HunterCore modules")));
        inventory.setItem(29, this.menuItem(Material.BELL, "广播", "Broadcast", List.of("受控聊天输入"), List.of("Controlled chat input")));
        inventory.setItem(30, this.menuItem(Material.SUNFLOWER, "白天晴天", "Day + Clear", List.of("设置白天并清除天气"), List.of("Set day and clear weather")));
        inventory.setItem(31, this.menuItem(Material.LIGHTNING_ROD, "雷暴", "Thunder", List.of("当前世界设置雷暴"), List.of("Set thunder in current world")));
        inventory.setItem(32, this.menuItem(Material.REPEATER, "重载核心", "Reload Core", List.of("重载 HunterCore 偏好"), List.of("Reload HunterCore preferences")));
        inventory.setItem(33, this.menuItem(Material.BLAZE_POWDER, "强制 GC", "Force GC", List.of("执行 /hc admin gc"), List.of("Run /hc admin gc")));
        inventory.setItem(34, this.menuItem(Material.SPYGLASS, "网页面板", "Web Panel", List.of("监听、端口、地图、用户"), List.of("Bind, port, map and users")));
        inventory.setItem(38, this.menuItem(Material.WRITABLE_BOOK, "聊天举报保护", "Chat Reports", List.of("核心聊天举报保护设置", "开箱即用"), List.of("Built-in chat report protection", "Ready out of the box")));
        inventory.setItem(39, this.menuItem(Material.BOOK, "偏好摘要", "Preferences", List.of("只读 HunterCore 偏好摘要"), List.of("Readonly HunterCore preferences summary")));
        inventory.setItem(40, this.menuItem(Material.OBSERVER, "优化模式", "Optimize", List.of("选择 CPU 优化模式"), List.of("Choose CPU optimization mode")));
        inventory.setItem(41, this.menuItem(Material.NETHER_STAR, "称号", "Titles", List.of("启用模块并为玩家分配称号"), List.of("Toggle module and assign titles to players")));
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/menu"), List.of("/menu")));
        player.openInventory(inventory);
        return true;
    }

    private void handleProfileWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case CHEST -> this.openInventoryPreviewWorkbench(player);
            case ENDER_CHEST -> player.performCommand("enderchest");
            case RED_BED -> player.performCommand("homes");
            case ENDER_PEARL -> player.performCommand("tpgui");
            case ARMOR_STAND -> this.openActorListWorkbench(player, REAL_FAKE_PLAYERS);
            case VILLAGER_SPAWN_EGG -> this.openActorListWorkbench(player, NPCS);
            case LEVER -> this.openSettingsWorkbench(player);
            case NETHER_STAR -> this.openTitleWorkbench(player);
            case ARROW -> this.openMainMenuWorkbench(player);
            default -> {
            }
        }
    }

    private void handleSettingsWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case ENDER_PEARL -> player.performCommand("tptoggle");
            case NETHER_STAR -> this.openTitleWorkbench(player);
            case NAME_TAG -> this.toggleLanguage(player);
            case FILLED_MAP -> player.sendMessage(ChatColor.AQUA + this.text("网页面板: ", "Web Panel: ") + ChatColor.WHITE + this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088"));
            case ENCHANTED_BOOK -> this.openStoryWorkbench(player);
            case ARROW -> this.openMainMenuWorkbench(player);
            default -> player.sendMessage(ChatColor.GRAY + this.text("这个设置项还在完善中。", "This settings entry is reserved for future work."));
        }
    }

    private void handleToolsWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case GOLDEN_APPLE -> player.performCommand("heal");
            case COOKED_BEEF -> player.performCommand("feed");
            case FEATHER -> player.performCommand("fly");
            case SUGAR -> this.beginGuiChat(player, new GuiChatSession("speed", null, null),
                this.text("请输入速度 1-10。", "Type a speed from 1 to 10."),
                this.text("输入 cancel 取消。", "Type 'cancel' to abort."));
            case LEATHER_HELMET -> player.performCommand("gms");
            case DIAMOND_BLOCK -> player.performCommand("gmc");
            case ELYTRA -> player.performCommand("gmsp");
            case SUNFLOWER -> player.performCommand("day");
            case BLACK_BED -> player.performCommand("night");
            case YELLOW_DYE -> player.performCommand("sun");
            case WATER_BUCKET -> player.performCommand("rain");
            case LIGHTNING_ROD -> player.performCommand("thunder");
            case CHAINMAIL_HELMET -> player.performCommand("hat");
            case COMPASS -> player.performCommand("spawn");
            case RED_BED -> player.performCommand("setspawn");
            case CLOCK -> player.performCommand("back");
            case CRAFTING_TABLE -> player.performCommand("craft");
            case ENDER_CHEST -> player.performCommand("enderchest");
            case CHEST -> player.performCommand("trash");
            case BELL -> this.beginGuiChat(player, new GuiChatSession("broadcast-tools", null, null),
                this.text("请输入广播内容。", "Type the broadcast message."),
                this.text("输入 cancel 取消。", "Type 'cancel' to abort."));
            case BARRIER -> player.performCommand("clearchat");
            case NETHER_STAR -> this.openTitleWorkbench(player);
            case PLAYER_HEAD -> this.openProfileWorkbench(player);
            case LEVER -> this.openSettingsWorkbench(player);
            case KNOWLEDGE_BOOK -> this.openCommandCenterWorkbench(player, "tools");
            case COMMAND_BLOCK -> this.openAdminWorkbench(player);
            case ARROW -> this.openMainMenuWorkbench(player);
            default -> {
            }
        }
    }

    private void openCommandCenterWorkbench(final Player player, final String category) {
        final String normalized = commandCenterCategory(category);
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.COMMAND_CENTER, null, null, normalized), 54, this.guiTitle(GuiPage.COMMAND_CENTER, null, null));
        inventory.setItem(0, this.commandCategoryItem("tools", normalized, Material.IRON_SWORD, "工具", "Tools", "HunterTools / essentials"));
        inventory.setItem(1, this.commandCategoryItem("teleport", normalized, Material.ENDER_PEARL, "传送", "Teleport", "TPA / homes / RTP"));
        inventory.setItem(2, this.commandCategoryItem("admin", normalized, Material.COMMAND_BLOCK, "管理", "Admin", "/hc admin"));
        inventory.setItem(3, this.commandCategoryItem("actors", normalized, Material.ARMOR_STAND, "假人与 NPC", "Actors", "/player /npc"));
        inventory.setItem(4, this.commandCategoryItem("story", normalized, Material.NETHER_STAR, "称号与故事", "Titles & Story", "/title /story"));

        final List<GuiCommand> commands = this.commandCenterCommands(normalized);
        for (int index = 0; index < commands.size() && index < 36; index++) {
            final GuiCommand command = commands.get(index);
            inventory.setItem(9 + index, this.menuItem(
                command.material(),
                command.zhName(),
                command.enName(),
                command.zhLore(),
                command.enLore()
            ));
        }
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/menu"), List.of("/menu")));
        player.openInventory(inventory);
    }

    private ItemStack commandCategoryItem(
        final String category,
        final String active,
        final Material material,
        final String zhName,
        final String enName,
        final String line
    ) {
        return this.menuItem(
            active.equals(category) ? Material.LIME_DYE : material,
            zhName,
            enName,
            List.of(line, active.equals(category) ? "当前分类" : "点击切换"),
            List.of(line, active.equals(category) ? "Current category" : "Click to switch")
        );
    }

    private void handleCommandCenterWorkbenchClick(final Player player, final ItemStack clicked, final GuiHolder holder, final int slot) {
        if (slot >= 0 && slot <= 4) {
            final String next = switch (slot) {
                case 0 -> "tools";
                case 1 -> "teleport";
                case 2 -> "admin";
                case 3 -> "actors";
                case 4 -> "story";
                default -> commandCenterCategory(holder.action());
            };
            this.openCommandCenterWorkbench(player, next);
            return;
        }
        if (clicked.getType() == Material.ARROW) {
            this.openMainMenuWorkbench(player);
            return;
        }
        if (slot < 9 || slot >= 45) {
            return;
        }
        final String category = commandCenterCategory(holder.action());
        final int index = slot - 9;
        final List<GuiCommand> commands = this.commandCenterCommands(category);
        if (index < 0 || index >= commands.size()) {
            return;
        }
        final GuiCommand command = commands.get(index);
        if (command.prompt()) {
            this.beginGuiChat(player, new GuiChatSession("gui-command", command.command(), category),
                this.text("请输入 /" + command.command() + " 后面的参数；输入 - 表示不带参数执行。", "Type the arguments after /" + command.command() + "; type - to run with no arguments."),
                this.text(command.zhPrompt(), command.enPrompt()));
            return;
        }
        if (command.command().startsWith("gui:")) {
            this.openGuiCommandTarget(player, command.command());
            return;
        }
        player.performCommand(command.command());
    }

    private void openGuiCommandTarget(final Player player, final String command) {
        switch (command) {
            case "gui:admin" -> this.openAdminWorkbench(player);
            case "gui:modules" -> this.openAdminModulesWorkbench(player);
            case "gui:commands" -> this.openAdminCommandsWorkbench(player, ESSENTIALS);
            case "gui:optimize" -> this.openAdminOptimizeWorkbench(player);
            case "gui:ncr" -> this.openAdminChatReportsWorkbench(player);
            case "gui:motd" -> this.openAdminMotdWorkbench(player);
            case "gui:web" -> this.openAdminWebWorkbench(player);
            case "gui:ai" -> this.openAdminAiWorkbench(player);
            case "gui:playerbots" -> this.openActorListWorkbench(player, REAL_FAKE_PLAYERS);
            case "gui:npcs" -> this.openActorListWorkbench(player, NPCS);
            case "gui:story" -> this.openStoryWorkbench(player);
            case "gui:title" -> this.openTitleWorkbench(player);
            default -> this.openCommandCenterWorkbench(player, "admin");
        }
    }

    private List<GuiCommand> commandCenterCommands(final String category) {
        return switch (commandCenterCategory(category)) {
            case "teleport" -> List.of(
                direct(Material.ENDER_PEARL, "TPA 面板", "TPA GUI", "tpgui"),
                prompted(Material.ENDER_EYE, "请求传送到玩家", "TPA", "tpa", "输入玩家名。", "Type a player name."),
                prompted(Material.LEAD, "请求玩家传送过来", "TPA Here", "tpahere", "输入玩家名。", "Type a player name."),
                direct(Material.LIME_DYE, "接受请求", "Accept", "tpaccept"),
                direct(Material.BARRIER, "拒绝请求", "Deny", "tpdeny"),
                direct(Material.GRAY_DYE, "取消请求", "Cancel", "tpcancel"),
                direct(Material.LEVER, "TPA 开关", "Toggle TPA", "tptoggle"),
                direct(Material.RED_BED, "家列表", "Homes", "homes"),
                direct(Material.OAK_DOOR, "家 GUI", "Home GUI", "homegui"),
                prompted(Material.WHITE_BED, "回家", "Home", "home", "输入家名称，或 - 使用默认。", "Type a home name, or - for default."),
                prompted(Material.LIME_BED, "设置家", "Set Home", "sethome", "输入家名称。", "Type a home name."),
                prompted(Material.RED_BED, "删除家", "Delete Home", "delhome", "输入家名称。", "Type a home name."),
                direct(Material.GRASS_BLOCK, "随机传送", "RTP", "rtp"),
                direct(Material.COMPASS, "出生点", "Spawn", "spawn")
            );
            case "admin" -> List.of(
                directGui(Material.COMMAND_BLOCK, "管理 GUI", "Admin GUI", "gui:admin"),
                direct(Material.PAPER, "帮助", "Help", "hc admin help"),
                direct(Material.REPEATER, "重载", "Reload", "hc admin reload"),
                directGui(Material.COMPARATOR, "模块列表", "Modules", "gui:modules"),
                directGui(Material.REDSTONE_TORCH, "指令开关", "Command Toggle", "gui:commands"),
                direct(Material.BOOK, "插件状态", "Plugins", "hc admin plugins"),
                direct(Material.REDSTONE, "内存", "Memory", "hc admin memory"),
                direct(Material.BLAZE_POWDER, "强制 GC", "GC", "hc admin gc"),
                direct(Material.REPEATER, "线程", "Threads", "hc admin threads"),
                directGui(Material.OBSERVER, "优化", "Optimize", "gui:optimize"),
                directGui(Material.WRITABLE_BOOK, "聊天举报保护", "Chat Reports", "gui:ncr"),
                directGui(Material.OAK_SIGN, "MOTD", "MOTD", "gui:motd"),
                directGui(Material.SPYGLASS, "网页面板", "Web Panel", "gui:web"),
                directGui(Material.BEACON, "AI", "AI", "gui:ai")
            );
            case "actors" -> List.of(
                directGui(Material.ARMOR_STAND, "假人 GUI", "PlayerBot GUI", "gui:playerbots"),
                prompted(Material.LIME_DYE, "生成假人", "Spawn PlayerBot", "player spawn", "输入假人名。", "Type a bot name."),
                prompted(Material.RED_WOOL, "删除假人", "Remove PlayerBot", "player remove", "输入假人名。", "Type a bot name."),
                prompted(Material.CHEST, "查看假人背包", "PlayerBot Inventory", "player inv", "输入假人名。", "Type a bot name."),
                prompted(Material.PLAYER_HEAD, "假人皮肤", "PlayerBot Skin", "player skin", "输入：假人名 皮肤名。", "Type: bot skin."),
                prompted(Material.ENDER_PEARL, "传送到假人", "TP To PlayerBot", "player tp", "输入假人名。", "Type a bot name."),
                prompted(Material.COMPASS, "假人到这里", "PlayerBot Here", "player tphere", "输入假人名。", "Type a bot name."),
                prompted(Material.SPYGLASS, "假人看向", "PlayerBot Look", "player look", "输入：假人名 x y z。", "Type: bot x y z."),
                prompted(Material.FEATHER, "假人移动/动作", "PlayerBot Action", "player move", "输入移动或动作参数。", "Type movement/action arguments."),
                prompted(Material.DIAMOND_SWORD, "假人攻击/使用", "PlayerBot Use", "player attack", "输入假人名，或改用 use/drop 等子命令参数。", "Type a bot name, or use use/drop style arguments."),
                prompted(Material.LEVER, "假人 AI", "PlayerBot AI", "player ai", "输入：假人名 on/off/goal。", "Type: bot on/off/goal."),
                prompted(Material.PAPER, "假人详情", "PlayerBot Info", "player info", "输入假人名。", "Type a bot name."),
                direct(Material.BARRIER, "清空假人", "Clear PlayerBots", "player clear"),
                directGui(Material.VILLAGER_SPAWN_EGG, "NPC GUI", "NPC GUI", "gui:npcs"),
                prompted(Material.EMERALD, "生成 NPC", "Spawn NPC", "npc spawn", "输入 NPC 名。", "Type an NPC name."),
                prompted(Material.REDSTONE_BLOCK, "删除 NPC", "Remove NPC", "npc remove", "输入 NPC 名。", "Type an NPC name."),
                prompted(Material.NAME_TAG, "NPC 皮肤", "NPC Skin", "npc skin", "输入：NPC 名 皮肤名。", "Type: NPC skin."),
                prompted(Material.ENDER_EYE, "传送到 NPC", "TP To NPC", "npc tp", "输入 NPC 名。", "Type an NPC name."),
                prompted(Material.COMPASS, "NPC 到这里", "NPC Here", "npc tphere", "输入 NPC 名。", "Type an NPC name."),
                prompted(Material.SPYGLASS, "NPC 朝向", "NPC Look", "npc look", "输入：NPC 名 x y z。", "Type: NPC x y z."),
                prompted(Material.ARMOR_STAND, "NPC 姿态", "NPC Pose", "npc pose", "输入：NPC 名 姿态。", "Type: NPC pose."),
                prompted(Material.COMMAND_BLOCK, "NPC 点击命令", "NPC Click Command", "npc click", "输入：NPC 名 command/clear。", "Type: NPC command/clear."),
                prompted(Material.PAPER, "NPC 详情", "NPC Info", "npc info", "输入 NPC 名。", "Type an NPC name."),
                direct(Material.TNT, "清空 NPC", "Clear NPCs", "npc clear")
            );
            case "story" -> List.of(
                directGui(Material.NETHER_STAR, "称号 GUI", "Title GUI", "gui:title"),
                direct(Material.PAPER, "称号列表", "Title List", "title list"),
                prompted(Material.LIME_DYE, "激活称号", "Activate Title", "title activate", "输入称号 ID。", "Type a title id."),
                direct(Material.BARRIER, "清空称号", "Clear Title", "title clear"),
                direct(Material.LEVER, "显示开关", "Toggle Title", "title toggle"),
                prompted(Material.WRITABLE_BOOK, "创建称号", "Create Title", "title create", "输入：id 显示名 前缀。", "Type: id display prefix."),
                prompted(Material.REDSTONE_BLOCK, "删除称号", "Delete Title", "title delete", "输入称号 ID。", "Type a title id."),
                prompted(Material.GOLD_INGOT, "授予称号", "Grant Title", "title grant", "输入：玩家 称号ID。", "Type: player titleId."),
                prompted(Material.IRON_INGOT, "收回称号", "Revoke Title", "title revoke", "输入：玩家 称号ID。", "Type: player titleId."),
                prompted(Material.SPYGLASS, "预览称号", "Preview Title", "title preview", "输入：称号ID 或 玩家 称号ID。", "Type: titleId or player titleId."),
                directGui(Material.ENCHANTED_BOOK, "故事 GUI", "Story GUI", "gui:story"),
                direct(Material.EMERALD_BLOCK, "开始故事", "Start Story", "start"),
                direct(Material.LIME_DYE, "启用故事", "Enable Story", "story enable"),
                direct(Material.ORANGE_DYE, "跳过段落", "Skip Story", "story skip"),
                direct(Material.RED_DYE, "停止故事", "Stop Story", "story stop"),
                prompted(Material.OAK_SIGN, "故事台词", "Story Line", "story line", "输入：auto 或具体台词内容。", "Type: auto or a line of text."),
                prompted(Material.TNT, "故事实验", "Story Meltdown", "story meltdown", "危险操作；输入确认参数。", "Dangerous action; type confirmation arguments.")
            );
            default -> List.of(
                direct(Material.REDSTONE, "TPS", "TPS", "htps"),
                prompted(Material.GOLDEN_APPLE, "治疗", "Heal", "heal", "输入玩家名，或 - 治疗自己。", "Type a player name, or - for yourself."),
                prompted(Material.COOKED_BEEF, "饱食", "Feed", "feed", "输入玩家名，或 - 补满自己。", "Type a player name, or - for yourself."),
                prompted(Material.FEATHER, "飞行", "Fly", "fly", "输入：玩家 on/off，或 - 切换自己。", "Type: player on/off, or - for yourself."),
                prompted(Material.DIAMOND_BLOCK, "游戏模式", "Gamemode", "gm", "输入：survival/creative/adventure/spectator [玩家]。", "Type: survival/creative/adventure/spectator [player]."),
                prompted(Material.LEATHER_HELMET, "生存", "Survival", "gms", "输入玩家名，或 - 作用自己。", "Type a player name, or - for yourself."),
                prompted(Material.COMMAND_BLOCK, "创造", "Creative", "gmc", "输入玩家名，或 - 作用自己。", "Type a player name, or - for yourself."),
                prompted(Material.IRON_CHESTPLATE, "冒险", "Adventure", "gma", "输入玩家名，或 - 作用自己。", "Type a player name, or - for yourself."),
                prompted(Material.ELYTRA, "旁观", "Spectator", "gmsp", "输入玩家名，或 - 作用自己。", "Type a player name, or - for yourself."),
                prompted(Material.SUNFLOWER, "白天", "Day", "day", "输入世界名，或 - 当前世界。", "Type a world name, or - for current."),
                prompted(Material.BLACK_BED, "夜晚", "Night", "night", "输入世界名，或 - 当前世界。", "Type a world name, or - for current."),
                prompted(Material.YELLOW_DYE, "晴天", "Sun", "sun", "输入世界名，或 - 当前世界。", "Type a world name, or - for current."),
                prompted(Material.WATER_BUCKET, "下雨", "Rain", "rain", "输入世界名，或 - 当前世界。", "Type a world name, or - for current."),
                prompted(Material.LIGHTNING_ROD, "雷暴", "Thunder", "thunder", "输入世界名，或 - 当前世界。", "Type a world name, or - for current."),
                prompted(Material.BELL, "广播", "Broadcast", "broadcast", "输入广播内容。", "Type the broadcast message."),
                direct(Material.BARRIER, "清屏", "Clear Chat", "clearchat"),
                prompted(Material.SUGAR, "速度", "Speed", "speed", "输入：1-10 [玩家] [walk/fly]。", "Type: 1-10 [player] [walk/fly]."),
                prompted(Material.COMPASS, "出生点", "Spawn", "spawn", "输入玩家名，或 - 传送自己。", "Type a player name, or - for yourself."),
                direct(Material.RED_BED, "设置出生点", "Set Spawn", "setspawn"),
                direct(Material.CLOCK, "返回", "Back", "back"),
                direct(Material.CHAINMAIL_HELMET, "戴帽子", "Hat", "hat"),
                direct(Material.CRAFTING_TABLE, "工作台", "Craft", "craft"),
                prompted(Material.ENDER_CHEST, "末影箱", "Ender Chest", "enderchest", "输入玩家名，或 - 打开自己。", "Type a player name, or - for yourself."),
                direct(Material.CHEST, "垃圾桶", "Trash", "trash"),
                direct(Material.BOOK, "主菜单", "Menu", "menu"),
                direct(Material.PLAYER_HEAD, "个人资料", "Profile", "profile"),
                direct(Material.LEVER, "设置", "Settings", "settings"),
                direct(Material.COMMAND_BLOCK, "管理", "Admin", "admin"),
                prompted(Material.ARMOR_STAND, "假人", "Player", "player", "输入 help 或任意 /player 子命令参数。", "Type help or any /player subcommand arguments."),
                prompted(Material.VILLAGER_SPAWN_EGG, "NPC", "NPC", "npc", "输入 help 或任意 /npc 子命令参数。", "Type help or any /npc subcommand arguments."),
                direct(Material.EMERALD_BLOCK, "开始", "Start", "start"),
                prompted(Material.ENCHANTED_BOOK, "故事", "Story", "story", "输入 start/status/skip/stop/line/meltdown。", "Type start/status/skip/stop/line/meltdown."),
                prompted(Material.NETHER_STAR, "称号", "Title", "title", "输入 list/activate/clear/toggle/create/delete/grant/revoke/preview。", "Type list/activate/clear/toggle/create/delete/grant/revoke/preview.")
            );
        };
    }

    private static String commandCenterCategory(@Nullable final String category) {
        final String normalized = category == null ? "" : category.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "teleport", "admin", "actors", "story" -> normalized;
            default -> "tools";
        };
    }

    private static List<String> commandGateModules() {
        return List.of(ESSENTIALS, MANAGEMENT, FAKE_PLAYERS, REAL_FAKE_PLAYERS, NPCS);
    }

    private static String commandGateModule(@Nullable final String module) {
        final String normalized = module == null ? "" : HunterToolsPreferences.normalize(module);
        return commandGateModules().contains(normalized) ? normalized : ESSENTIALS;
    }

    private static List<String> commandGateCommands(final String module) {
        return switch (commandGateModule(module)) {
            case MANAGEMENT -> HunterToolsPreferences.managementCommands();
            case FAKE_PLAYERS, NPCS -> HunterToolsPreferences.actorCommands();
            case REAL_FAKE_PLAYERS -> HunterToolsPreferences.realFakePlayerCommands();
            default -> HunterToolsPreferences.essentialsCommands();
        };
    }

    private static GuiCommand direct(final Material material, final String zhName, final String enName, final String command) {
        return new GuiCommand(material, zhName, enName, List.of("/" + command), List.of("/" + command), command, false, "", "");
    }

    private static GuiCommand directGui(final Material material, final String zhName, final String enName, final String command) {
        return new GuiCommand(material, zhName, enName, List.of("打开图形设置页"), List.of("Open GUI settings page"), command, false, "", "");
    }

    private static GuiCommand prompted(
        final Material material,
        final String zhName,
        final String enName,
        final String command,
        final String zhPrompt,
        final String enPrompt
    ) {
        return new GuiCommand(
            material,
            zhName,
            enName,
            List.of("/" + command + " ...", "点击后输入参数"),
            List.of("/" + command + " ...", "Click and type arguments"),
            command,
            true,
            zhPrompt,
            enPrompt
        );
    }

    private void handleAdminWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case PLAYER_HEAD -> this.openActorListWorkbench(player, REAL_FAKE_PLAYERS);
            case VILLAGER_SPAWN_EGG -> this.openActorListWorkbench(player, NPCS);
            case ENCHANTED_BOOK -> this.openStoryWorkbench(player);
            case PAPER -> this.openAdminPluginsWorkbench(player);
            case BEACON -> this.openAdminAiWorkbench(player);
            case REDSTONE -> this.openAdminSystemWorkbench(player);
            case TARGET -> this.openAdminSystemWorkbench(player);
            case COMPARATOR -> this.openAdminModulesWorkbench(player);
            case BELL -> this.beginGuiChat(player, new GuiChatSession("broadcast", null, null),
                this.text("请直接在聊天栏输入广播内容。", "Type the broadcast message in chat."),
                this.text("输入 cancel 取消。", "Type 'cancel' to abort."));
            case SUNFLOWER -> {
                this.time(player, "day", new String[0]);
                this.weather(player, "sun", new String[0]);
            }
            case LIGHTNING_ROD -> this.weather(player, "thunder", new String[0]);
            case REPEATER -> this.adminReload(player);
            case BLAZE_POWDER -> player.performCommand("hc admin gc");
            case SPYGLASS -> this.openAdminWebWorkbench(player);
            case WRITABLE_BOOK -> this.openAdminChatReportsWorkbench(player);
            case BOOK -> this.openAdminPreferencesWorkbench(player);
            case OBSERVER -> this.openAdminOptimizeWorkbench(player);
            case NETHER_STAR -> this.openTitleWorkbench(player);
            case ARROW -> this.openMainMenuWorkbench(player);
            default -> {
            }
        }
    }

    private void openAdminPluginsWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_PLUGINS, null, null, null), 54, this.guiTitle(GuiPage.ADMIN_PLUGINS, null, null));
        inventory.setItem(4, this.menuItem(Material.PAPER, "插件状态", "Plugins", List.of(
            "已加载 " + Bukkit.getPluginManager().getPlugins().length + " 个插件",
            "点击条目可查看版本和主类"
        ), List.of(
            "Loaded " + Bukkit.getPluginManager().getPlugins().length + " plugins",
            "Entries show version and main class"
        )));
        int slot = 9;
        for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (slot >= 45) {
                break;
            }
            final boolean enabled = plugin.isEnabled();
            inventory.setItem(slot++, this.menuItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE, plugin.getName(), plugin.getName(), List.of(
                enabled ? "已启用" : "已关闭",
                "版本 " + plugin.getDescription().getVersion(),
                plugin.getDescription().getMain()
            ), List.of(
                enabled ? "Enabled" : "Disabled",
                "Version " + plugin.getDescription().getVersion(),
                plugin.getDescription().getMain()
            )));
        }
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminPluginsWorkbenchClick(final Player player, final ItemStack clicked) {
        if (clicked.getType() == Material.ARROW) {
            this.openAdminWorkbench(player);
        }
    }

    private void openAdminSystemWorkbench(final Player player) {
        final Runtime runtime = Runtime.getRuntime();
        final long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
        final long maxMb = runtime.maxMemory() / 1024L / 1024L;
        final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_SYSTEM, null, null, null), 27, this.guiTitle(GuiPage.ADMIN_SYSTEM, null, null));
        inventory.setItem(4, this.menuItem(Material.TARGET, "系统", "System", List.of(
            "在线 " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(),
            "TPS " + String.format(Locale.ROOT, "%.2f", this.snapshot.tps1()),
            "MSPT " + String.format(Locale.ROOT, "%.2f", this.snapshot.mspt())
        ), List.of(
            "Online " + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(),
            "TPS " + String.format(Locale.ROOT, "%.2f", this.snapshot.tps1()),
            "MSPT " + String.format(Locale.ROOT, "%.2f", this.snapshot.mspt())
        )));
        inventory.setItem(10, this.menuItem(Material.REDSTONE, "内存", "Memory", List.of(
            "已用 " + usedMb + " MB",
            "上限 " + maxMb + " MB",
            this.snapshot.memoryLine()
        ), List.of(
            "Used " + usedMb + " MB",
            "Max " + maxMb + " MB",
            this.snapshot.memoryLine()
        )));
        inventory.setItem(12, this.menuItem(Material.REPEATER, "线程", "Threads", List.of(
            "活动 " + bean.getThreadCount(),
            "守护 " + bean.getDaemonThreadCount(),
            "峰值 " + bean.getPeakThreadCount()
        ), List.of(
            "Live " + bean.getThreadCount(),
            "Daemon " + bean.getDaemonThreadCount(),
            "Peak " + bean.getPeakThreadCount()
        )));
        inventory.setItem(14, this.menuItem(Material.OBSERVER, "工作器", "Workers", List.of(
            "HunterTools " + this.preferences.intValue("optimizations.hunter-tools.render-workers", 4),
            "WebPanel " + this.preferences.intValue("optimizations.hunter-tools.web-panel-workers", 4),
            "CPU 模式 " + this.preferences.stringValue("optimizations.cpu.mode", "multi-thread")
        ), List.of(
            "HunterTools " + this.preferences.intValue("optimizations.hunter-tools.render-workers", 4),
            "WebPanel " + this.preferences.intValue("optimizations.hunter-tools.web-panel-workers", 4),
            "CPU mode " + this.preferences.stringValue("optimizations.cpu.mode", "multi-thread")
        )));
        inventory.setItem(16, this.menuItem(Material.ARMOR_STAND, "实体助手", "Actors", List.of(
            "假人 " + (this.realFakePlayerManager == null ? 0 : this.realFakePlayerManager.liveCount()),
            "NPC " + (this.actorManager == null ? 0 : this.actorManager.liveCount(NPCS))
        ), List.of(
            "PlayerBots " + (this.realFakePlayerManager == null ? 0 : this.realFakePlayerManager.liveCount()),
            "NPCs " + (this.actorManager == null ? 0 : this.actorManager.liveCount(NPCS))
        )));
        inventory.setItem(22, this.menuItem(Material.BLAZE_POWDER, "强制 GC", "Force GC", List.of("立即执行垃圾回收"), List.of("Run garbage collection now")));
        inventory.setItem(26, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminSystemWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case BLAZE_POWDER -> this.adminGc(player);
            case ARROW -> this.openAdminWorkbench(player);
            default -> {
            }
        }
    }

    private void openAdminModulesWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_MODULES, null, null, null), 54, this.guiTitle(GuiPage.ADMIN_MODULES, null, null));
        inventory.setItem(4, this.menuItem(Material.COMPARATOR, "模块", "Modules", List.of("点击模块切换启用状态"), List.of("Click a module slot to toggle on or off")));
        int slot = 9;
        for (final String module : MODULES) {
            if (slot >= 45) {
                break;
            }
            final boolean enabled = this.preferences.moduleEnabled(module);
            inventory.setItem(slot++, this.menuItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE, module, module, List.of(
                enabled ? "已启用" : "已停用",
                "点击切换"
            ), List.of(
                enabled ? "Enabled" : "Disabled",
                "Click to toggle"
            )));
        }
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminModulesWorkbenchClick(final Player player, final ItemStack clicked, final int slot) {
        if (clicked.getType() == Material.ARROW) {
            this.openAdminWorkbench(player);
            return;
        }
        if (slot < 9 || slot >= 45) {
            return;
        }
        final String module = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (!MODULES.contains(module)) {
            return;
        }
        final boolean currentlyEnabled = this.preferences.moduleEnabled(module);
        if (currentlyEnabled) {
            this.openConfirmWorkbench(player, "disable-module", module, null,
                this.text("确认关闭模块 " + module + " 吗？", "Disable module " + module + "?"),
                this.text("这可能立即影响功能可用性。", "This can immediately affect available features."));
            return;
        }
        this.preferences.setModuleEnabled(module, true);
        this.preferences.save(this.workerExecutor);
        this.startTasks();
        if (this.actorManager != null && (module.equals(FAKE_PLAYERS) || module.equals(NPCS))) {
            this.actorManager.reload();
        }
        if (this.webPanelManager != null && module.equals(WEB_PANEL)) {
            this.webPanelManager.restart();
        }
        player.sendMessage(ChatColor.GREEN + this.text("模块 ", "Module ") + module + this.text(" 已设置为 ", " set to ") + this.preferences.moduleEnabled(module) + ".");
        this.openAdminModulesWorkbench(player);
    }

    private void openAdminPreferencesWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_PREFERENCES, null, null, null), 54, this.guiTitle(GuiPage.ADMIN_PREFERENCES, null, null));
        inventory.setItem(4, this.menuItem(Material.BOOK, "偏好摘要", "Preferences", List.of(this.preferences.file().getName(), "只读状态摘要"), List.of(this.preferences.file().getName(), "Readonly summary of current HunterCore state")));
        inventory.setItem(19, this.menuItem(Material.SPYGLASS, "网页设置", "Web", List.of(
            this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1") + ":" + this.preferences.intValue("modules.web-panel.port", 8088),
            this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088")
        ), List.of(
            this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1") + ":" + this.preferences.intValue("modules.web-panel.port", 8088),
            this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088")
        )));
        inventory.setItem(20, this.menuItem(Material.PAPER, "MOTD", "MOTD", List.of(
            this.preferences.stringValue("modules.motd.line-1", ""),
            this.preferences.stringValue("modules.motd.line-2", "")
        ), List.of(
            this.preferences.stringValue("modules.motd.line-1", ""),
            this.preferences.stringValue("modules.motd.line-2", "")
        )));
        inventory.setItem(21, this.menuItem(Material.OBSERVER, "优化", "Optimize", List.of(
            this.preferences.stringValue("optimizations.cpu.mode", "multi-thread"),
            "工作器 " + this.preferences.intValue("optimizations.hunter-tools.render-workers", 4)
        ), List.of(
            this.preferences.stringValue("optimizations.cpu.mode", "multi-thread"),
            "Workers " + this.preferences.intValue("optimizations.hunter-tools.render-workers", 4)
        )));
        inventory.setItem(22, this.menuItem(Material.BEACON, "AI", "AI", List.of(
            "启用 " + this.preferences.moduleEnabled(AI),
            this.preferences.stringValue("modules.ai.model", "gpt-4o-mini")
        ), List.of(
            "Enabled " + this.preferences.moduleEnabled(AI),
            this.preferences.stringValue("modules.ai.model", "gpt-4o-mini")
        )));
        inventory.setItem(23, this.menuItem(Material.ENCHANTED_BOOK, "故事模式", "Story", List.of(
            "启用 " + this.preferences.booleanValue("modules.story-mode.enabled", false),
            "默认保持关闭"
        ), List.of(
            "Enabled " + this.preferences.booleanValue("modules.story-mode.enabled", false),
            "Default remains off"
        )));
        inventory.setItem(24, this.menuItem(Material.COMPARATOR, "模块", "Modules", List.of("打开模块切换工作台"), List.of("Open the module toggle workbench")));
        inventory.setItem(25, this.menuItem(Material.WRITABLE_BOOK, "聊天举报保护", "Chat Reports", List.of("核心 NoChatReports 设置"), List.of("Built-in NoChatReports settings")));
        inventory.setItem(28, this.menuItem(Material.REDSTONE_TORCH, "指令开关", "Command Gates", List.of("所有模块指令图形化开关"), List.of("GUI toggles for all module commands")));
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminPreferencesWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case SPYGLASS -> this.openAdminWebWorkbench(player);
            case PAPER -> this.openAdminMotdWorkbench(player);
            case COMPARATOR -> this.openAdminModulesWorkbench(player);
            case OBSERVER -> this.openAdminOptimizeWorkbench(player);
            case BEACON -> this.openAdminAiWorkbench(player);
            case WRITABLE_BOOK -> this.openAdminChatReportsWorkbench(player);
            case REDSTONE_TORCH -> this.openAdminCommandsWorkbench(player, ESSENTIALS);
            case ARROW -> this.openAdminWorkbench(player);
            default -> {
            }
        }
    }

    private void openAdminOptimizeWorkbench(final Player player) {
        final String current = this.preferences.stringValue("optimizations.cpu.mode", "multi-thread");
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_OPTIMIZE, null, null, null), 27, this.guiTitle(GuiPage.ADMIN_OPTIMIZE, null, null));
        inventory.setItem(4, this.menuItem(Material.OBSERVER, "CPU 优化", "CPU Optimize", List.of(
            "当前模式: " + current,
            "完整线程效果需要重启服务器"
        ), List.of(
            "Current mode: " + current,
            "Restart server for full core-thread effect"
        )));
        inventory.setItem(10, this.menuItem(Material.IRON_INGOT, "single-thread", "single-thread", List.of("稳定优先"), List.of("Stability-first mode")));
        inventory.setItem(12, this.menuItem(Material.CLOCK, "high-clock", "high-clock", List.of("适合高频 CPU"), List.of("Good for fast CPUs")));
        inventory.setItem(14, this.menuItem(Material.DIAMOND, "high-core", "high-core", List.of("适合多核 CPU"), List.of("Good for many-core CPUs")));
        inventory.setItem(16, this.menuItem(Material.REDSTONE_BLOCK, "multi-thread", "multi-thread", List.of("最激进的线程模式"), List.of("Most aggressive threading")));
        inventory.setItem(22, this.menuItem(Material.PAPER, "状态", "Status", List.of("查看当前优化状态"), List.of("Show current optimize status")));
        inventory.setItem(26, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminOptimizeWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case IRON_INGOT -> this.setCpuModeQuick(player, "single-thread");
            case CLOCK -> this.setCpuModeQuick(player, "high-clock");
            case DIAMOND -> this.setCpuModeQuick(player, "high-core");
            case REDSTONE_BLOCK -> this.setCpuModeQuick(player, "multi-thread");
            case PAPER -> this.adminOptimize(player, new String[] {"optimize", "status"});
            case ARROW -> this.openAdminWorkbench(player);
            default -> {
            }
        }
    }

    private void setCpuModeQuick(final Player player, final String mode) {
        final boolean asyncEnabled = !mode.equals("single-thread");
        this.preferences.setValue("optimizations.cpu.mode", mode);
        this.preferences.setValue("optimizations.hunter-tools.async-rendering", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.async-save", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.actor-async-load", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.actor-batch-save", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.render-workers", this.preferences.defaultWorkerCount());
        this.preferences.setValue("optimizations.hunter-tools.web-panel-workers", this.preferences.defaultWorkerCount());
        this.preferences.save(this.workerExecutor);
        player.sendMessage(ChatColor.GREEN + this.text("CPU 模式已保存为 ", "CPU mode saved as ") + mode + this.text("。重启后线程效果会完整生效。", ". Restart for full effect."));
        this.openAdminOptimizeWorkbench(player);
    }

    private void openAdminChatReportsWorkbench(final Player player) {
        final HunterNoChatReportsBridge.Settings settings = HunterNoChatReportsBridge.read();
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_CHAT_REPORTS, null, null, null), 27, this.guiTitle(GuiPage.ADMIN_CHAT_REPORTS, null, null));
        inventory.setItem(4, this.menuItem(Material.WRITABLE_BOOK, "聊天举报保护", "Chat Report Protection", List.of(
            settings.enabled() ? "核心保护已启用" : "核心保护已关闭",
            "由 HunterCore 核心直接处理"
        ), List.of(
            settings.enabled() ? "Built-in protection enabled" : "Built-in protection disabled",
            "Handled directly by HunterCore core"
        )));
        inventory.setItem(10, this.menuItem(settings.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, "禁用聊天举报", "Disable Reports", List.of(settings.enabled() ? "点击关闭" : "点击开启"), List.of(settings.enabled() ? "Click to turn off" : "Click to turn on")));
        inventory.setItem(11, this.menuItem(settings.addQueryData() ? Material.LIME_DYE : Material.GRAY_DYE, "客户端状态显示", "Client Query Data", List.of(settings.addQueryData() ? "点击关闭" : "点击开启"), List.of(settings.addQueryData() ? "Click to turn off" : "Click to turn on")));
        inventory.setItem(12, this.menuItem(settings.convertToGameMessage() ? Material.LIME_DYE : Material.GRAY_DYE, "转为游戏消息", "Game Message Mode", List.of(settings.convertToGameMessage() ? "点击关闭" : "点击开启"), List.of(settings.convertToGameMessage() ? "Click to turn off" : "Click to turn on")));
        inventory.setItem(14, this.menuItem(settings.demandOnClient() ? Material.REDSTONE_BLOCK : Material.GRAY_DYE, "要求客户端 Mod", "Require Client Mod", List.of(settings.demandOnClient() ? "点击关闭" : "点击开启"), List.of(settings.demandOnClient() ? "Click to turn off" : "Click to turn on")));
        inventory.setItem(15, this.menuItem(settings.debugLog() ? Material.LIME_DYE : Material.GRAY_DYE, "调试日志", "Debug Log", List.of(settings.debugLog() ? "点击关闭" : "点击开启"), List.of(settings.debugLog() ? "Click to turn off" : "Click to turn on")));
        inventory.setItem(16, this.menuItem(Material.PAPER, "踢出提示", "Kick Message", List.of(settings.disconnectMessage(), "点击后在聊天栏输入"), List.of(settings.disconnectMessage(), "Click and type in chat")));
        inventory.setItem(22, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminChatReportsWorkbenchClick(final Player player, final ItemStack clicked) {
        final HunterNoChatReportsBridge.Settings settings = HunterNoChatReportsBridge.read();
        switch (clicked.getType()) {
            case LIME_DYE, GRAY_DYE, REDSTONE_BLOCK -> {
                final String name = this.itemName(clicked).toLowerCase(Locale.ROOT);
                HunterNoChatReportsBridge.Settings next = settings;
                if (name.contains("禁用") || name.contains("disable")) {
                    next = settings.withEnabled(!settings.enabled());
                } else if (name.contains("客户端") || name.contains("query")) {
                    next = settings.withAddQueryData(!settings.addQueryData());
                } else if (name.contains("转为") || name.contains("game message")) {
                    next = settings.withConvertToGameMessage(!settings.convertToGameMessage());
                } else if (name.contains("要求") || name.contains("require")) {
                    next = settings.withDemandOnClient(!settings.demandOnClient());
                } else if (name.contains("调试") || name.contains("debug")) {
                    next = settings.withDebugLog(!settings.debugLog());
                }
                HunterNoChatReportsBridge.save(next);
                player.sendMessage(ChatColor.GREEN + this.text("聊天举报保护设置已保存。", "Chat report protection saved."));
                this.openAdminChatReportsWorkbench(player);
            }
            case PAPER -> this.beginGuiChat(player, new GuiChatSession("ncr-message", null, null),
                this.text("请输入要求客户端 Mod 时的踢出提示。", "Type the kick message used when the client mod is required."),
                this.text("输入 cancel 取消。", "Type 'cancel' to abort."));
            case ARROW -> this.openAdminWorkbench(player);
            default -> {
            }
        }
    }

    private void openAdminAiWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_AI, null, null, null), 54, this.guiTitle(GuiPage.ADMIN_AI, null, null));
        inventory.setItem(4, this.menuItem(Material.BEACON, "AI 设置", "AI Settings", List.of(
            "模块: " + this.preferences.moduleEnabled(AI),
            "模型: " + this.preferences.stringValue("modules.ai.model", "gpt-4o-mini"),
            "Key: " + (this.aiApiKeyConfigured() ? "configured" : "missing")
        ), List.of(
            "Module: " + this.preferences.moduleEnabled(AI),
            "Model: " + this.preferences.stringValue("modules.ai.model", "gpt-4o-mini"),
            "Key: " + (this.aiApiKeyConfigured() ? "configured" : "missing")
        )));
        inventory.setItem(10, this.menuItem(this.preferences.moduleEnabled(AI) ? Material.LIME_DYE : Material.GRAY_DYE, "AI 模块", "AI Module", List.of("点击切换启用状态"), List.of("Toggle module")));
        inventory.setItem(11, this.menuItem(this.preferences.booleanValue("modules.ai.chat.enabled", true) ? Material.LIME_DYE : Material.GRAY_DYE, "聊天 AI", "Chat AI", List.of("前缀 " + this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai")), List.of("Prefix " + this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai"))));
        inventory.setItem(12, this.menuItem(this.preferences.booleanValue("modules.ai.npc.enabled", true) ? Material.LIME_DYE : Material.GRAY_DYE, "NPC AI", "NPC AI", List.of("点击切换 NPC 对话"), List.of("Toggle NPC AI")));
        inventory.setItem(14, this.menuItem(Material.NAME_TAG, "模型", "Model", List.of(this.preferences.stringValue("modules.ai.model", "gpt-4o-mini"), "点击输入模型名"), List.of(this.preferences.stringValue("modules.ai.model", "gpt-4o-mini"), "Click to type model")));
        inventory.setItem(15, this.menuItem(Material.COMPASS, "Base URL", "Base URL", List.of(this.preferences.stringValue("modules.ai.base-url", "https://api.openai.com/v1"), "点击输入地址"), List.of(this.preferences.stringValue("modules.ai.base-url", "https://api.openai.com/v1"), "Click to type URL")));
        inventory.setItem(16, this.menuItem(Material.TRIPWIRE_HOOK, "API Key", "API Key", List.of(this.aiApiKeyConfigured() ? "已配置" : "未配置", "点击输入新 Key"), List.of(this.aiApiKeyConfigured() ? "Configured" : "Missing", "Click to type new key")));
        inventory.setItem(19, this.menuItem(Material.BARRIER, "清除 Key", "Clear Key", List.of("清空保存的 API Key"), List.of("Clear stored API key")));
        inventory.setItem(20, this.menuItem(Material.OAK_SIGN, "环境变量", "Env Var", List.of(this.preferences.stringValue("modules.ai.api-key-env", "OPENAI_API_KEY"), "点击输入变量名"), List.of(this.preferences.stringValue("modules.ai.api-key-env", "OPENAI_API_KEY"), "Click to type env var")));
        inventory.setItem(21, this.menuItem(Material.WRITABLE_BOOK, "聊天前缀", "Chat Prefix", List.of(this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai"), "点击输入前缀"), List.of(this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai"), "Click to type prefix")));
        inventory.setItem(23, this.menuItem(Material.REDSTONE, "温度", "Temperature", List.of(String.valueOf(this.preferences.doubleValue("modules.ai.temperature", 0.7D)), "点击输入 0.0-2.0"), List.of(String.valueOf(this.preferences.doubleValue("modules.ai.temperature", 0.7D)), "Click to type 0.0-2.0")));
        inventory.setItem(24, this.menuItem(Material.PAPER, "回复长度", "Max Tokens", List.of(String.valueOf(this.preferences.intValue("modules.ai.max-tokens", 512)), "点击输入 16-4096"), List.of(String.valueOf(this.preferences.intValue("modules.ai.max-tokens", 512)), "Click to type 16-4096")));
        inventory.setItem(25, this.menuItem(Material.ENDER_EYE, "测试提示词", "Test Prompt", List.of("点击输入测试内容"), List.of("Click to type a test prompt")));
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminAiWorkbenchClick(final Player player, final ItemStack clicked) {
        final String name = this.itemName(clicked).toLowerCase(Locale.ROOT);
        switch (clicked.getType()) {
            case LIME_DYE, GRAY_DYE -> {
                if (name.contains("聊天") || name.contains("chat")) {
                    this.preferences.setValue("modules.ai.chat.enabled", !this.preferences.booleanValue("modules.ai.chat.enabled", true));
                } else if (name.contains("npc")) {
                    this.preferences.setValue("modules.ai.npc.enabled", !this.preferences.booleanValue("modules.ai.npc.enabled", true));
                } else {
                    this.preferences.setModuleEnabled(AI, !this.preferences.moduleEnabled(AI));
                }
                this.preferences.save(this.workerExecutor);
                this.openAdminAiWorkbench(player);
            }
            case NAME_TAG -> this.beginGuiChat(player, new GuiChatSession("ai-model", null, null), this.text("请输入 AI 模型名。", "Type the AI model name."));
            case COMPASS -> this.beginGuiChat(player, new GuiChatSession("ai-base-url", null, null), this.text("请输入 OpenAI-compatible Base URL。", "Type the OpenAI-compatible Base URL."));
            case TRIPWIRE_HOOK -> this.beginGuiChat(player, new GuiChatSession("ai-key", null, null), this.text("请输入 API Key；不会在面板回显完整内容。", "Type the API key; it will not be echoed back."));
            case BARRIER -> {
                this.preferences.setValue("modules.ai.api-key", "");
                this.preferences.save(this.workerExecutor);
                this.openAdminAiWorkbench(player);
            }
            case OAK_SIGN -> this.beginGuiChat(player, new GuiChatSession("ai-env", null, null), this.text("请输入环境变量名。", "Type the environment variable name."));
            case WRITABLE_BOOK -> this.beginGuiChat(player, new GuiChatSession("ai-prefix", null, null), this.text("请输入聊天 AI 触发前缀，例如 @ai。", "Type the chat AI prefix, e.g. @ai."));
            case REDSTONE -> this.beginGuiChat(player, new GuiChatSession("ai-temperature", null, null), this.text("请输入温度 0.0-2.0。", "Type temperature 0.0-2.0."));
            case PAPER -> this.beginGuiChat(player, new GuiChatSession("ai-max-tokens", null, null), this.text("请输入最大 tokens，16-4096。", "Type max tokens, 16-4096."));
            case ENDER_EYE -> this.beginGuiChat(player, new GuiChatSession("ai-test", null, null), this.text("请输入要发送给 AI 的测试提示词。", "Type the test prompt to send to AI."));
            case ARROW -> this.openAdminWorkbench(player);
            default -> {
            }
        }
    }

    private void openAdminWebWorkbench(final Player player) {
        final boolean running = this.webPanelManager != null && this.webPanelManager.running();
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_WEB, null, null, null), 54, this.guiTitle(GuiPage.ADMIN_WEB, null, null));
        inventory.setItem(4, this.menuItem(Material.SPYGLASS, "网页面板", "Web Panel", List.of(
            running ? "运行中" : "未运行",
            this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1") + ":" + this.preferences.intValue("modules.web-panel.port", 8088),
            this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088")
        ), List.of(
            running ? "Running" : "Stopped",
            this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1") + ":" + this.preferences.intValue("modules.web-panel.port", 8088),
            this.preferences.stringValue("modules.web-panel.external-url", "http://127.0.0.1:8088")
        )));
        inventory.setItem(10, this.menuItem(Material.REPEATER, "重启面板", "Restart Panel", List.of("重启 HTTP 服务"), List.of("Restart HTTP service")));
        inventory.setItem(11, this.menuItem(Material.COMPASS, "监听地址", "Bind Address", List.of(this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1")), List.of(this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1"))));
        inventory.setItem(12, this.menuItem(Material.REDSTONE, "端口", "Port", List.of(String.valueOf(this.preferences.intValue("modules.web-panel.port", 8088))), List.of(String.valueOf(this.preferences.intValue("modules.web-panel.port", 8088)))));
        inventory.setItem(13, this.menuItem(Material.FILLED_MAP, "地图地址", "Map URL", List.of(this.preferences.stringValue("modules.web-panel.map-url", "http://%host%:8100/")), List.of(this.preferences.stringValue("modules.web-panel.map-url", "http://%host%:8100/"))));
        inventory.setItem(14, this.menuItem(this.preferences.booleanValue("modules.web-panel.public-map", true) ? Material.LIME_DYE : Material.GRAY_DYE, "公开地图", "Public Map", List.of("点击切换"), List.of("Toggle")));
        inventory.setItem(19, this.menuItem(Material.PLAYER_HEAD, "创建/更新用户", "Save User", List.of("输入：用户名 admin/player 密码"), List.of("Type: username admin/player password")));
        inventory.setItem(20, this.menuItem(Material.RED_WOOL, "删除用户", "Remove User", List.of("输入用户名"), List.of("Type username")));
        inventory.setItem(21, this.menuItem(Material.COMMAND_BLOCK, "用户可执行命令", "Allowed Commands", List.of("输入：用户名 inherit/none/命令..."), List.of("Type: user inherit/none/commands...")));
        inventory.setItem(22, this.menuItem(Material.LEVER, "用户命令执行", "Command Execution", List.of("输入：用户名 on/off"), List.of("Type: user on/off")));
        inventory.setItem(23, this.menuItem(Material.BOOK, "用户列表", "Users", List.of("点击在聊天栏显示用户摘要"), List.of("Click to print user summary")));
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminWebWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case REPEATER -> {
                if (this.webPanelManager != null) {
                    this.webPanelManager.restart();
                }
                this.openAdminWebWorkbench(player);
            }
            case COMPASS -> this.beginGuiChat(player, new GuiChatSession("web-bind", null, null), this.text("请输入监听地址，例如 127.0.0.1 或 0.0.0.0。", "Type bind address, e.g. 127.0.0.1 or 0.0.0.0."));
            case REDSTONE -> this.beginGuiChat(player, new GuiChatSession("web-port", null, null), this.text("请输入网页端口 1-65535。", "Type web port 1-65535."));
            case FILLED_MAP -> this.beginGuiChat(player, new GuiChatSession("web-map", null, null), this.text("请输入地图 URL，支持 %host%。", "Type map URL; %host% is supported."));
            case LIME_DYE, GRAY_DYE -> {
                this.preferences.setValue("modules.web-panel.public-map", !this.preferences.booleanValue("modules.web-panel.public-map", true));
                this.preferences.save(this.workerExecutor);
                this.openAdminWebWorkbench(player);
            }
            case PLAYER_HEAD -> this.beginGuiChat(player, new GuiChatSession("web-user", null, null), this.text("请输入：用户名 admin/player 密码。", "Type: username admin/player password."));
            case RED_WOOL -> this.beginGuiChat(player, new GuiChatSession("web-remove", null, null), this.text("请输入要删除的网页用户名。", "Type the web username to remove."));
            case COMMAND_BLOCK -> this.beginGuiChat(player, new GuiChatSession("web-allow", null, null), this.text("请输入：用户名 inherit/none/命令...。", "Type: username inherit/none/commands..."));
            case LEVER -> this.beginGuiChat(player, new GuiChatSession("web-execution", null, null), this.text("请输入：用户名 on/off。", "Type: username on/off."));
            case BOOK -> this.adminWeb(player, new String[] {"web", "users"});
            case ARROW -> this.openAdminWorkbench(player);
            default -> {
            }
        }
    }

    private void openAdminMotdWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_MOTD, null, null, null), 27, this.guiTitle(GuiPage.ADMIN_MOTD, null, null));
        inventory.setItem(4, this.menuItem(Material.OAK_SIGN, "MOTD", "MOTD", List.of(
            "启用: " + this.preferences.moduleEnabled(MOTD),
            this.preferences.stringValue("modules.motd.line-1", ""),
            this.preferences.stringValue("modules.motd.line-2", "")
        ), List.of(
            "Enabled: " + this.preferences.moduleEnabled(MOTD),
            this.preferences.stringValue("modules.motd.line-1", ""),
            this.preferences.stringValue("modules.motd.line-2", "")
        )));
        inventory.setItem(10, this.menuItem(this.preferences.moduleEnabled(MOTD) ? Material.LIME_DYE : Material.GRAY_DYE, "MOTD 模块", "MOTD Module", List.of("点击切换"), List.of("Toggle")));
        inventory.setItem(12, this.menuItem(Material.PAPER, "第一行", "Line 1", List.of(this.preferences.stringValue("modules.motd.line-1", ""), "点击输入"), List.of(this.preferences.stringValue("modules.motd.line-1", ""), "Click to type")));
        inventory.setItem(14, this.menuItem(Material.MAP, "第二行", "Line 2", List.of(this.preferences.stringValue("modules.motd.line-2", ""), "点击输入"), List.of(this.preferences.stringValue("modules.motd.line-2", ""), "Click to type")));
        inventory.setItem(16, this.menuItem(Material.PLAYER_HEAD, "显示人数上限", "Max Players", List.of(String.valueOf(this.preferences.intValue("modules.motd.max-players", -1)), "输入数字或 default"), List.of(String.valueOf(this.preferences.intValue("modules.motd.max-players", -1)), "Type number or default")));
        inventory.setItem(22, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminMotdWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case LIME_DYE, GRAY_DYE -> {
                this.preferences.setModuleEnabled(MOTD, !this.preferences.moduleEnabled(MOTD));
                this.preferences.save(this.workerExecutor);
                this.openAdminMotdWorkbench(player);
            }
            case PAPER -> this.beginGuiChat(player, new GuiChatSession("motd-line1", null, null), this.text("请输入 MOTD 第一行，支持颜色符号。", "Type MOTD line 1; color codes are supported."));
            case MAP -> this.beginGuiChat(player, new GuiChatSession("motd-line2", null, null), this.text("请输入 MOTD 第二行，支持颜色符号。", "Type MOTD line 2; color codes are supported."));
            case PLAYER_HEAD -> this.beginGuiChat(player, new GuiChatSession("motd-max", null, null), this.text("请输入显示人数上限，或 default 恢复默认。", "Type max players, or default."));
            case ARROW -> this.openAdminWorkbench(player);
            default -> {
            }
        }
    }

    private void openAdminCommandsWorkbench(final Player player, final String module) {
        final String selected = commandGateModule(module);
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ADMIN_COMMANDS, selected, null, null), 54, this.guiTitle(GuiPage.ADMIN_COMMANDS, selected, null));
        final List<String> modules = commandGateModules();
        for (int i = 0; i < modules.size(); i++) {
            final String current = modules.get(i);
            inventory.setItem(i, this.menuItem(selected.equals(current) ? Material.LIME_DYE : Material.COMPARATOR, current, current, List.of("点击切换分类"), List.of("Switch category")));
        }
        final List<String> commands = commandGateCommands(selected);
        for (int i = 0; i < commands.size() && i < 36; i++) {
            final String command = commands.get(i);
            final boolean enabled = this.preferences.commandEnabled(selected, command);
            inventory.setItem(9 + i, this.menuItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE, command, command, List.of(enabled ? "已启用" : "已关闭", "点击切换"), List.of(enabled ? "Enabled" : "Disabled", "Click to toggle")));
        }
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/admin"), List.of("/admin")));
        player.openInventory(inventory);
    }

    private void handleAdminCommandsWorkbenchClick(final Player player, final ItemStack clicked, final GuiHolder holder, final int slot) {
        final List<String> modules = commandGateModules();
        if (slot >= 0 && slot < modules.size()) {
            this.openAdminCommandsWorkbench(player, modules.get(slot));
            return;
        }
        if (clicked.getType() == Material.ARROW) {
            this.openAdminWorkbench(player);
            return;
        }
        final String module = commandGateModule(holder.module());
        final List<String> commands = commandGateCommands(module);
        final int index = slot - 9;
        if (index < 0 || index >= commands.size()) {
            return;
        }
        final String command = commands.get(index);
        this.preferences.setCommandEnabled(module, command, !this.preferences.commandEnabled(module, command));
        this.preferences.save(this.workerExecutor);
        this.openAdminCommandsWorkbench(player, module);
    }

    private void openActorListWorkbench(final Player player, final String module) {
        final boolean players = module.equals(REAL_FAKE_PLAYERS);
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ACTOR_LIST, module, null, null), 54, this.guiTitle(GuiPage.ACTOR_LIST, module, null));
        inventory.setItem(4, this.menuItem(
            players ? Material.ARMOR_STAND : Material.VILLAGER_SPAWN_EGG,
            players ? "假人列表" : "NPC 列表",
            players ? "PlayerBots" : "NPCs",
            List.of("点击条目进入工作台"),
            List.of("Click one entry to open its workbench.")
        ));
        int slot = 9;
        if (players) {
            for (final HunterRealFakePlayerManager.RealFakePlayerView view : this.realFakePlayerViews()) {
                if (slot >= 45) {
                    break;
                }
                inventory.setItem(slot++, this.menuItem(Material.PLAYER_HEAD, view.id(), view.id(), List.of(
                    view.world() + " " + (int) view.x() + ", " + (int) view.y() + ", " + (int) view.z(),
                    "AI " + (view.aiEnabled() ? "启用" : "停用") + " | " + view.gameMode(),
                    view.aiStatus().isBlank() ? "暂无最近 AI 状态。" : view.aiStatus()
                ), List.of(
                    view.world() + " " + (int) view.x() + ", " + (int) view.y() + ", " + (int) view.z(),
                    "AI " + (view.aiEnabled() ? "enabled" : "disabled") + " | " + view.gameMode(),
                    view.aiStatus().isBlank() ? "No recent AI status." : view.aiStatus()
                )));
            }
            inventory.setItem(53, this.menuItem(Material.LIME_DYE, "生成假人", "Spawn PlayerBot", List.of("受控聊天输入"), List.of("Controlled chat input")));
        } else {
            for (final HunterActorManager.ActorView view : this.actorViews(module)) {
                if (slot >= 45) {
                    break;
                }
                inventory.setItem(slot++, this.menuItem(Material.PLAYER_HEAD, view.id(), view.id(), List.of(
                    view.world() + " " + (int) view.x() + ", " + (int) view.y() + ", " + (int) view.z(),
                    view.kind() + " | AI " + (view.aiEnabled() ? "启用" : "停用"),
                    view.clickCommand().isBlank() ? "未配置点击命令。" : "已配置点击命令。"
                ), List.of(
                    view.world() + " " + (int) view.x() + ", " + (int) view.y() + ", " + (int) view.z(),
                    view.kind() + " | AI " + (view.aiEnabled() ? "enabled" : "disabled"),
                    view.clickCommand().isBlank() ? "No click command." : "Click command configured."
                )));
            }
        }
        inventory.setItem(45, this.menuItem(Material.EMERALD, "文本列表", "List Text", List.of(players ? "/player list" : "/npc list"), List.of(players ? "/player list" : "/npc list")));
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/menu"), List.of("/menu")));
        player.openInventory(inventory);
    }

    private void handleActorListWorkbenchClick(final Player player, final ItemStack clicked, final GuiHolder holder) {
        final String module = holder.module() == null ? NPCS : holder.module();
        switch (clicked.getType()) {
            case PLAYER_HEAD -> {
                final String id = this.itemName(clicked);
                if (id.isBlank()) {
                    player.sendMessage(ChatColor.RED + this.text("无法识别这个条目。", "This entry could not be identified."));
                    return;
                }
                this.openActorDetailWorkbench(player, module, id);
            }
            case LIME_DYE -> this.beginGuiChat(player, new GuiChatSession("spawn-player", REAL_FAKE_PLAYERS, null),
                this.text("请在聊天栏输入新的假人名称。", "Type the new fake player name in chat."),
                this.text("输入 cancel 取消。", "Type 'cancel' to abort."));
            case EMERALD -> player.performCommand(module.equals(REAL_FAKE_PLAYERS) ? "player list" : "npc list");
            case ARROW -> this.openMainMenuWorkbench(player);
            default -> {
            }
        }
    }

    private void openActorDetailWorkbench(final Player player, final String module, final String id) {
        final boolean players = module.equals(REAL_FAKE_PLAYERS);
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.ACTOR_DETAIL, module, id, null), 54, this.guiTitle(GuiPage.ACTOR_DETAIL, module, id));
        inventory.setItem(4, this.menuItem(Material.PLAYER_HEAD, id, id, List.of("这个对象的 GUI 工作台"), List.of("GUI workbench for this actor.")));
        inventory.setItem(19, this.menuItem(Material.ENDER_PEARL, "传送到对象", "Teleport To Actor", List.of(players ? "/player tp " + id : "/npc tp " + id), List.of(players ? "/player tp " + id : "/npc tp " + id)));
        inventory.setItem(20, this.menuItem(Material.COMPASS, "传送对象到这里", "Teleport Actor Here", List.of(players ? "/player tphere " + id : "/npc tphere " + id), List.of(players ? "/player tphere " + id : "/npc tphere " + id)));
        inventory.setItem(21, this.menuItem(Material.LEVER, "切换 AI", "Toggle AI", List.of(players ? "/player ai " + id + " on|off" : "切换保存的 NPC AI"), List.of(players ? "/player ai " + id + " on|off" : "Toggle stored NPC AI")));
        inventory.setItem(22, this.menuItem(Material.NAME_TAG, "点击命令", "Click Command", List.of("受控聊天输入", "输入 clear 可清除"), List.of("Controlled chat input", "Type 'clear' to remove")));
        inventory.setItem(23, this.menuItem(Material.PAPER, "详情", "Info", List.of(players ? "/player info " + id : "/npc info " + id), List.of(players ? "/player info " + id : "/npc info " + id)));
        inventory.setItem(24, this.menuItem(Material.RED_WOOL, "删除对象", "Remove Actor", List.of("危险操作，需要确认"), List.of("Dangerous action, confirmation required")));
        inventory.setItem(29, this.menuItem(Material.PLAYER_HEAD, "皮肤", "Skin", List.of("受控聊天输入", "输入玩家名或 clear"), List.of("Controlled chat input", "Minecraft name or clear")));
        if (players) {
            inventory.setItem(30, this.menuItem(Material.ENCHANTED_BOOK, "AI 目标", "AI Goal", List.of("受控聊天输入"), List.of("Controlled chat input")));
        }
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("回到对象列表"), List.of("Return to actor list")));
        player.openInventory(inventory);
    }

    private void handleActorDetailWorkbenchClick(final Player player, final ItemStack clicked, final GuiHolder holder) {
        final String module = holder.module();
        final String id = holder.id();
        if (module == null || id == null) {
            return;
        }
        if (!this.actorExists(module, id)) {
            player.sendMessage(ChatColor.RED + this.text("这个对象已经不存在了。", "That actor no longer exists."));
            this.openActorListWorkbench(player, module);
            return;
        }
        switch (clicked.getType()) {
            case ENDER_PEARL -> player.performCommand((module.equals(REAL_FAKE_PLAYERS) ? "player tp " : "npc tp ") + id);
            case COMPASS -> player.performCommand((module.equals(REAL_FAKE_PLAYERS) ? "player tphere " : "npc tphere ") + id);
            case LEVER -> this.toggleActorAi(player, module, id);
            case NAME_TAG -> this.beginGuiChat(player, new GuiChatSession("actor-click", module, id),
                this.text("请在聊天栏输入点击命令。", "Type the click command in chat."),
                this.text("输入 clear 清除，输入 cancel 取消。", "Type 'clear' to remove or 'cancel' to abort."));
            case PAPER -> player.performCommand((module.equals(REAL_FAKE_PLAYERS) ? "player info " : "npc info ") + id);
            case RED_WOOL -> this.openConfirmWorkbench(player, "remove-actor", module, id,
                this.text("确认删除 " + id + " 吗？", "Remove " + id + "?"),
                this.text("这个操作会立即移除实体。", "This will remove the actor immediately."));
            case PLAYER_HEAD -> this.beginGuiChat(player, new GuiChatSession("actor-skin", module, id),
                this.text("请在聊天栏输入皮肤来源。", "Type the skin source in chat."),
                this.text("使用玩家名，或输入 clear。", "Use a Minecraft name, or type 'clear'."));
            case ENCHANTED_BOOK -> {
                if (module.equals(REAL_FAKE_PLAYERS)) {
                    this.beginGuiChat(player, new GuiChatSession("actor-goal", module, id),
                        this.text("请在聊天栏输入新的 AI 目标。", "Type the new AI goal in chat."),
                        this.text("输入 cancel 取消。", "Type 'cancel' to abort."));
                }
            }
            case ARROW -> this.openActorListWorkbench(player, module);
            default -> {
            }
        }
    }

    private boolean openStoryWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.STORY, null, null, null), 27, this.guiTitle(GuiPage.STORY, null, null));
        inventory.setItem(4, this.menuItem(Material.ENCHANTED_BOOK, "故事模式", "Story Mode", List.of(
            this.preferences.booleanValue("modules.story-mode.enabled", false) ? "配置中已启用。" : "配置中已关闭。",
            "默认保持关闭。"
        ), List.of(
            this.preferences.booleanValue("modules.story-mode.enabled", false) ? "Enabled in preferences." : "Disabled in preferences.",
            "Story mode stays disabled by default."
        )));
        inventory.setItem(10, this.menuItem(Material.LIME_DYE, "启用", "Enable", List.of("/story enable"), List.of("/story enable")));
        inventory.setItem(11, this.menuItem(Material.EMERALD_BLOCK, "开始", "Start", List.of("/start"), List.of("/start")));
        inventory.setItem(12, this.menuItem(Material.PAPER, "状态", "Status", List.of("/story status"), List.of("/story status")));
        inventory.setItem(14, this.menuItem(Material.ORANGE_DYE, "自动台词", "Line Auto", List.of("/story line auto"), List.of("/story line auto")));
        inventory.setItem(15, this.menuItem(Material.BARRIER, "停止", "Stop", List.of("/story stop"), List.of("/story stop")));
        inventory.setItem(16, this.menuItem(Material.REDSTONE_BLOCK, "Meltdown", "Meltdown", List.of("危险操作，需要确认"), List.of("Dangerous action, confirmation required")));
        inventory.setItem(22, this.menuItem(Material.ARROW, "返回", "Back", List.of("/menu"), List.of("/menu")));
        player.openInventory(inventory);
        return true;
    }

    private void handleStoryWorkbenchClick(final Player player, final ItemStack clicked) {
        switch (clicked.getType()) {
            case LIME_DYE -> player.performCommand("story enable");
            case EMERALD_BLOCK -> player.performCommand("start");
            case PAPER -> player.performCommand("story status");
            case ORANGE_DYE -> player.performCommand("story line auto");
            case BARRIER -> player.performCommand("story stop");
            case REDSTONE_BLOCK -> this.openConfirmWorkbench(player, "story-meltdown", null, null,
                this.text("确认触发 Story Meltdown 吗？", "Trigger Story Meltdown?"),
                this.text("这是高风险实验能力，默认不开放。", "This is a high-risk experimental action."));
            case ARROW -> this.openMainMenuWorkbench(player);
            default -> {
            }
        }
    }

    private boolean openTitleWorkbench(final Player player) {
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.TITLE_LIST, null, null, null), 54, this.guiTitle(GuiPage.TITLE_LIST, null, null));
        final HunterTitleManager manager = this.titleManager;
        if (manager == null) {
            player.sendMessage(ChatColor.RED + this.text("称号模块暂不可用。", "Titles are unavailable right now."));
            return true;
        }
        final HunterTitleManager.PlayerTitles titles = manager.playerTitles(player.getUniqueId());
        inventory.setItem(4, this.menuItem(Material.NAME_TAG, "称号状态", "Title Status", List.of(
            (manager.enabled() ? "模块已启用" : "模块默认关闭"),
            "显示: " + (titles.visible() ? "开启" : "关闭"),
            "当前: " + (titles.activeId().isBlank() ? "无" : titles.activeId())
        ), List.of(
            manager.enabled() ? "Module enabled" : "Module disabled by default",
            "Visible: " + titles.visible(),
            "Active: " + (titles.activeId().isBlank() ? "none" : titles.activeId())
        )));
        int slot = 0;
        for (final String id : titles.owned()) {
            if (slot >= 45) {
                break;
            }
            final HunterTitleManager.TitleDefinition definition = manager.definition(id);
            if (definition == null) {
                continue;
            }
            final boolean active = id.equalsIgnoreCase(titles.activeId());
            inventory.setItem(slot++, this.menuItem(active ? Material.LIME_DYE : Material.PAPER, definition.displayName(), definition.displayName(), List.of(
                "ID: " + definition.id(),
                "前缀: " + definition.prefix(),
                active ? "当前已激活" : "点击激活"
            ), List.of(
                "ID: " + definition.id(),
                "Prefix: " + definition.prefix(),
                active ? "Currently active" : "Click to activate"
            )));
        }
        inventory.setItem(45, this.menuItem(Material.LEVER, "显示开关", "Visibility", List.of(titles.visible() ? "点击隐藏称号" : "点击显示称号"), List.of(titles.visible() ? "Click to hide your title" : "Click to show your title")));
        inventory.setItem(46, this.menuItem(Material.BARRIER, "清空当前称号", "Clear Active", List.of("保留拥有列表，只清空激活状态"), List.of("Keep owned titles but clear the active one")));
        if (player.hasPermission("huntertools.command.title.admin")) {
            inventory.setItem(47, this.menuItem(Material.REDSTONE_TORCH, "模块开关", "Module Toggle", List.of(manager.enabled() ? "点击关闭称号模块" : "点击启用称号模块"), List.of(manager.enabled() ? "Disable the titles module" : "Enable the titles module")));
        }
        inventory.setItem(49, this.menuItem(Material.ARROW, "返回", "Back", List.of("/profile"), List.of("/profile")));
        player.openInventory(inventory);
        return true;
    }

    private void handleTitleWorkbenchClick(final Player player, final ItemStack clicked, final int slot) {
        if (this.titleManager == null) {
            return;
        }
        final HunterTitleManager.PlayerTitles titles = this.titleManager.playerTitles(player.getUniqueId());
        if (slot >= 0 && slot < Math.min(45, titles.owned().size())) {
            final String id = titles.owned().get(slot);
            this.titleManager.activateTitle(player.getUniqueId(), id);
            this.preferences.save(this.workerExecutor);
            this.titleManager.refreshAllPlayers();
            this.openTitleWorkbench(player);
            player.sendMessage(ChatColor.GREEN + this.text("当前称号已切换为 ", "Active title set to ") + id + ".");
            return;
        }
        switch (clicked.getType()) {
            case LEVER -> {
                this.titleManager.setVisible(player.getUniqueId(), !titles.visible());
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                this.openTitleWorkbench(player);
            }
            case BARRIER -> {
                this.titleManager.activateTitle(player.getUniqueId(), "");
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                this.openTitleWorkbench(player);
            }
            case REDSTONE_TORCH -> {
                if (player.hasPermission("huntertools.command.title.admin")) {
                    this.preferences.setModuleEnabled(TITLES, !this.preferences.moduleEnabled(TITLES));
                    this.preferences.save(this.workerExecutor);
                    this.titleManager.refreshAllPlayers();
                    this.restartDisplayTasks();
                    this.openTitleWorkbench(player);
                }
            }
            case ARROW -> this.openProfileWorkbench(player);
            default -> {
            }
        }
    }

    private void beginGuiChat(final Player player, final GuiChatSession session, final String... instructions) {
        this.guiConfirmSessions.remove(player.getUniqueId());
        this.guiChatSessions.put(player.getUniqueId(), session);
        player.closeInventory();
        for (final String instruction : instructions) {
            player.sendMessage(ChatColor.AQUA + "[HunterCore] " + ChatColor.WHITE + instruction);
        }
    }

    private void handleGuiChatInput(final Player player, final GuiChatSession session, final String message) {
        final String trimmed = message == null ? "" : message.trim();
        if (trimmed.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + this.text("GUI 输入已取消。", "GUI input cancelled."));
            this.reopenAfterGuiChat(player, session);
            return;
        }
        if (trimmed.isBlank()) {
            player.sendMessage(ChatColor.RED + this.text("输入不能为空。", "Input cannot be empty."));
            this.reopenAfterGuiChat(player, session);
            return;
        }
        if (session.module() != null && session.id() != null && !this.actorExists(session.module(), session.id())) {
            player.sendMessage(ChatColor.RED + this.text("目标对象已经不存在了。", "The target actor no longer exists."));
            this.reopenAfterGuiChat(player, session);
            return;
        }
        switch (session.kind()) {
            case "broadcast" -> this.broadcast(player, new String[] {trimmed});
            case "broadcast-tools" -> this.broadcast(player, new String[] {trimmed});
            case "speed" -> player.performCommand("speed " + trimmed);
            case "gui-command" -> {
                final String base = session.module() == null ? "" : session.module();
                if (!base.isBlank()) {
                    player.performCommand(trimmed.equals("-") ? base : base + " " + trimmed);
                }
            }
            case "ncr-message" -> {
                HunterNoChatReportsBridge.save(HunterNoChatReportsBridge.read().withDisconnectMessage(trimmed));
                player.sendMessage(ChatColor.GREEN + this.text("聊天举报保护提示已保存。", "Chat report protection message saved."));
            }
            case "ai-model" -> this.saveGuiString(player, "modules.ai.model", trimmed, 128);
            case "ai-base-url" -> this.saveGuiString(player, "modules.ai.base-url", trimmed, 512);
            case "ai-key" -> this.saveGuiString(player, "modules.ai.api-key", trimmed, 512);
            case "ai-env" -> this.saveGuiString(player, "modules.ai.api-key-env", trimmed, 128);
            case "ai-prefix" -> this.saveGuiString(player, "modules.ai.chat.trigger-prefix", trimmed, 32);
            case "ai-temperature" -> this.saveGuiDouble(player, "modules.ai.temperature", trimmed, 0.0D, 2.0D);
            case "ai-max-tokens" -> this.saveGuiInteger(player, "modules.ai.max-tokens", trimmed, 16, 4096);
            case "ai-test" -> {
                player.sendMessage(ChatColor.AQUA + "HunterCore AI test request started...");
                this.testAiPrompt(trimmed).whenComplete((response, error) -> this.getServer().getScheduler().runTask(this, () -> {
                    if (error != null) {
                        player.sendMessage(ChatColor.RED + "HunterCore AI test failed: " + (error.getCause() == null ? error.getMessage() : error.getCause().getMessage()));
                    } else {
                        player.sendMessage(ChatColor.AQUA + "AI > " + ChatColor.WHITE + response);
                    }
                }));
            }
            case "web-bind" -> this.adminWeb(player, new String[] {"web", "bind", trimmed});
            case "web-port" -> this.adminWeb(player, new String[] {"web", "port", trimmed});
            case "web-map" -> this.adminWeb(player, new String[] {"web", "map", trimmed});
            case "web-user" -> {
                final String[] parts = trimmed.split("\\s+", 3);
                if (parts.length == 3) {
                    this.adminWeb(player, new String[] {"web", "user", parts[0], parts[1], parts[2]});
                } else {
                    player.sendMessage(ChatColor.RED + this.text("格式应为：用户名 admin/player 密码。", "Format: username admin/player password."));
                }
            }
            case "web-remove" -> this.adminWeb(player, new String[] {"web", "remove", trimmed});
            case "web-allow" -> {
                final String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2) {
                    final String[] args = new String[parts.length + 2];
                    args[0] = "web";
                    args[1] = "allow";
                    System.arraycopy(parts, 0, args, 2, parts.length);
                    this.adminWeb(player, args);
                } else {
                    player.sendMessage(ChatColor.RED + this.text("格式应为：用户名 inherit/none/命令...。", "Format: username inherit/none/commands..."));
                }
            }
            case "web-execution" -> {
                final String[] parts = trimmed.split("\\s+");
                if (parts.length == 2) {
                    this.adminWeb(player, new String[] {"web", "execution", parts[0], parts[1]});
                } else {
                    player.sendMessage(ChatColor.RED + this.text("格式应为：用户名 on/off。", "Format: username on/off."));
                }
            }
            case "motd-line1" -> this.saveGuiString(player, "modules.motd.line-1", trimmed, 256);
            case "motd-line2" -> this.saveGuiString(player, "modules.motd.line-2", trimmed, 256);
            case "motd-max" -> {
                if (List.of("default", "off", "reset", "-").contains(HunterToolsPreferences.normalize(trimmed))) {
                    this.preferences.setValue("modules.motd.max-players", -1);
                    this.preferences.save(this.workerExecutor);
                } else {
                    this.saveGuiInteger(player, "modules.motd.max-players", trimmed, 1, 1_000_000);
                }
            }
            case "spawn-player" -> player.performCommand("player spawn " + trimmed);
            case "actor-click" -> player.performCommand((session.module().equals(REAL_FAKE_PLAYERS) ? "player click " : "npc click ")
                + session.id() + (trimmed.equalsIgnoreCase("clear") ? " clear" : " " + trimmed));
            case "actor-skin" -> player.performCommand((session.module().equals(REAL_FAKE_PLAYERS) ? "player skin " : "npc skin ")
                + session.id() + " " + trimmed);
            case "actor-goal" -> player.performCommand("player ai " + session.id() + " goal " + trimmed);
            default -> player.sendMessage(ChatColor.RED + this.text("未知的 GUI 输入动作。", "Unknown GUI input action."));
        }
        this.reopenAfterGuiChat(player, session);
    }

    private void reopenAfterGuiChat(final Player player, final GuiChatSession session) {
        if ("spawn-player".equals(session.kind())) {
            this.openActorListWorkbench(player, REAL_FAKE_PLAYERS);
            return;
        }
        if ("speed".equals(session.kind()) || "broadcast-tools".equals(session.kind())) {
            this.openToolsWorkbench(player);
            return;
        }
        if ("ncr-message".equals(session.kind())) {
            this.openAdminChatReportsWorkbench(player);
            return;
        }
        if (session.kind().startsWith("ai-")) {
            this.openAdminAiWorkbench(player);
            return;
        }
        if (session.kind().startsWith("web-")) {
            this.openAdminWebWorkbench(player);
            return;
        }
        if (session.kind().startsWith("motd-")) {
            this.openAdminMotdWorkbench(player);
            return;
        }
        if ("gui-command".equals(session.kind())) {
            this.openCommandCenterWorkbench(player, session.id() == null ? "tools" : session.id());
            return;
        }
        if (session.module() != null && session.id() != null) {
            if (!this.actorExists(session.module(), session.id())) {
                this.openActorListWorkbench(player, session.module());
                return;
            }
            this.openActorDetailWorkbench(player, session.module(), session.id());
            return;
        }
        this.openAdminWorkbench(player);
    }

    private void saveGuiString(final Player player, final String key, final String value, final int maxLength) {
        if (value.isBlank() || value.length() > maxLength) {
            player.sendMessage(ChatColor.RED + this.text("输入长度不合法。", "Invalid input length."));
            return;
        }
        this.preferences.setValue(key, value);
        this.preferences.save(this.workerExecutor);
        player.sendMessage(ChatColor.GREEN + this.text("设置已保存。", "Setting saved."));
    }

    private void saveGuiInteger(final Player player, final String key, final String value, final int min, final int max) {
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                player.sendMessage(ChatColor.RED + this.text("数字超出范围。", "Number out of range."));
                return;
            }
            this.preferences.setValue(key, parsed);
            this.preferences.save(this.workerExecutor);
            player.sendMessage(ChatColor.GREEN + this.text("设置已保存。", "Setting saved."));
        } catch (final NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + this.text("请输入数字。", "Please type a number."));
        }
    }

    private void saveGuiDouble(final Player player, final String key, final String value, final double min, final double max) {
        try {
            final double parsed = Double.parseDouble(value);
            if (parsed < min || parsed > max) {
                player.sendMessage(ChatColor.RED + this.text("数字超出范围。", "Number out of range."));
                return;
            }
            this.preferences.setValue(key, parsed);
            this.preferences.save(this.workerExecutor);
            player.sendMessage(ChatColor.GREEN + this.text("设置已保存。", "Setting saved."));
        } catch (final NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + this.text("请输入数字。", "Please type a number."));
        }
    }

    private void toggleActorAi(final Player player, final String module, final String id) {
        if (module.equals(REAL_FAKE_PLAYERS)) {
            final HunterRealFakePlayerManager.RealFakePlayerView view = this.realFakePlayerView(id);
            if (view == null) {
                player.sendMessage(ChatColor.RED + this.text("找不到对象: ", "Actor not found: ") + id);
                return;
            }
            player.performCommand("player ai " + id + " " + (view.aiEnabled() ? "off" : "on"));
        } else {
            final HunterActorManager.ActorView view = this.actorView(module, id);
            if (view == null) {
                player.sendMessage(ChatColor.RED + this.text("找不到对象: ", "Actor not found: ") + id);
                return;
            }
            this.setActorAi(module, id, !view.aiEnabled(), view.aiPersona());
            player.sendMessage(ChatColor.GREEN + this.text("对象 AI 已", "Actor AI ") + (!view.aiEnabled() ? this.text("启用", "enabled") : this.text("停用", "disabled")) + ": " + id);
        }
        this.openActorDetailWorkbench(player, module, id);
    }

    private void toggleLanguage(final Player player) {
        final String current = this.preferences.language();
        final String next = current.equalsIgnoreCase("zh_cn") ? "en_us" : "zh_cn";
        this.preferences.setValue("language", next);
        this.preferences.save(this.workerExecutor);
        player.sendMessage(ChatColor.GREEN + this.text("HunterCore 语言已切换为 ", "HunterCore language switched to ") + next + this.text("。", "."));
        this.openSettingsWorkbench(player);
    }

    private @Nullable HunterActorManager.ActorView actorView(final String module, final String id) {
        for (final HunterActorManager.ActorView view : this.actorViews(module)) {
            if (view.id().equalsIgnoreCase(id)) {
                return view;
            }
        }
        return null;
    }

    private @Nullable HunterRealFakePlayerManager.RealFakePlayerView realFakePlayerView(final String id) {
        for (final HunterRealFakePlayerManager.RealFakePlayerView view : this.realFakePlayerViews()) {
            if (view.id().equalsIgnoreCase(id)) {
                return view;
            }
        }
        return null;
    }

    private static ItemStack cloneOrEmpty(final ItemStack stack) {
        return stack == null || stack.getType().isAir() ? new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE) : stack.clone();
    }

    private double maxHealth(final Player player) {
        final AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0D : attribute.getValue();
    }

    private ItemStack menuItem(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + name);
            meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack menuItem(
        final Material material,
        final String zhName,
        final String enName,
        final List<String> zhLore,
        final List<String> enLore
    ) {
        return this.menuItem(material, this.text(zhName, enName), this.textLines(zhLore, enLore));
    }

    private Inventory createGui(final GuiHolder holder, final int size, final String title) {
        final Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.attach(inventory);
        return inventory;
    }

    private @Nullable GuiHolder guiHolder(final InventoryClickEvent event) {
        final InventoryHolder holder = event.getView().getTopInventory().getHolder();
        return holder instanceof GuiHolder guiHolder ? guiHolder : null;
    }

    private boolean clickingTopInventory(final InventoryClickEvent event) {
        return event.getClickedInventory() != null
            && event.getClickedInventory().equals(event.getView().getTopInventory())
            && event.getRawSlot() >= 0
            && event.getRawSlot() < event.getView().getTopInventory().getSize();
    }

    private boolean isGuiPlaceholder(final ItemStack clicked) {
        return clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta();
    }

    private void runGuiAction(final Player player, final Runnable action) {
        try {
            action.run();
        } catch (final RuntimeException ex) {
            this.getLogger().log(java.util.logging.Level.SEVERE, "HunterCore GUI action failed for " + player.getName(), ex);
            player.sendMessage(ChatColor.RED + this.text("这个界面操作失败了，已安全中止。", "This GUI action failed and was stopped safely."));
            player.closeInventory();
        }
    }

    private String guiTitle(final GuiPage page, @Nullable final String module, @Nullable final String id) {
        return switch (page) {
            case MAIN -> ChatColor.DARK_AQUA + this.text("HunterCore · 服务器菜单", "HunterCore · Server Menu");
            case PROFILE -> ChatColor.DARK_AQUA + this.text("HunterCore · 我的资料", "HunterCore · My Profile");
            case INVENTORY_PREVIEW -> ChatColor.DARK_AQUA + this.text("HunterCore · 背包预览", "HunterCore · Inventory Preview");
            case SETTINGS -> ChatColor.DARK_AQUA + this.text("HunterCore · 设置", "HunterCore · Settings");
            case TOOLS -> ChatColor.DARK_AQUA + this.text("HunterCore · 工具", "HunterCore · Tools");
            case ADMIN -> ChatColor.DARK_RED + this.text("HunterCore · 管理中心", "HunterCore · Admin");
            case ADMIN_SYSTEM -> ChatColor.DARK_RED + this.text("HunterCore · 系统状态", "HunterCore · System");
            case ADMIN_PLUGINS -> ChatColor.DARK_RED + this.text("HunterCore · 插件状态", "HunterCore · Plugins");
            case ADMIN_MODULES -> ChatColor.DARK_RED + this.text("HunterCore · 模块", "HunterCore · Modules");
            case ADMIN_PREFERENCES -> ChatColor.DARK_RED + this.text("HunterCore · 偏好", "HunterCore · Preferences");
            case ADMIN_OPTIMIZE -> ChatColor.DARK_RED + this.text("HunterCore · 优化", "HunterCore · Optimize");
            case ADMIN_CHAT_REPORTS -> ChatColor.DARK_RED + this.text("HunterCore · 聊天举报", "HunterCore · Chat Reports");
            case ADMIN_AI -> ChatColor.DARK_RED + this.text("HunterCore · AI", "HunterCore · AI");
            case ADMIN_WEB -> ChatColor.DARK_RED + this.text("HunterCore · 网页面板", "HunterCore · Web Panel");
            case ADMIN_MOTD -> ChatColor.DARK_RED + this.text("HunterCore · MOTD", "HunterCore · MOTD");
            case ADMIN_COMMANDS -> ChatColor.DARK_RED + this.text("HunterCore · 指令开关", "HunterCore · Command Gates");
            case COMMAND_CENTER -> ChatColor.DARK_AQUA + this.text("HunterCore · 全部指令", "HunterCore · Commands");
            case ACTOR_LIST -> ChatColor.DARK_AQUA + (REAL_FAKE_PLAYERS.equals(module) ? this.text("HunterCore · 假人", "HunterCore · PlayerBots") : this.text("HunterCore · NPC", "HunterCore · NPCs"));
            case ACTOR_DETAIL -> ChatColor.DARK_AQUA + this.text("HunterCore · 对象 ", "HunterCore · Actor ") + (id == null ? "" : id);
            case STORY -> ChatColor.DARK_AQUA + this.text("HunterCore · 故事模式", "HunterCore · Story Mode");
            case TITLE_LIST -> ChatColor.DARK_AQUA + this.text("HunterCore · 称号", "HunterCore · Titles");
            case CONFIRM -> ChatColor.DARK_RED + this.text("HunterCore · 确认操作", "HunterCore · Confirm Action");
        };
    }

    private List<String> textLines(final List<String> zhLines, final List<String> enLines) {
        return "zh_cn".equalsIgnoreCase(this.language()) ? zhLines : enLines;
    }

    private String itemName(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta() == null || !stack.getItemMeta().hasDisplayName()) {
            return "";
        }
        final String name = ChatColor.stripColor(stack.getItemMeta().getDisplayName());
        return name == null ? "" : name.trim();
    }

    private boolean actorExists(final String module, final String id) {
        return REAL_FAKE_PLAYERS.equals(module) ? this.realFakePlayerView(id) != null : this.actorView(module, id) != null;
    }

    private void openConfirmWorkbench(
        final Player player,
        final String action,
        @Nullable final String module,
        @Nullable final String id,
        final String primaryLine,
        final String secondaryLine
    ) {
        this.guiConfirmSessions.put(player.getUniqueId(), new GuiConfirmSession(action, module, id));
        final Inventory inventory = this.createGui(new GuiHolder(GuiPage.CONFIRM, module, id, action), 27, this.guiTitle(GuiPage.CONFIRM, module, id));
        inventory.setItem(11, this.menuItem(Material.LIME_WOOL, "确认", "Confirm", List.of(primaryLine, secondaryLine), List.of(primaryLine, secondaryLine)));
        inventory.setItem(15, this.menuItem(Material.BARRIER, "取消", "Cancel", List.of(this.text("返回上一页", "Return to previous screen")), List.of(this.text("返回上一页", "Return to previous screen"))));
        player.openInventory(inventory);
    }

    private void handleConfirmWorkbenchClick(final Player player, final ItemStack clicked, final GuiHolder holder) {
        final GuiConfirmSession session = this.guiConfirmSessions.get(player.getUniqueId());
        if (session == null || holder.action() == null || !holder.action().equals(session.action())) {
            player.sendMessage(ChatColor.RED + this.text("确认会话已失效，请重新操作。", "Confirmation expired. Please try again."));
            this.openAdminWorkbench(player);
            return;
        }
        if (clicked.getType() == Material.BARRIER) {
            this.guiConfirmSessions.remove(player.getUniqueId());
            this.reopenAfterConfirm(player, session);
            return;
        }
        if (clicked.getType() != Material.LIME_WOOL) {
            return;
        }
        this.guiConfirmSessions.remove(player.getUniqueId());
        switch (session.action()) {
            case "disable-module" -> this.confirmDisableModule(player, session.module());
            case "remove-actor" -> this.confirmRemoveActor(player, session.module(), session.id());
            case "story-meltdown" -> player.performCommand("story meltdown");
            default -> player.sendMessage(ChatColor.RED + this.text("未知的确认动作。", "Unknown confirmation action."));
        }
    }

    private void confirmDisableModule(final Player player, @Nullable final String module) {
        if (module == null || !MODULES.contains(module)) {
            player.sendMessage(ChatColor.RED + this.text("无法识别要关闭的模块。", "Could not resolve the module to disable."));
            this.openAdminModulesWorkbench(player);
            return;
        }
        this.preferences.setModuleEnabled(module, false);
        this.preferences.save(this.workerExecutor);
        this.startTasks();
        if (this.actorManager != null && (module.equals(FAKE_PLAYERS) || module.equals(NPCS))) {
            this.actorManager.reload();
        }
        if (this.webPanelManager != null && module.equals(WEB_PANEL)) {
            this.webPanelManager.restart();
        }
        player.sendMessage(ChatColor.YELLOW + this.text("模块已关闭: ", "Module disabled: ") + module);
        this.openAdminModulesWorkbench(player);
    }

    private void confirmRemoveActor(final Player player, @Nullable final String module, @Nullable final String id) {
        if (module == null || id == null) {
            player.sendMessage(ChatColor.RED + this.text("无法识别要删除的对象。", "Could not resolve the actor to remove."));
            this.openAdminWorkbench(player);
            return;
        }
        if (!this.actorExists(module, id)) {
            player.sendMessage(ChatColor.RED + this.text("这个对象已经不存在了。", "That actor no longer exists."));
            this.openActorListWorkbench(player, module);
            return;
        }
        player.performCommand((module.equals(REAL_FAKE_PLAYERS) ? "player remove " : "npc remove ") + id);
        this.openActorListWorkbench(player, module);
    }

    private void reopenAfterConfirm(final Player player, final GuiConfirmSession session) {
        if ("disable-module".equals(session.action())) {
            this.openAdminModulesWorkbench(player);
            return;
        }
        if ("remove-actor".equals(session.action()) && session.module() != null) {
            this.openActorListWorkbench(player, session.module());
            return;
        }
        if ("story-meltdown".equals(session.action())) {
            this.openStoryWorkbench(player);
            return;
        }
        this.openAdminWorkbench(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final String root = commandRoot(event.getMessage());
        if (root.equals("help") || root.equals("?")) {
            event.setCancelled(true);
            this.sendHelp(event.getPlayer(), commandArguments(event.getMessage()));
            return;
        }
        if (!this.preferences.moduleEnabled(COMMAND_OVERRIDES)) {
            return;
        }
        switch (root) {
            case "about" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "about");
            }
            case "plugins", "pl" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "plugins");
            }
            case "version", "ver" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "version");
            }
            case "rules" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "rules");
            }
            case "discord" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "discord");
            }
            case "website", "site" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "website");
            }
            case "motd" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "motd");
            }
            case "info" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "info");
            }
            case "server" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "server");
            }
            case "links" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "links");
            }
            case "qq" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "qq");
            }
            case "group" -> {
                event.setCancelled(true);
                this.sendCommandOverride(event.getPlayer(), "group");
            }
            case "op" -> {
                if (!this.canUseOp(event.getPlayer())) {
                    event.setCancelled(true);
                    this.sendCommandOverride(event.getPlayer(), "op-denied");
                }
            }
            default -> {
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        final GuiChatSession guiChat = this.guiChatSessions.remove(event.getPlayer().getUniqueId());
        if (guiChat != null) {
            event.setCancelled(true);
            this.getServer().getScheduler().runTask(this, () -> this.runGuiAction(event.getPlayer(), () -> this.handleGuiChatInput(event.getPlayer(), guiChat, event.getMessage())));
            return;
        }
        if (this.aiManager != null) {
            this.aiManager.observeChat(event.getPlayer(), event.getMessage());
        }
        if (this.realFakePlayerManager != null) {
            this.realFakePlayerManager.observeChat(event.getPlayer(), event.getMessage());
        }
        if (this.webPanelManager != null) {
            this.webPanelManager.observeChat(event.getPlayer(), event.getMessage());
        }
        if (this.storyModeManager != null) {
            this.getServer().getScheduler().runTask(this, () -> this.storyModeManager.observePlayerChat(event.getPlayer(), event.getMessage()));
        }
        final boolean fakePlayerAiHandled = this.realFakePlayerManager != null
            && this.realFakePlayerManager.handleChatControl(event.getPlayer(), event.getMessage());
        if (!fakePlayerAiHandled && this.aiManager != null && this.aiManager.handleChat(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
            return;
        }
        if (this.titleManager != null && this.titleManager.enabled() && this.titleManager.displayChat()) {
            event.setFormat(this.titleManager.formatChatName(event.getPlayer()) + ChatColor.RESET + ": %2$s");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onActorInteract(final PlayerInteractEntityEvent event) {
        if (this.actorManager != null) {
            if (this.actorManager.handleInteract(event.getPlayer(), event.getRightClicked())) {
                event.setCancelled(true);
                return;
            }
            final HunterActorManager.ActorInteraction interaction = this.actorManager.interaction(event.getRightClicked());
            if (interaction != null && this.aiManager != null && this.aiManager.handleActorInteract(event.getPlayer(), interaction)) {
                event.setCancelled(true);
                return;
            }
        }
        if (this.realFakePlayerManager != null && this.realFakePlayerManager.handleInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private void registerCommands() {
        for (final String command : List.of(
            "htps", "heal", "feed", "fly", "gm", "gms", "gmc", "gma", "gmsp",
            "day", "night", "sun", "rain", "thunder", "broadcast", "clearchat", "speed", "spawn", "setspawn", "back",
            "hat", "craft", "enderchest", "trash",
            "menu", "profile", "playerinfo", "me", "settings", "admin", "player", "npc", "start", "story", "title", "titles"
        )) {
            final org.bukkit.command.PluginCommand pluginCommand = this.getCommand(command);
            if (pluginCommand != null) {
                pluginCommand.setExecutor(this);
                pluginCommand.setTabCompleter(this);
            }
        }
    }

    private void registerHunterCoreCommands() {
        HunterCoreProvider.get().registerCommandExtension(new HunterToolsCoreCommand("admin", List.of(), "huntertools.command.admin", "manage HunterCore from traditional admin commands"));
        HunterCoreProvider.get().registerCommandExtension(new HunterToolsCoreCommand("menu", List.of("gui"), "huntertools.command.menu", "open the HunterCore player GUI"));
        HunterCoreProvider.get().registerCommandExtension(new HunterToolsCoreCommand("profile", List.of("me", "playerinfo"), "huntertools.command.profile", "open your HunterCore profile GUI"));
        HunterCoreProvider.get().registerCommandExtension(new HunterToolsCoreCommand("settings", List.of("prefs"), "huntertools.command.settings", "open your HunterCore settings GUI"));
        HunterCoreProvider.get().registerCommandExtension(new HunterToolsCoreCommand("title", List.of("titles"), "huntertools.command.title", "manage HunterCore titles"));
        for (final String command : HUNTERCORE_SHORTCUTS) {
            HunterCoreProvider.get().registerCommandExtension(new HunterToolsCoreCommand(command, this.hunterCoreShortcutAliases(command), this.hunterCoreShortcutPermission(command), this.hunterCoreShortcutDescription(command)));
        }
    }

    private boolean executeHunterCoreCommand(final CommandSender sender, final String label, final String[] args) {
        return switch (label) {
            case "admin" -> this.admin(sender, args);
            case "tps", "htps" -> this.showTps(sender);
            case "heal" -> this.heal(sender, args);
            case "feed" -> this.feed(sender, args);
            case "fly" -> this.fly(sender, args);
            case "gm", "gms", "gmc", "gma", "gmsp" -> this.gameMode(sender, label, args);
            case "day", "night" -> this.time(sender, label, args);
            case "sun", "rain", "thunder" -> this.weather(sender, label, args);
            case "broadcast", "bc" -> this.broadcast(sender, args);
            case "clearchat", "cc" -> this.clearChat(sender);
            case "speed" -> this.speed(sender, args);
            case "spawn" -> this.spawn(sender, args);
            case "setspawn" -> this.setSpawn(sender);
            case "back" -> this.back(sender);
            case "hat" -> this.hat(sender);
            case "craft", "workbench", "wb" -> this.craft(sender);
            case "enderchest", "ec" -> this.enderChest(sender, args);
            case "trash", "disposal" -> this.trash(sender);
            case "menu", "gui" -> this.openMainMenuWorkbench(sender);
            case "profile", "me", "playerinfo" -> this.openProfileWorkbench(sender);
            case "settings", "prefs" -> this.openSettingsWorkbench(sender);
            case "title", "titles" -> this.titleCommand(sender, args);
            default -> false;
        };
    }

    private List<String> hunterCoreCompletions(final CommandSender sender, final String label, final String[] args) {
        if (label.equals("admin")) {
            return this.adminCompletions(args);
        }
        if (label.equals("title") || label.equals("titles")) {
            return this.titleCompletions(args);
        }
        return this.shortcutCompletions(sender, label, args);
    }

    private List<String> shortcutCompletions(final CommandSender sender, final String name, final String[] args) {
        if (name.equals("gm") && args.length == 1) {
            return matching(args[0], List.of("survival", "creative", "adventure", "spectator"));
        }
        if (name.equals("fly") && args.length == 2) {
            return matching(args[1], List.of("on", "off"));
        }
        if ((name.equals("day") || name.equals("night") || name.equals("sun") || name.equals("rain") || name.equals("thunder")) && args.length == 1) {
            return matching(args[0], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        if ((name.equals("enderchest") || name.equals("ec")) && args.length == 1 && sender.hasPermission("huntertools.command.enderchest.other")) {
            return matching(args[0], this.onlinePlayerNames());
        }
        if (List.of("heal", "feed", "fly", "gm", "gms", "gmc", "gma", "gmsp", "spawn", "speed").contains(name)) {
            final int playerArg = name.equals("speed") ? 1 : args.length - 1;
            if (args.length - 1 == playerArg) {
                return matching(args[args.length - 1], this.onlinePlayerNames());
            }
        }
        if (name.equals("speed") && args.length == 3) {
            return matching(args[2], List.of("walk", "fly"));
        }
        return List.of();
    }

    private boolean titleCommand(final CommandSender sender, final String[] args) {
        if (this.titleManager == null) {
            sender.sendMessage(ChatColor.RED + this.text("称号模块暂不可用。", "Titles are unavailable right now."));
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) {
                return this.openTitleWorkbench(player);
            }
            sender.sendMessage(ChatColor.YELLOW + "/title list|activate|clear|toggle|create|delete|grant|revoke|preview");
            return true;
        }
        final String sub = HunterToolsPreferences.normalize(args[0]);
        switch (sub) {
            case "list" -> {
                if (sender instanceof Player player && args.length == 1) {
                    final HunterTitleManager.PlayerTitles titles = this.titleManager.playerTitles(player.getUniqueId());
                    sender.sendMessage(ChatColor.GOLD + this.text("你的称号: ", "Your titles: ") + String.join(", ", titles.owned()));
                    sender.sendMessage(ChatColor.GRAY + this.text("当前: ", "Active: ") + (titles.activeId().isBlank() ? this.text("无", "none") : titles.activeId()));
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + this.text("已注册称号: ", "Registered titles: ") + this.titleManager.definitions().stream().map(HunterTitleManager.TitleDefinition::id).toList());
                return true;
            }
            case "activate", "use" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + this.text("只有玩家可以激活称号。", "Only players can activate titles."));
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/title activate <id>");
                    return true;
                }
                if (!this.titleManager.activateTitle(player.getUniqueId(), args[1])) {
                    sender.sendMessage(ChatColor.RED + this.text("你还没有这个称号。", "You do not own that title."));
                    return true;
                }
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                sender.sendMessage(ChatColor.GREEN + this.text("已激活称号 ", "Activated title ") + args[1] + ".");
                return true;
            }
            case "clear", "none" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + this.text("只有玩家可以清空称号。", "Only players can clear their title."));
                    return true;
                }
                this.titleManager.activateTitle(player.getUniqueId(), "");
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                sender.sendMessage(ChatColor.GREEN + this.text("已清空当前称号。", "Cleared the active title."));
                return true;
            }
            case "toggle", "visible" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + this.text("只有玩家可以切换显示。", "Only players can toggle visibility."));
                    return true;
                }
                final HunterTitleManager.PlayerTitles titles = this.titleManager.playerTitles(player.getUniqueId());
                this.titleManager.setVisible(player.getUniqueId(), !titles.visible());
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                sender.sendMessage(ChatColor.GREEN + this.text("称号显示已切换为 ", "Title visibility is now ") + (!titles.visible()) + ".");
                return true;
            }
            case "create", "delete", "grant", "revoke", "preview", "module" -> {
                if (!this.require(sender, "huntertools.command.title.admin")) {
                    return true;
                }
                return this.titleAdminCommand(sender, sub, args);
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "/title list|activate|clear|toggle|create|delete|grant|revoke|preview");
                return true;
            }
        }
    }

    private boolean titleAdminCommand(final CommandSender sender, final String sub, final String[] args) {
        switch (sub) {
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "/title create <id> <prefix>");
                    return true;
                }
                final String id = HunterToolsPreferences.normalize(args[1]);
                final String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                this.titleManager.saveDefinition(new HunterTitleManager.TitleDefinition(id, id, prefix, "", 0, true, ""));
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                sender.sendMessage(ChatColor.GREEN + this.text("已创建称号 ", "Created title ") + id + ".");
                return true;
            }
            case "delete" -> {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/title delete <id>");
                    return true;
                }
                if (!this.titleManager.removeDefinition(args[1])) {
                    sender.sendMessage(ChatColor.RED + this.text("找不到这个称号。", "Title not found."));
                    return true;
                }
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                sender.sendMessage(ChatColor.GREEN + this.text("已删除称号 ", "Deleted title ") + args[1] + ".");
                return true;
            }
            case "grant", "revoke" -> {
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.YELLOW + "/title " + sub + " <player> <id>");
                    return true;
                }
                final UUID playerId = HunterTitleManager.resolvePlayerId(args[1]);
                if (playerId == null) {
                    sender.sendMessage(ChatColor.RED + this.text("找不到玩家。", "Player not found."));
                    return true;
                }
                final boolean changed = sub.equals("grant")
                    ? this.titleManager.grantTitle(playerId, args[2])
                    : this.titleManager.revokeTitle(playerId, args[2]);
                if (!changed) {
                    sender.sendMessage(ChatColor.RED + this.text("操作未生效，请检查玩家或称号。", "Nothing changed. Check the player and title."));
                    return true;
                }
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                sender.sendMessage(ChatColor.GREEN + this.text("称号操作已完成。", "Title assignment updated."));
                return true;
            }
            case "preview" -> {
                if (args.length != 3) {
                    sender.sendMessage(ChatColor.YELLOW + "/title preview <player> <id>");
                    return true;
                }
                final Player target = Bukkit.getPlayerExact(args[1]);
                final HunterTitleManager.TitleDefinition definition = this.titleManager.definition(args[2]);
                if (target == null || definition == null) {
                    sender.sendMessage(ChatColor.RED + this.text("找不到玩家或称号。", "Player or title not found."));
                    return true;
                }
                sender.sendMessage(ChatColor.AQUA + this.text("预览: ", "Preview: ") + ChatColor.translateAlternateColorCodes('&', definition.prefix()) + ChatColor.RESET + target.getName());
                return true;
            }
            case "module" -> {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/title module <on|off>");
                    return true;
                }
                final Boolean enabled = parseToggle(args[1]);
                if (enabled == null) {
                    sender.sendMessage(ChatColor.RED + this.text("请使用 on/off。", "Use on/off."));
                    return true;
                }
                this.preferences.setModuleEnabled(TITLES, enabled);
                this.preferences.save(this.workerExecutor);
                this.titleManager.refreshAllPlayers();
                this.restartDisplayTasks();
                sender.sendMessage(ChatColor.GREEN + this.text("称号模块已设置为 ", "Titles module set to ") + enabled + ".");
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private List<String> titleCompletions(final String[] args) {
        if (args.length == 1) {
            return matching(args[0], List.of("list", "activate", "clear", "toggle", "create", "delete", "grant", "revoke", "preview", "module"));
        }
        if (args.length == 2 && List.of("activate", "delete").contains(HunterToolsPreferences.normalize(args[0]))) {
            return matching(args[1], this.titleManager == null ? List.of() : this.titleManager.definitions().stream().map(HunterTitleManager.TitleDefinition::id).toList());
        }
        if (args.length == 2 && List.of("grant", "revoke", "preview").contains(HunterToolsPreferences.normalize(args[0]))) {
            return matching(args[1], this.onlinePlayerNames());
        }
        if (args.length == 3 && List.of("grant", "revoke", "preview").contains(HunterToolsPreferences.normalize(args[0]))) {
            return matching(args[2], this.titleManager == null ? List.of() : this.titleManager.definitions().stream().map(HunterTitleManager.TitleDefinition::id).toList());
        }
        if (args.length == 2 && HunterToolsPreferences.normalize(args[0]).equals("module")) {
            return matching(args[1], List.of("on", "off"));
        }
        return List.of();
    }

    private Collection<String> hunterCoreShortcutAliases(final String command) {
        return switch (command) {
            case "tps" -> List.of("htps");
            case "broadcast" -> List.of("bc");
            case "clearchat" -> List.of("cc");
            case "craft" -> List.of("workbench", "wb");
            case "enderchest" -> List.of("ec");
            case "trash" -> List.of("disposal");
            default -> List.of();
        };
    }

    private String hunterCoreShortcutPermission(final String command) {
        return switch (command) {
            case "tps" -> "huntertools.command.tps";
            case "gm", "gms", "gmc", "gma", "gmsp" -> "huntertools.command.gamemode";
            case "day", "night" -> "huntertools.command.time";
            case "sun", "rain", "thunder" -> "huntertools.command.weather";
            case "broadcast" -> "huntertools.command.broadcast";
            case "clearchat" -> "huntertools.command.clearchat";
            default -> "huntertools.command." + command;
        };
    }

    private String hunterCoreShortcutDescription(final String command) {
        return switch (command) {
            case "tps" -> "show TPS and MSPT";
            case "gm", "gms", "gmc", "gma", "gmsp" -> "change game mode";
            case "day", "night" -> "change world time";
            case "sun", "rain", "thunder" -> "change weather";
            case "broadcast" -> "broadcast a message";
            case "clearchat" -> "clear chat";
            case "setspawn" -> "set server spawn";
            case "enderchest" -> "open an ender chest";
            default -> "run /" + command;
        };
    }

    private final class HunterToolsCoreCommand implements HunterCommandExtension {
        private final String name;
        private final Collection<String> aliases;
        private final String permission;
        private final String description;

        private HunterToolsCoreCommand(final String name, final Collection<String> aliases, final String permission, final String description) {
            this.name = name;
            this.aliases = aliases;
            this.permission = permission;
            this.description = description;
        }

        @Override
        public @NotNull String name() {
            return this.name;
        }

        @Override
        public @NotNull Collection<String> aliases() {
            return this.aliases;
        }

        @Override
        public @Nullable String permission() {
            return this.permission;
        }

        @Override
        public @NotNull String description() {
            return this.description;
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            final String normalized = HunterToolsPreferences.normalize(label);
            if (args.length > 0 && isHelp(args[0])) {
                HunterToolsPlugin.this.sendCommandHelp(sender, normalized, args);
                return true;
            }
            return HunterToolsPlugin.this.executeHunterCoreCommand(sender, normalized, args);
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String alias, @NotNull final String[] args) {
            return HunterToolsPlugin.this.hunterCoreCompletions(sender, HunterToolsPreferences.normalize(alias), args);
        }
    }

    private ExecutorService createWorkerExecutor() {
        final int configured = this.preferences.intValue("optimizations.hunter-tools.render-workers", 4);
        final int workers = this.preferences.singleThreadMode()
            ? 1
            : Math.max(1, Math.min(configured, Math.min(8, Runtime.getRuntime().availableProcessors())));
        final AtomicInteger id = new AtomicInteger();
        final ThreadFactory factory = task -> {
            final Thread thread = new Thread(task, "HunterTools worker " + id.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(workers, factory);
    }

    private void startTasks() {
        this.cancelTasks();
        this.sampleMetrics();
        this.metricsTask = this.getServer().getScheduler().runTaskTimer(this, this::sampleMetrics, 20L, 20L);

        if (this.preferences.moduleEnabled("tps-display") && this.preferences.booleanValue("modules.tps-display.actionbar", false)) {
            final long interval = Math.max(20L, this.preferences.intValue("modules.tps-display.interval-ticks", 40));
            this.actionbarTask = this.getServer().getScheduler().runTaskTimer(this, this::tickActionBar, interval, interval);
        }

        if (this.preferences.moduleEnabled("sidebar")) {
            final long interval = Math.max(20L, this.preferences.intValue("modules.sidebar.interval-ticks", 40));
            this.sidebarTask = this.getServer().getScheduler().runTaskTimer(this, this::tickSidebar, 10L, interval);
        } else {
            this.clearSidebars();
        }
    }

    private void cancelTasks() {
        for (final BukkitTask task : new BukkitTask[] {this.metricsTask, this.actionbarTask, this.sidebarTask}) {
            if (task != null) {
                task.cancel();
            }
        }
        this.metricsTask = null;
        this.actionbarTask = null;
        this.sidebarTask = null;
    }

    void restartDisplayTasks() {
        this.startTasks();
        if (this.titleManager != null) {
            this.titleManager.refreshAllPlayers();
        }
    }

    private void sampleMetrics() {
        final double[] tps = Bukkit.getTPS();
        final Runtime runtime = Runtime.getRuntime();
        final long total = runtime.totalMemory();
        final long free = runtime.freeMemory();
        final double mspt = Bukkit.getAverageTickTime();
        final AdaptiveBudget adaptiveBudget = HunterRuntimeSampler.adaptiveBudget(this.preferences, mspt);
        final List<QueuePressure> queuePressures = HunterRuntimeSampler.queuePressures();
        final List<HotPathSample> hotPathSamples = HunterRuntimeSampler.hotPathSamples(
            Bukkit.getWorlds(),
            Bukkit.getOnlinePlayers().size(),
            this.realFakePlayerManager == null ? 0 : this.realFakePlayerManager.liveCount(),
            queuePressures
        );
        this.snapshot = new MetricsSnapshot(
            tps.length > 0 ? tps[0] : 20.0D,
            tps.length > 1 ? tps[1] : 20.0D,
            tps.length > 2 ? tps[2] : 20.0D,
            mspt,
            total - free,
            total,
            runtime.maxMemory(),
            Bukkit.getOnlinePlayers().size(),
            Bukkit.getMaxPlayers(),
            adaptiveBudget,
            queuePressures,
            hotPathSamples
        );
        if (this.preferences.booleanValue("optimizations.enabled", true) && this.preferences.booleanValue("optimizations.hunter-tools.player-cache", true)) {
            this.cachedPlayerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
    }

    void applyServerBrand() {
        try {
            final Class<?> purpurConfig = Class.forName("org.purpurmc.purpur.PurpurConfig");
            final Field f3Name = purpurConfig.getField("f3Name");
            final String brand = this.preferences == null
                ? "\"HunterCraft\" Server"
                : this.preferences.stringValue("modules.management.f3-server-name", "\"HunterCraft\" Server");
            f3Name.set(null, color(brand == null || brand.isBlank() ? "\"HunterCraft\" Server" : brand));
        } catch (final ReflectiveOperationException ex) {
            this.getLogger().fine("Unable to set runtime F3 server brand: " + ex.getMessage());
        }
    }

    private void tickActionBar() {
        if (!this.preferences.moduleEnabled("tps-display")) {
            return;
        }
        final MetricsSnapshot current = this.snapshot;
        final String serverName = this.preferences.stringValue("modules.web-panel.server-name", "HunterCore");
        final String template = this.preferences.stringValue(
            "modules.tps-display.actionbar-format",
            "&7TPS %tps_color%%tps% &8| &7MSPT &f%mspt% &8| &7Players &f%online%/%max%"
        );
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("huntertools.display.tps")) {
                player.sendActionBar(LEGACY.deserialize(renderDisplayLine(template, current, playerView(player), serverName)));
            }
        }
    }

    private void tickSidebar() {
        if (!this.preferences.moduleEnabled("sidebar")) {
            this.clearSidebars();
            return;
        }
        final List<PlayerView> players = Bukkit.getOnlinePlayers().stream()
            .filter(player -> player.hasPermission("huntertools.display.sidebar"))
            .map(HunterToolsPlugin::playerView)
            .toList();
        final MetricsSnapshot current = this.snapshot;
        final String title = this.preferences.stringValue("modules.sidebar.title", "&6HunterCore");
        final List<String> templates = this.sidebarLines();
        final String serverName = this.preferences.stringValue("modules.web-panel.server-name", "HunterCore");
        if (players.isEmpty()) {
            return;
        }
        if (this.preferences.booleanValue("optimizations.enabled", true) && this.preferences.booleanValue("optimizations.hunter-tools.async-rendering", true)) {
            CompletableFuture
                .supplyAsync(() -> renderSidebars(current, players, templates, serverName), this.workerExecutor)
                .thenAccept(rendered -> this.getServer().getScheduler().runTask(this, () -> applySidebars(title, rendered)));
        } else {
            this.applySidebars(title, renderSidebars(current, players, templates, serverName));
        }
    }

    private void updateSidebarSoon(final Player player) {
        this.getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || !this.preferences.moduleEnabled("sidebar")) {
                return;
            }
            this.applySidebar(player, this.preferences.stringValue("modules.sidebar.title", "&6HunterCore"), this.buildSidebarLines(
                this.snapshot,
                playerView(player),
                this.sidebarLines(),
                this.preferences.stringValue("modules.web-panel.server-name", "HunterCore")
            ));
        }, 20L);
    }

    private static Map<UUID, List<String>> renderSidebars(final MetricsSnapshot snapshot, final List<PlayerView> players, final List<String> templates, final String serverName) {
        final Map<UUID, List<String>> rendered = new HashMap<>();
        for (final PlayerView player : players) {
            rendered.put(player.uuid(), buildSidebarLines(snapshot, player, templates, serverName));
        }
        return rendered;
    }

    private static List<String> buildSidebarLines(final MetricsSnapshot snapshot, final PlayerView player, final List<String> templates, final String serverName) {
        return templates.stream()
            .map(template -> renderDisplayLine(template, snapshot, player, serverName))
            .filter(line -> !line.isBlank())
            .limit(SIDEBAR_KEYS.length)
            .toList();
    }

    private void applySidebars(final String title, final Map<UUID, List<String>> rendered) {
        final Set<UUID> seen = new HashSet<>();
        for (final Map.Entry<UUID, List<String>> entry : rendered.entrySet()) {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                seen.add(player.getUniqueId());
                this.applySidebar(player, title, entry.getValue());
            }
        }
        this.sidebars.keySet().removeIf(uuid -> {
            if (seen.contains(uuid)) {
                return false;
            }
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return true;
        });
    }

    private void applySidebar(final Player player, final String title, final List<String> lines) {
        final boolean dirtyOnly = this.preferences.booleanValue("modules.sidebar.dirty-updates-only", true);
        final SidebarBoard board = this.sidebars.computeIfAbsent(player.getUniqueId(), ignored -> this.createSidebar());
        if (this.titleManager != null) {
            this.titleManager.syncScoreboard(board.scoreboard);
        }
        board.objective.setDisplayName(color(renderDisplayLine(title, this.snapshot, playerView(player), this.preferences.stringValue("modules.web-panel.server-name", "HunterCore"))));
        final List<String> encoded = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size() && i < SIDEBAR_KEYS.length; i++) {
            encoded.add(SIDEBAR_KEYS[i] + color(lines.get(i)));
        }
        if (dirtyOnly && encoded.equals(board.entries)) {
            if (player.getScoreboard() != board.scoreboard) {
                player.setScoreboard(board.scoreboard);
            }
            return;
        }
        for (final String old : board.entries) {
            board.scoreboard.resetScores(old);
        }
        int score = encoded.size();
        for (final String entry : encoded) {
            board.objective.getScore(entry).setScore(score--);
        }
        board.entries = encoded;
        if (player.getScoreboard() != board.scoreboard) {
            player.setScoreboard(board.scoreboard);
        }
    }

    private SidebarBoard createSidebar() {
        final ScoreboardManager manager = Bukkit.getScoreboardManager();
        final Scoreboard scoreboard = manager.getNewScoreboard();
        final Objective objective = scoreboard.registerNewObjective("huntercore", Criteria.DUMMY, Component.text("HunterCore", NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setAutoUpdateDisplay(false);
        return new SidebarBoard(scoreboard, objective, List.of());
    }

    private void clearSidebars() {
        final Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        if (this.titleManager != null) {
            this.titleManager.syncScoreboard(main);
        }
        for (final UUID uuid : new ArrayList<>(this.sidebars.keySet())) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setScoreboard(main);
            }
        }
        this.sidebars.clear();
    }

    String webPanelAddress() {
        return this.webPanelManager == null ? "http://127.0.0.1:8088" : this.webPanelManager.addressLine().replaceAll("/+$", "");
    }

    HunterTitleManager titleManager() {
        return this.titleManager;
    }

    private boolean showTps(final CommandSender sender) {
        if (!this.preferences.moduleEnabled("tps-display")) {
            sender.sendMessage("HunterCore TPS display is disabled in preferences.yml.");
            return true;
        }
        final MetricsSnapshot current = this.snapshot;
        sender.sendMessage(ChatColor.GOLD + "HunterCore TPS");
        sender.sendMessage(ChatColor.GRAY + "1m/5m/15m: " + colorCode(current.tps1()) + MetricsSnapshot.formatTps(current.tps1())
            + ChatColor.GRAY + " / " + colorCode(current.tps5()) + MetricsSnapshot.formatTps(current.tps5())
            + ChatColor.GRAY + " / " + colorCode(current.tps15()) + MetricsSnapshot.formatTps(current.tps15()));
        sender.sendMessage(ChatColor.GRAY + "MSPT: " + ChatColor.WHITE + String.format(Locale.ROOT, "%.2f", current.mspt()));
        sender.sendMessage(ChatColor.GRAY + "Memory: " + ChatColor.WHITE + current.memoryLine());
        return true;
    }

    private boolean admin(final CommandSender sender, final String[] args) {
        if (!this.require(sender, "huntertools.command.admin")) {
            return true;
        }
        if (args.length == 0) {
            this.sendAdminUsage(sender);
            return true;
        }
        final String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> {
                this.sendHelp(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
                yield true;
            }
            case "reload" -> this.adminReload(sender);
            case "modules" -> this.adminModules(sender);
            case "module" -> this.adminToggleModule(sender, args);
            case "command" -> this.adminToggleCommand(sender, args);
            case "plugins" -> this.adminPlugins(sender);
            case "memory" -> this.adminMemory(sender);
            case "gc" -> this.adminGc(sender);
            case "threads" -> this.adminThreads(sender);
            case "optimize" -> this.adminOptimize(sender, args);
            case "ncr", "nochatreports", "chatreports" -> this.adminNoChatReports(sender, args);
            case "motd" -> this.adminMotd(sender, args);
            case "web" -> this.adminWeb(sender, args);
            case "ai" -> this.adminAi(sender, args);
            default -> {
                this.sendAdminUsage(sender);
                yield true;
            }
        };
    }

    private void sendAdminUsage(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + this.text("可用 HunterCore 管理指令：", "Available HunterCore admin commands:"));
        this.sendUsage(sender, "/hc admin reload", "重载 HunterCore 偏好和网页面板。", "Reloads HunterCore preferences and the web panel.");
        this.sendUsage(sender, "/hc admin modules", "列出模块状态。", "Lists module states.");
        this.sendUsage(sender, "/hc admin module <module> <on|off>", "开启或关闭模块。", "Enables or disables a module.");
        this.sendUsage(sender, "/hc admin command <module> <command> <on|off>", "开启或关闭模块内指令。", "Enables or disables a command inside a module.");
        this.sendUsage(sender, "/hc admin optimize [status|mode]", "查看或保存 CPU 优化模式。", "Shows or saves the CPU optimization mode.");
        this.sendUsage(sender, "/hc admin ncr [status|on|off|convert|query|demand|debug|message]", "管理核心聊天举报保护。", "Manages built-in chat report protection.");
        this.sendUsage(sender, "/hc admin motd [status|line1|line2|max]", "查看或修改服务器列表 MOTD。", "Views or edits the server-list MOTD.");
        this.sendUsage(sender, "/hc admin web [status|restart|bind|port|map|public-map|user|remove|users|allow|execution]", "管理网页面板。", "Manages the web panel.");
        this.sendUsage(sender, "/hc admin ai [status|enable|disable|model|base-url|key|env|prefix|chat|npc|temperature|max-tokens|test]", "管理 AI 接入。", "Manages AI integration.");
        this.sendUsage(sender, "/hc admin plugins|memory|gc|threads", "查看运行状态或执行维护操作。", "Shows runtime state or runs maintenance actions.");
    }

    private boolean adminReload(final CommandSender sender) {
        this.preferences.reload();
        if (this.workerExecutor != null) {
            this.workerExecutor.shutdownNow();
        }
        this.workerExecutor = this.createWorkerExecutor();
        if (this.actorManager != null) {
            this.actorManager.setExecutor(this.workerExecutor);
            this.actorManager.reload();
        }
        if (this.aiManager != null) {
            this.aiManager.setExecutor(this.workerExecutor);
        }
        if (this.realFakePlayerManager != null) {
            this.realFakePlayerManager.setAiManager(this.aiManager);
        }
        if (this.realFakePlayerManager != null && !this.preferences.moduleEnabled(REAL_FAKE_PLAYERS)) {
            this.realFakePlayerManager.shutdown();
        }
        if (this.webPanelManager != null) {
            this.webPanelManager.restart();
        }
        this.startTasks();
        sender.sendMessage(this.text("HunterCore 偏好已从 " + this.preferences.file().getPath() + " 重载。", "HunterCore preferences reloaded from " + this.preferences.file().getPath() + "."));
        return true;
    }

    private boolean adminModules(final CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + this.text("HunterCore 模块：", "HunterCore modules:"));
        for (final String module : MODULES) {
            sender.sendMessage("- " + module + ": " + (this.preferences.moduleEnabled(module) ? ChatColor.GREEN + this.text("启用", "enabled") : ChatColor.RED + this.text("停用", "disabled")));
        }
        return true;
    }

    private boolean adminToggleModule(final CommandSender sender, final String[] args) {
        if (args.length != 3) {
            this.sendModuleUsage(sender);
            return true;
        }
        final String module = HunterToolsPreferences.normalize(args[1]);
        if (!MODULES.contains(module)) {
            sender.sendMessage(this.text("未知模块。可用：", "Unknown module. Available: ") + String.join(", ", MODULES));
            return true;
        }
        final Boolean enabled = parseToggle(args[2]);
        if (enabled == null) {
            this.sendModuleUsage(sender);
            return true;
        }
        this.preferences.setModuleEnabled(module, enabled);
        this.preferences.save(this.workerExecutor);
        this.startTasks();
        if (this.actorManager != null && (module.equals(FAKE_PLAYERS) || module.equals(NPCS))) {
            this.actorManager.reload();
        }
        if (this.realFakePlayerManager != null && module.equals(REAL_FAKE_PLAYERS) && !enabled) {
            this.realFakePlayerManager.shutdown();
        }
        if (this.webPanelManager != null && module.equals(WEB_PANEL)) {
            this.webPanelManager.restart();
        }
        sender.sendMessage(this.text("HunterCore 模块 ", "HunterCore module ") + module + this.text(" 已设置为 ", " set to ") + enabled + ".");
        return true;
    }

    private boolean adminToggleCommand(final CommandSender sender, final String[] args) {
        if (args.length != 4) {
            this.sendCommandToggleUsage(sender);
            return true;
        }
        final String module = HunterToolsPreferences.normalize(args[1]);
        final String command = HunterToolsPreferences.normalize(args[2]);
        if (!module.equals(ESSENTIALS) && !module.equals(MANAGEMENT) && !module.equals(FAKE_PLAYERS) && !module.equals(REAL_FAKE_PLAYERS) && !module.equals(NPCS)) {
            sender.sendMessage(this.text("可切换指令的模块：essentials, management, fake-players, real-fake-players, npcs。", "Command toggles are available for essentials, management, fake-players, real-fake-players, and npcs."));
            return true;
        }
        final Boolean enabled = parseToggle(args[3]);
        if (enabled == null) {
            this.sendCommandToggleUsage(sender);
            return true;
        }
        this.preferences.setCommandEnabled(module, command, enabled);
        this.preferences.save(this.workerExecutor);
        sender.sendMessage(this.text("HunterCore 指令 ", "HunterCore command ") + module + "." + command + this.text(" 已设置为 ", " set to ") + enabled + ".");
        return true;
    }

    private void sendModuleUsage(final CommandSender sender) {
        this.sendUsage(sender, "/hc admin module <module> <on|off>", "开启或关闭模块。", "Enables or disables a module.");
        sender.sendMessage(ChatColor.GRAY + this.text("可用模块：", "Available modules: ") + ChatColor.WHITE + String.join(", ", MODULES));
    }

    private void sendCommandToggleUsage(final CommandSender sender) {
        this.sendUsage(sender, "/hc admin command <module> <command> <on|off>", "开启或关闭模块内某条指令。", "Enables or disables one command inside a module.");
        sender.sendMessage(ChatColor.GRAY + this.text("可切换模块：", "Toggleable modules: ") + ChatColor.WHITE + String.join(", ", List.of(ESSENTIALS, MANAGEMENT, FAKE_PLAYERS, REAL_FAKE_PLAYERS, NPCS)));
        sender.sendMessage(ChatColor.DARK_GRAY + "Example: /hc admin command essentials heal off");
    }

    private boolean adminPlugins(final CommandSender sender) {
        if (!this.managementCommandEnabled(sender, "plugins")) {
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "Loaded plugins (" + Bukkit.getPluginManager().getPlugins().length + "):");
        for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            sender.sendMessage("- " + plugin.getName() + " " + plugin.getPluginMeta().getVersion() + " " + (plugin.isEnabled() ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
        }
        return true;
    }

    private boolean adminMemory(final CommandSender sender) {
        if (!this.managementCommandEnabled(sender, "memory")) {
            return true;
        }
        final MetricsSnapshot current = this.snapshot;
        sender.sendMessage(ChatColor.GOLD + "Memory: " + ChatColor.WHITE + current.memoryLine());
        sender.sendMessage(ChatColor.GRAY + "Allocated: " + ChatColor.WHITE + MetricsSnapshot.formatBytes(current.totalMemory()));
        return true;
    }

    private boolean adminGc(final CommandSender sender) {
        if (!this.managementCommandEnabled(sender, "gc")) {
            return true;
        }
        final long before = this.snapshot.usedMemory();
        CompletableFuture.runAsync(System::gc, this.workerExecutor).thenRun(() -> this.getServer().getScheduler().runTask(this, () -> {
            this.sampleMetrics();
            sender.sendMessage("GC requested off the main thread. Used memory: " + MetricsSnapshot.formatBytes(before) + " -> " + MetricsSnapshot.formatBytes(this.snapshot.usedMemory()));
        }));
        return true;
    }

    private boolean adminThreads(final CommandSender sender) {
        if (!this.managementCommandEnabled(sender, "threads")) {
            return true;
        }
        final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        sender.sendMessage(ChatColor.GOLD + "Threads: " + ChatColor.WHITE + bean.getThreadCount() + " live, " + bean.getDaemonThreadCount() + " daemon, peak " + bean.getPeakThreadCount());
        sender.sendMessage(ChatColor.GRAY + "HunterTools render workers: " + ChatColor.WHITE + this.preferences.intValue("optimizations.hunter-tools.render-workers", 4));
        sender.sendMessage(ChatColor.GRAY + "Fake players: " + ChatColor.WHITE + (this.actorManager == null ? 0 : this.actorManager.liveCount(FAKE_PLAYERS)) + " live");
        sender.sendMessage(ChatColor.GRAY + "Real fake players: " + ChatColor.WHITE + (this.realFakePlayerManager == null ? 0 : this.realFakePlayerManager.liveCount()) + " live");
        sender.sendMessage(ChatColor.GRAY + "NPCs: " + ChatColor.WHITE + (this.actorManager == null ? 0 : this.actorManager.liveCount(NPCS)) + " live");
        return true;
    }

    private boolean adminNoChatReports(final CommandSender sender, final String[] args) {
        if (!this.managementCommandEnabled(sender, "nochatreports")) {
            return true;
        }
        final HunterNoChatReportsBridge.Settings current = HunterNoChatReportsBridge.read();
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + this.text("聊天举报保护：", "Chat report protection:"));
            sender.sendMessage(ChatColor.GRAY + "enabled: " + ChatColor.WHITE + current.enabled());
            sender.sendMessage(ChatColor.GRAY + "add-query-data: " + ChatColor.WHITE + current.addQueryData());
            sender.sendMessage(ChatColor.GRAY + "convert-to-game-message: " + ChatColor.WHITE + current.convertToGameMessage());
            sender.sendMessage(ChatColor.GRAY + "demand-on-client: " + ChatColor.WHITE + current.demandOnClient());
            sender.sendMessage(ChatColor.GRAY + "debug-log: " + ChatColor.WHITE + current.debugLog());
            sender.sendMessage(ChatColor.GRAY + "message: " + ChatColor.WHITE + current.disconnectMessage());
            return true;
        }

        final String sub = args[1].toLowerCase(Locale.ROOT);
        final HunterNoChatReportsBridge.Settings next;
        switch (sub) {
            case "on", "enable", "enabled" -> next = current.withEnabled(true);
            case "off", "disable", "disabled" -> next = current.withEnabled(false);
            case "query", "add-query-data" -> {
                if (args.length != 3 || parseToggle(args[2]) == null) {
                    this.sendNoChatReportsUsage(sender);
                    return true;
                }
                next = current.withAddQueryData(parseToggle(args[2]));
            }
            case "convert", "convert-to-game-message" -> {
                if (args.length != 3 || parseToggle(args[2]) == null) {
                    this.sendNoChatReportsUsage(sender);
                    return true;
                }
                next = current.withConvertToGameMessage(parseToggle(args[2]));
            }
            case "demand", "require-client" -> {
                if (args.length != 3 || parseToggle(args[2]) == null) {
                    this.sendNoChatReportsUsage(sender);
                    return true;
                }
                next = current.withDemandOnClient(parseToggle(args[2]));
            }
            case "debug", "debug-log" -> {
                if (args.length != 3 || parseToggle(args[2]) == null) {
                    this.sendNoChatReportsUsage(sender);
                    return true;
                }
                next = current.withDebugLog(parseToggle(args[2]));
            }
            case "message" -> {
                if (args.length < 3) {
                    this.sendNoChatReportsUsage(sender);
                    return true;
                }
                final String message = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
                if (message.isBlank() || message.length() > 256) {
                    sender.sendMessage(ChatColor.RED + this.text("提示不能为空且不能超过 256 字符。", "Message cannot be blank or longer than 256 characters."));
                    return true;
                }
                next = current.withDisconnectMessage(message);
            }
            default -> {
                this.sendNoChatReportsUsage(sender);
                return true;
            }
        }
        HunterNoChatReportsBridge.save(next);
        sender.sendMessage(ChatColor.GREEN + this.text("聊天举报保护设置已保存。", "Chat report protection saved."));
        return true;
    }

    private void sendNoChatReportsUsage(final CommandSender sender) {
        this.sendUsage(sender, "/hc admin ncr status", "查看核心聊天举报保护状态。", "Shows built-in chat report protection state.");
        this.sendUsage(sender, "/hc admin ncr <on|off>", "开启或关闭核心聊天举报保护。", "Enables or disables built-in protection.");
        this.sendUsage(sender, "/hc admin ncr convert <on|off>", "切换聊天转游戏消息。", "Toggles chat-to-game-message conversion.");
        this.sendUsage(sender, "/hc admin ncr query <on|off>", "切换客户端查询提示。", "Toggles client query data.");
        this.sendUsage(sender, "/hc admin ncr demand <on|off>", "是否强制客户端安装 No Chat Reports。", "Requires or stops requiring the client mod.");
        this.sendUsage(sender, "/hc admin ncr debug <on|off>", "切换调试日志。", "Toggles debug logging.");
        this.sendUsage(sender, "/hc admin ncr message <text>", "设置强制客户端 Mod 时的踢出提示。", "Sets the kick message when client mod is required.");
    }

    private boolean adminOptimize(final CommandSender sender, final String[] args) {
        if (!this.managementCommandEnabled(sender, "optimize")) {
            return true;
        }
        if (args.length >= 2 && !args[1].equalsIgnoreCase("status")) {
            if (!validCpuMode(args[1])) {
                this.sendOptimizeUsage(sender);
                return true;
            }
            final String mode = normalizeCpuMode(args[1]);
            final boolean asyncEnabled = !mode.equals("single-thread");
            this.preferences.setValue("optimizations.cpu.mode", mode);
            this.preferences.setValue("optimizations.hunter-tools.async-rendering", asyncEnabled);
            this.preferences.setValue("optimizations.hunter-tools.async-save", asyncEnabled);
            this.preferences.setValue("optimizations.hunter-tools.actor-async-load", asyncEnabled);
            this.preferences.setValue("optimizations.hunter-tools.actor-batch-save", asyncEnabled);
            this.preferences.setValue("optimizations.hunter-tools.render-workers", this.preferences.defaultWorkerCount());
            this.preferences.setValue("optimizations.hunter-tools.web-panel-workers", this.preferences.defaultWorkerCount());
            this.preferences.save(this.workerExecutor);
            sender.sendMessage("HunterCore CPU mode saved as " + mode + ". Restart the server to fully apply core thread settings.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "HunterCore CPU optimization");
        sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + this.preferences.stringValue("optimizations.cpu.mode", "multi-thread"));
        sender.sendMessage(ChatColor.GRAY + "CPU threads: " + ChatColor.WHITE + Runtime.getRuntime().availableProcessors());
        sender.sendMessage(ChatColor.GRAY + "Paper workers: " + ChatColor.WHITE + System.getProperty("Paper.WorkerThreadCount", "auto"));
        sender.sendMessage(ChatColor.GRAY + "Core workers: " + ChatColor.WHITE + System.getProperty("DivineMC.WorkerThreadCount", "auto"));
        sender.sendMessage(ChatColor.GRAY + "Netty IO threads: " + ChatColor.WHITE + System.getProperty("io.netty.eventLoopThreads", "auto"));
        sender.sendMessage(ChatColor.GRAY + "ForkJoin common parallelism: " + ChatColor.WHITE + System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "auto"));
        sender.sendMessage(ChatColor.GRAY + "HunterTools workers: " + ChatColor.WHITE + this.preferences.intValue("optimizations.hunter-tools.render-workers", 4));
        sender.sendMessage(ChatColor.GRAY + "Async actor load: " + ChatColor.WHITE + this.preferences.booleanValue("optimizations.hunter-tools.actor-async-load", true));
        sender.sendMessage(ChatColor.GRAY + "Async/batched actor save: " + ChatColor.WHITE + this.preferences.booleanValue("optimizations.hunter-tools.actor-batch-save", true));
        sender.sendMessage(ChatColor.GRAY + "Web panel workers: " + ChatColor.WHITE + this.preferences.intValue("optimizations.hunter-tools.web-panel-workers", 4));
        sender.sendMessage(ChatColor.GRAY + "Experimental region ticking: " + ChatColor.WHITE + this.preferences.booleanValue("optimizations.cpu.allow-experimental-region-ticking", false));
        sender.sendMessage(ChatColor.GRAY + "Guest status cache: " + ChatColor.WHITE + this.preferences.intValue("modules.web-panel.status-cache-millis", 1000) + "ms");
        this.sendOptimizeUsage(sender);
        return true;
    }

    private void sendOptimizeUsage(final CommandSender sender) {
        this.sendUsage(sender, "/hc admin optimize status", "查看当前线程和缓存配置。", "Shows current thread and cache settings.");
        this.sendUsage(sender, "/hc admin optimize single-thread", "稳定优先，减少异步工作。", "Prioritizes stability and reduces async work.");
        this.sendUsage(sender, "/hc admin optimize high-clock", "高主频 CPU 推荐。", "Recommended for high-clock CPUs.");
        this.sendUsage(sender, "/hc admin optimize high-core", "高核心数 CPU 推荐。", "Recommended for high-core CPUs.");
        this.sendUsage(sender, "/hc admin optimize multi-thread", "更激进地使用多线程。", "Uses multi-threading more aggressively.");
        sender.sendMessage(ChatColor.DARK_GRAY + this.text("切换模式后建议重启服务器以完整生效。", "Restart the server after switching modes for full effect."));
    }

    private boolean adminMotd(final CommandSender sender, final String[] args) {
        if (!this.managementCommandEnabled(sender, "motd")) {
            return true;
        }
        if (args.length < 2 || args.length == 2 && args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "HunterCore MOTD");
            sender.sendMessage(ChatColor.GRAY + "Enabled: " + ChatColor.WHITE + this.preferences.moduleEnabled(MOTD));
            sender.sendMessage(ChatColor.GRAY + "Line 1: " + ChatColor.WHITE + this.preferences.stringValue("modules.motd.line-1", ""));
            sender.sendMessage(ChatColor.GRAY + "Line 2: " + ChatColor.WHITE + this.preferences.stringValue("modules.motd.line-2", ""));
            sender.sendMessage(ChatColor.GRAY + "Max players: " + ChatColor.WHITE + this.preferences.intValue("modules.motd.max-players", -1));
            sender.sendMessage(ChatColor.GRAY + "Placeholders: " + ChatColor.WHITE + "%online%, %max%, %tps%, %mspt%, %version%.");
            this.sendMotdUsage(sender);
            return true;
        }
        final String sub = HunterToolsPreferences.normalize(args[1]);
        if ((sub.equals("line1") || sub.equals("line-1") || sub.equals("line2") || sub.equals("line-2")) && args.length >= 3) {
            final String key = sub.endsWith("2") || sub.equals("line-2") ? "line-2" : "line-1";
            final String text = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
            if (text.length() > 256) {
                sender.sendMessage("MOTD line must be 256 characters or fewer.");
                return true;
            }
            this.preferences.setValue("modules.motd." + key, text);
            this.preferences.save(this.workerExecutor);
            sender.sendMessage("HunterCore MOTD " + key + " updated.");
            return true;
        }
        if (sub.equals("max") && args.length == 3) {
            if (List.of("default", "off", "reset", "-").contains(HunterToolsPreferences.normalize(args[2]))) {
                this.preferences.setValue("modules.motd.max-players", -1);
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore MOTD max players reset to the server default.");
                return true;
            }
            try {
                final int maxPlayers = Integer.parseInt(args[2]);
                if (maxPlayers < 1 || maxPlayers > 1_000_000) {
                    sender.sendMessage("Max players must be between 1 and 1000000, or default.");
                    return true;
                }
                this.preferences.setValue("modules.motd.max-players", maxPlayers);
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore MOTD max players set to " + maxPlayers + ".");
                return true;
            } catch (final NumberFormatException ex) {
                sender.sendMessage("Max players must be a number, or default.");
                return true;
            }
        }
        this.sendMotdUsage(sender);
        return true;
    }

    private void sendMotdUsage(final CommandSender sender) {
        this.sendUsage(sender, "/hc admin motd status", "查看当前 MOTD 配置。", "Shows the current MOTD settings.");
        this.sendUsage(sender, "/hc admin motd line1 <text>", "设置第一行 MOTD。", "Sets the first MOTD line.");
        this.sendUsage(sender, "/hc admin motd line2 <text>", "设置第二行 MOTD。", "Sets the second MOTD line.");
        this.sendUsage(sender, "/hc admin motd max <number|default>", "设置服务器列表显示人数上限。", "Sets the displayed server-list player limit.");
        sender.sendMessage(ChatColor.DARK_GRAY + "Placeholders: %online%, %max%, %tps%, %mspt%, %version%");
    }

    private boolean adminWeb(final CommandSender sender, final String[] args) {
        if (!this.managementCommandEnabled(sender, "web")) {
            return true;
        }
        if (args.length < 2) {
            this.sendWebUsage(sender);
            return true;
        }
        final String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "status" -> {
                final boolean running = this.webPanelManager != null && this.webPanelManager.running();
                sender.sendMessage(ChatColor.GOLD + "HunterCore web panel: " + (running ? ChatColor.GREEN + "running " : ChatColor.RED + "stopped ") + (this.webPanelManager == null ? "" : this.webPanelManager.addressLine()));
                sender.sendMessage(ChatColor.GRAY + "Bind: " + ChatColor.WHITE + this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1")
                    + ChatColor.GRAY + " Port: " + ChatColor.WHITE + this.preferences.intValue("modules.web-panel.port", 8088));
                sender.sendMessage(ChatColor.GRAY + "Public map: " + ChatColor.WHITE + this.preferences.booleanValue("modules.web-panel.public-map", true)
                    + ChatColor.GRAY + " URL: " + ChatColor.WHITE + this.preferences.stringValue("modules.web-panel.map-url", "http://%host%:8100/"));
                this.sendWebUsage(sender);
                yield true;
            }
            case "restart" -> {
                if (this.webPanelManager != null) {
                    this.webPanelManager.restart();
                }
                sender.sendMessage("HunterCore web panel restarted.");
                yield true;
            }
            case "bind", "address" -> this.adminWebBind(sender, args);
            case "port" -> this.adminWebPort(sender, args);
            case "map" -> this.adminWebMap(sender, args);
            case "public-map" -> this.adminWebPublicMap(sender, args);
            case "users" -> {
                sender.sendMessage(ChatColor.GOLD + "HunterCore web users:");
                for (final String id : this.preferences.webUserIds()) {
                    final HunterToolsPreferences.WebUser user = this.preferences.webUser(id);
                    if (user != null) {
                        sender.sendMessage("- " + user.displayName() + ": " + user.role()
                            + (user.passwordConfigured() ? "" : " (password not set)")
                            + ", execution=" + user.commandExecution()
                            + ", allowed=" + webAllowedLine(user));
                    }
                }
                yield true;
            }
            case "user" -> this.adminWebUser(sender, args);
            case "remove" -> this.adminWebRemove(sender, args);
            case "allow" -> this.adminWebAllow(sender, args);
            case "execution" -> this.adminWebExecution(sender, args);
            default -> {
                this.sendWebUsage(sender);
                yield true;
            }
        };
    }

    private void sendWebUsage(final CommandSender sender) {
        this.sendUsage(sender, "/hc admin web status", "查看网页面板监听和地图配置。", "Shows web-panel bind and map settings.");
        this.sendUsage(sender, "/hc admin web restart", "重启网页面板 HTTP 服务。", "Restarts the web-panel HTTP service.");
        this.sendUsage(sender, "/hc admin web bind <address>", "设置监听地址，例如 127.0.0.1 或 0.0.0.0。", "Sets the bind address, for example 127.0.0.1 or 0.0.0.0.");
        this.sendUsage(sender, "/hc admin web port <port>", "设置网页端口并重启面板。", "Sets the web port and restarts the panel.");
        this.sendUsage(sender, "/hc admin web map <url>", "设置地图 URL，支持 %host%。", "Sets the map URL; %host% is supported.");
        this.sendUsage(sender, "/hc admin web public-map <on|off>", "设置访客是否可见地图。", "Controls whether guests can see the map.");
        this.sendUsage(sender, "/hc admin web users", "列出网页用户。", "Lists web users.");
        this.sendUsage(sender, "/hc admin web user <name> <admin|player> <password>", "新增或更新网页用户。", "Creates or updates a web user.");
        this.sendUsage(sender, "/hc admin web remove <name>", "删除网页用户。", "Removes a web user.");
        this.sendUsage(sender, "/hc admin web allow <name> <inherit|none|command...>", "设置网页可执行命令。", "Sets allowed web commands.");
        this.sendUsage(sender, "/hc admin web execution <name> <on|off>", "开关网页命令执行能力。", "Toggles web command execution.");
    }

    private boolean adminWebUser(final CommandSender sender, final String[] args) {
        if (args.length != 5) {
            this.sendUsage(sender, "/hc admin web user <name> <admin|player> <password>", "新增或更新网页用户。", "Creates or updates a web user.");
            return true;
        }
        final String role = HunterToolsPreferences.normalize(args[3]);
        if (!role.equals("admin") && !role.equals("player")) {
            sender.sendMessage(this.text("角色必须是 admin 或 player。", "Role must be admin or player."));
            return true;
        }
        this.preferences.setWebUser(args[2], role, HunterWebPanelManager.hashPassword(args[4]));
        this.preferences.save(this.workerExecutor);
        sender.sendMessage(this.text("HunterCore 网页用户 ", "HunterCore web user ") + HunterToolsPreferences.webUserId(args[2]) + this.text(" 已保存为 ", " saved as ") + role + ".");
        return true;
    }

    private boolean adminWebRemove(final CommandSender sender, final String[] args) {
        if (args.length != 3) {
            this.sendUsage(sender, "/hc admin web remove <name>", "删除网页用户。", "Removes a web user.");
            return true;
        }
        this.preferences.removeWebUser(args[2]);
        this.preferences.save(this.workerExecutor);
        sender.sendMessage(this.text("HunterCore 网页用户 ", "HunterCore web user ") + HunterToolsPreferences.webUserId(args[2]) + this.text(" 已删除。", " removed."));
        return true;
    }

    private boolean adminWebAllow(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            this.sendUsage(sender, "/hc admin web allow <name> <inherit|none|command...>", "设置网页用户可执行的命令。", "Sets commands that the web user may run.");
            return true;
        }
        final HunterToolsPreferences.WebUser user = this.preferences.webUser(args[2]);
        if (user == null) {
            sender.sendMessage(this.text("未知网页用户。", "Unknown web user."));
            return true;
        }
        final String mode = args[3].toLowerCase(Locale.ROOT);
        final List<String> commands;
        if (mode.equals("inherit")) {
            commands = null;
        } else if (mode.equals("none")) {
            commands = List.of();
        } else {
            final List<String> parsed = new ArrayList<>();
            for (int i = 3; i < args.length; i++) {
                for (final String raw : args[i].split(",")) {
                    final String command = normalizeWebCommand(raw);
                    if (!command.isBlank() && !parsed.contains(command)) {
                        parsed.add(command);
                    }
                }
            }
            commands = parsed;
        }
        this.preferences.setWebUserAllowedCommands(args[2], commands);
        this.preferences.save(this.workerExecutor);
        sender.sendMessage(this.text("HunterCore 网页用户 ", "HunterCore web user ") + HunterToolsPreferences.webUserId(args[2]) + this.text(" 可执行命令已设置为 ", " allowed commands set to ") + (commands == null ? "inherit" : commands) + ".");
        return true;
    }

    private boolean adminWebExecution(final CommandSender sender, final String[] args) {
        if (args.length != 4) {
            this.sendUsage(sender, "/hc admin web execution <name> <on|off>", "开关网页用户的命令执行能力。", "Toggles command execution for a web user.");
            return true;
        }
        if (this.preferences.webUser(args[2]) == null) {
            sender.sendMessage(this.text("未知网页用户。", "Unknown web user."));
            return true;
        }
        final Boolean enabled = parseToggle(args[3]);
        if (enabled == null) {
            this.sendUsage(sender, "/hc admin web execution <name> <on|off>", "开关网页用户的命令执行能力。", "Toggles command execution for a web user.");
            return true;
        }
        this.preferences.setWebUserCommandExecution(args[2], enabled);
        this.preferences.save(this.workerExecutor);
        sender.sendMessage(this.text("HunterCore 网页用户 ", "HunterCore web user ") + HunterToolsPreferences.webUserId(args[2]) + this.text(" 命令执行已设置为 ", " command execution set to ") + enabled + ".");
        return true;
    }

    private boolean adminWebBind(final CommandSender sender, final String[] args) {
        if (args.length != 3) {
            this.sendUsage(sender, "/hc admin web bind <address>", "设置网页面板监听地址。", "Sets the web-panel bind address.");
            return true;
        }
        final String bindAddress = args[2].trim();
        if (bindAddress.isBlank() || bindAddress.length() > 128 || bindAddress.contains(" ")) {
            sender.sendMessage("Invalid bind address.");
            return true;
        }
        this.preferences.setValue("modules.web-panel.bind-address", bindAddress);
        this.preferences.save(this.workerExecutor);
        if (this.webPanelManager != null) {
            this.webPanelManager.restart();
        }
        sender.sendMessage(this.text("HunterCore 网页面板监听地址已设置为 ", "HunterCore web bind address set to ") + bindAddress + this.text("，面板已重启。", " and panel restarted."));
        return true;
    }

    private boolean adminWebPort(final CommandSender sender, final String[] args) {
        if (args.length != 3) {
            this.sendUsage(sender, "/hc admin web port <port>", "设置网页面板端口。", "Sets the web-panel port.");
            return true;
        }
        final int port;
        try {
            port = Integer.parseInt(args[2]);
        } catch (final NumberFormatException ex) {
            sender.sendMessage("Port must be a number.");
            return true;
        }
        if (port < 1 || port > 65535) {
            sender.sendMessage("Port must be between 1 and 65535.");
            return true;
        }
        this.preferences.setValue("modules.web-panel.port", port);
        this.preferences.save(this.workerExecutor);
        if (this.webPanelManager != null) {
            this.webPanelManager.restart();
        }
        sender.sendMessage(this.text("HunterCore 网页面板端口已设置为 ", "HunterCore web port set to ") + port + this.text("，面板已重启。", " and panel restarted."));
        return true;
    }

    private boolean adminWebMap(final CommandSender sender, final String[] args) {
        if (args.length != 3) {
            this.sendUsage(sender, "/hc admin web map <url>", "设置网页面板地图地址。", "Sets the web-panel map URL.");
            return true;
        }
        final String mapUrl = args[2].trim();
        if (mapUrl.isBlank() || mapUrl.length() > 512) {
            sender.sendMessage("Invalid map URL.");
            return true;
        }
        this.preferences.setValue("modules.web-panel.map-url", mapUrl);
        this.preferences.save(this.workerExecutor);
        sender.sendMessage(this.text("HunterCore 网页面板地图 URL 已设置为 ", "HunterCore web map URL set to ") + mapUrl + ".");
        return true;
    }

    private boolean adminWebPublicMap(final CommandSender sender, final String[] args) {
        if (args.length != 3) {
            this.sendUsage(sender, "/hc admin web public-map <on|off>", "设置访客是否可以看到地图。", "Controls whether guests can see the map.");
            return true;
        }
        final Boolean enabled = parseToggle(args[2]);
        if (enabled == null) {
            this.sendUsage(sender, "/hc admin web public-map <on|off>", "设置访客是否可以看到地图。", "Controls whether guests can see the map.");
            return true;
        }
        this.preferences.setValue("modules.web-panel.public-map", enabled);
        this.preferences.save(this.workerExecutor);
        sender.sendMessage(this.text("HunterCore 公开地图访问已设置为 ", "HunterCore public map access set to ") + enabled + ".");
        return true;
    }

    private boolean adminAi(final CommandSender sender, final String[] args) {
        if (!this.managementCommandEnabled(sender, "ai")) {
            return true;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "HunterCore AI");
            sender.sendMessage(ChatColor.GRAY + "Module: " + ChatColor.WHITE + this.preferences.moduleEnabled(AI));
            sender.sendMessage(ChatColor.GRAY + "Base URL: " + ChatColor.WHITE + this.preferences.stringValue("modules.ai.base-url", "https://api.openai.com/v1"));
            sender.sendMessage(ChatColor.GRAY + "Model: " + ChatColor.WHITE + this.preferences.stringValue("modules.ai.model", "gpt-4o-mini"));
            sender.sendMessage(ChatColor.GRAY + "API key: " + ChatColor.WHITE + (this.aiApiKeyConfigured() ? "configured" : "missing"));
            sender.sendMessage(ChatColor.GRAY + "Chat: " + ChatColor.WHITE + this.preferences.booleanValue("modules.ai.chat.enabled", true)
                + ChatColor.GRAY + " prefix " + ChatColor.WHITE + this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai"));
            sender.sendMessage(ChatColor.GRAY + "NPC: " + ChatColor.WHITE + this.preferences.booleanValue("modules.ai.npc.enabled", true)
                + ChatColor.GRAY + " actions " + ChatColor.WHITE + this.preferences.booleanValue("modules.ai.npc.allow-actions", true));
            this.sendAdminAiUsage(sender);
            return true;
        }

        final String sub = HunterToolsPreferences.normalize(args[1]);
        switch (sub) {
            case "enable", "on" -> {
                this.preferences.setModuleEnabled(AI, true);
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI module enabled.");
                return true;
            }
            case "disable", "off" -> {
                this.preferences.setModuleEnabled(AI, false);
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI module disabled.");
                return true;
            }
            case "model" -> {
                if (args.length != 3 || args[2].isBlank() || args[2].length() > 128) {
                    this.sendUsage(sender, "/hc admin ai model <model>", "设置发送给 OpenAI-compatible 服务的模型名称。", "Sets the model name sent to the OpenAI-compatible provider.");
                    return true;
                }
                this.preferences.setValue("modules.ai.model", args[2].trim());
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI model set to " + args[2].trim() + ".");
                return true;
            }
            case "base-url", "baseurl", "url" -> {
                if (args.length != 3 || args[2].isBlank() || args[2].length() > 512) {
                    this.sendUsage(sender, "/hc admin ai base-url <url>", "设置 API Base URL，例如 https://api.openai.com/v1。", "Sets the API base URL, for example https://api.openai.com/v1.");
                    return true;
                }
                this.preferences.setValue("modules.ai.base-url", args[2].trim());
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI base URL updated.");
                return true;
            }
            case "key", "api-key" -> {
                if (args.length != 3 || args[2].isBlank() || args[2].length() > 512) {
                    this.sendUsage(sender, "/hc admin ai key <api-key>", "保存 AI API Key；网页面板不会回显完整密钥。", "Saves the AI API key; the web panel will not echo the full key.");
                    return true;
                }
                this.preferences.setValue("modules.ai.api-key", args[2].trim());
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI API key saved.");
                return true;
            }
            case "clear-key", "clearkey" -> {
                this.preferences.setValue("modules.ai.api-key", "");
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI API key cleared. Env fallback can still be used.");
                return true;
            }
            case "env", "api-key-env" -> {
                if (args.length != 3 || args[2].isBlank() || args[2].length() > 128) {
                    this.sendUsage(sender, "/hc admin ai env <ENV_NAME>", "设置未保存密钥时读取的环境变量名。", "Sets the environment variable used when no key is stored.");
                    return true;
                }
                this.preferences.setValue("modules.ai.api-key-env", args[2].trim());
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI API key env set to " + args[2].trim() + ".");
                return true;
            }
            case "prefix" -> {
                if (args.length != 3 || args[2].isBlank() || args[2].length() > 32) {
                    this.sendUsage(sender, "/hc admin ai prefix <prefix>", "设置聊天 AI 触发前缀，例如 @ai。", "Sets the chat AI trigger prefix, for example @ai.");
                    return true;
                }
                this.preferences.setValue("modules.ai.chat.trigger-prefix", args[2].trim());
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI chat prefix set to " + args[2].trim() + ".");
                return true;
            }
            case "chat", "npc" -> {
                if (args.length != 3) {
                    this.sendUsage(sender, "/hc admin ai " + sub + " <on|off>", "开启或关闭指定 AI 通道。", "Enables or disables the selected AI channel.");
                    return true;
                }
                final Boolean enabled = parseToggle(args[2]);
                if (enabled == null) {
                    sender.sendMessage(this.text("请使用 on/off。", "Use on/off."));
                    return true;
                }
                this.preferences.setValue("modules.ai." + sub + ".enabled", enabled);
                this.preferences.save(this.workerExecutor);
                sender.sendMessage("HunterCore AI " + sub + " set to " + enabled + ".");
                return true;
            }
            case "temperature" -> {
                if (args.length != 3) {
                    this.sendUsage(sender, "/hc admin ai temperature <0.0-2.0>", "设置 AI 输出随机性，数值越低越稳定。", "Sets AI response randomness; lower values are more deterministic.");
                    return true;
                }
                try {
                    final double temperature = Double.parseDouble(args[2]);
                    if (temperature < 0.0D || temperature > 2.0D) {
                        sender.sendMessage("Temperature must be between 0.0 and 2.0.");
                        return true;
                    }
                    this.preferences.setValue("modules.ai.temperature", temperature);
                    this.preferences.save(this.workerExecutor);
                    sender.sendMessage("HunterCore AI temperature set to " + temperature + ".");
                } catch (final NumberFormatException ex) {
                    sender.sendMessage("Temperature must be a number.");
                }
                return true;
            }
            case "max-tokens", "maxtokens" -> {
                if (args.length != 3) {
                    this.sendUsage(sender, "/hc admin ai max-tokens <16-4096>", "设置单次 AI 回复的最大 token 数。", "Sets the maximum token count for one AI response.");
                    return true;
                }
                try {
                    final int maxTokens = Integer.parseInt(args[2]);
                    if (maxTokens < 16 || maxTokens > 4096) {
                        sender.sendMessage("Max tokens must be between 16 and 4096.");
                        return true;
                    }
                    this.preferences.setValue("modules.ai.max-tokens", maxTokens);
                    this.preferences.save(this.workerExecutor);
                    sender.sendMessage("HunterCore AI max tokens set to " + maxTokens + ".");
                } catch (final NumberFormatException ex) {
                    sender.sendMessage("Max tokens must be a number.");
                }
                return true;
            }
            case "test" -> {
                if (args.length < 3) {
                    this.sendUsage(sender, "/hc admin ai test <prompt>", "发送一次测试请求给当前 AI 配置。", "Sends a test request using the current AI settings.");
                    return true;
                }
                final String prompt = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
                sender.sendMessage("HunterCore AI test request started...");
                this.testAiPrompt(prompt).whenComplete((response, error) -> this.getServer().getScheduler().runTask(this, () -> {
                    if (error != null) {
                        sender.sendMessage(ChatColor.RED + "HunterCore AI test failed: " + (error.getCause() == null ? error.getMessage() : error.getCause().getMessage()));
                    } else {
                        sender.sendMessage(ChatColor.AQUA + "AI > " + ChatColor.WHITE + response);
                    }
                }));
                return true;
            }
            default -> {
                this.sendAdminAiUsage(sender);
                return true;
            }
        }
    }

    private void sendAdminAiUsage(final CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + this.text("可用 AI 管理指令：", "Available AI admin commands:"));
        this.sendUsage(sender, "/hc admin ai status", "查看当前 AI 配置。", "Shows the current AI configuration.");
        this.sendUsage(sender, "/hc admin ai enable|disable", "开启或关闭 AI 模块。", "Enables or disables the AI module.");
        this.sendUsage(sender, "/hc admin ai model <model>", "设置模型名称。", "Sets the model name.");
        this.sendUsage(sender, "/hc admin ai base-url <url>", "设置 OpenAI-compatible Base URL。", "Sets the OpenAI-compatible base URL.");
        this.sendUsage(sender, "/hc admin ai key <api-key> | clear-key", "保存或清除 API Key。", "Saves or clears the API key.");
        this.sendUsage(sender, "/hc admin ai env <ENV_NAME>", "设置环境变量密钥来源。", "Sets the environment variable key source.");
        this.sendUsage(sender, "/hc admin ai prefix <prefix>", "设置聊天触发前缀。", "Sets the chat trigger prefix.");
        this.sendUsage(sender, "/hc admin ai chat|npc <on|off>", "开启或关闭聊天/NPC AI。", "Enables or disables chat/NPC AI.");
        this.sendUsage(sender, "/hc admin ai temperature <0.0-2.0>", "调整输出随机性。", "Adjusts output randomness.");
        this.sendUsage(sender, "/hc admin ai max-tokens <16-4096>", "调整回复长度上限。", "Adjusts the response length limit.");
        this.sendUsage(sender, "/hc admin ai test <prompt>", "发送测试请求。", "Sends a test request.");
    }

    private boolean heal(final CommandSender sender, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "heal")) {
            return true;
        }
        final Player target = this.target(sender, args, 0);
        if (target == null) {
            return true;
        }
        final AttributeInstance maxHealthAttribute = target.getAttribute(Attribute.MAX_HEALTH);
        final double maxHealth = maxHealthAttribute == null ? 20.0D : maxHealthAttribute.getValue();
        target.setHealth(maxHealth);
        target.setFireTicks(0);
        target.sendMessage("You have been healed.");
        if (!sender.equals(target)) {
            sender.sendMessage("Healed " + target.getName() + ".");
        }
        return true;
    }

    private boolean feed(final CommandSender sender, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "feed")) {
            return true;
        }
        final Player target = this.target(sender, args, 0);
        if (target == null) {
            return true;
        }
        target.setFoodLevel(20);
        target.setSaturation(20.0F);
        target.sendMessage("You have been fed.");
        if (!sender.equals(target)) {
            sender.sendMessage("Fed " + target.getName() + ".");
        }
        return true;
    }

    private boolean fly(final CommandSender sender, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "fly")) {
            return true;
        }
        final Player target = args.length > 0 ? this.player(args[0], sender) : this.self(sender);
        if (target == null) {
            return true;
        }
        final Boolean explicit = args.length > 1 ? parseToggle(args[1]) : null;
        final boolean enabled = explicit == null ? !target.getAllowFlight() : explicit;
        target.setAllowFlight(enabled);
        if (!enabled) {
            target.setFlying(false);
        }
        target.sendMessage("Flight " + (enabled ? "enabled" : "disabled") + ".");
        if (!sender.equals(target)) {
            sender.sendMessage("Flight for " + target.getName() + " " + (enabled ? "enabled" : "disabled") + ".");
        }
        return true;
    }

    private boolean gameMode(final CommandSender sender, final String command, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "gm")) {
            return true;
        }
        final GameMode mode;
        int playerArg = 0;
        switch (command) {
            case "gms" -> mode = GameMode.SURVIVAL;
            case "gmc" -> mode = GameMode.CREATIVE;
            case "gma" -> mode = GameMode.ADVENTURE;
            case "gmsp" -> mode = GameMode.SPECTATOR;
            default -> {
                if (args.length == 0) {
                    sender.sendMessage("Usage: /gm <survival|creative|adventure|spectator> [player]");
                    return true;
                }
                mode = parseGameMode(args[0]);
                playerArg = 1;
            }
        }
        if (mode == null) {
            sender.sendMessage("Unknown game mode.");
            return true;
        }
        final Player target = args.length > playerArg ? this.player(args[playerArg], sender) : this.self(sender);
        if (target == null) {
            return true;
        }
        target.setGameMode(mode);
        target.sendMessage("Game mode set to " + mode.name().toLowerCase(Locale.ROOT) + ".");
        if (!sender.equals(target)) {
            sender.sendMessage("Set " + target.getName() + " to " + mode.name().toLowerCase(Locale.ROOT) + ".");
        }
        return true;
    }

    private boolean time(final CommandSender sender, final String command, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, command)) {
            return true;
        }
        final World world = this.world(sender, args);
        if (world == null) {
            return true;
        }
        world.setTime(command.equals("day") ? 1000L : 13000L);
        sender.sendMessage("Set time in " + world.getName() + " to " + command + ".");
        return true;
    }

    private boolean weather(final CommandSender sender, final String command, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, command)) {
            return true;
        }
        final World world = this.world(sender, args);
        if (world == null) {
            return true;
        }
        world.setStorm(!command.equals("sun"));
        world.setThundering(command.equals("thunder"));
        world.setWeatherDuration(20 * 60 * 10);
        sender.sendMessage("Set weather in " + world.getName() + " to " + command + ".");
        return true;
    }

    private boolean broadcast(final CommandSender sender, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "broadcast")) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /broadcast <message>");
            return true;
        }
        Bukkit.broadcast(Component.text("[HunterCore] ", NamedTextColor.GOLD).append(Component.text(String.join(" ", args), NamedTextColor.YELLOW)));
        return true;
    }

    private boolean clearChat(final CommandSender sender) {
        if (!this.essentialsCommandEnabled(sender, "clearchat")) {
            return true;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 80; i++) {
                player.sendMessage("");
            }
            player.sendMessage(ChatColor.GRAY + "Chat was cleared by " + sender.getName() + ".");
        }
        sender.sendMessage("Cleared chat for " + Bukkit.getOnlinePlayers().size() + " players.");
        return true;
    }

    private boolean speed(final CommandSender sender, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "speed")) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /speed <1-10> [player] [walk|fly]");
            return true;
        }
        final float speed;
        try {
            speed = Math.max(0.0F, Math.min(1.0F, Float.parseFloat(args[0]) / 10.0F));
        } catch (final NumberFormatException ex) {
            sender.sendMessage("Speed must be a number from 1 to 10.");
            return true;
        }
        final Player target = args.length > 1 ? this.player(args[1], sender) : this.self(sender);
        if (target == null) {
            return true;
        }
        final String type = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : (target.isFlying() ? "fly" : "walk");
        if (type.equals("fly")) {
            target.setFlySpeed(speed);
        } else {
            target.setWalkSpeed(speed);
        }
        sender.sendMessage("Set " + target.getName() + " " + type + " speed to " + args[0] + ".");
        return true;
    }

    private boolean spawn(final CommandSender sender, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "spawn")) {
            return true;
        }
        final Player target = args.length > 0 ? this.player(args[0], sender) : this.self(sender);
        if (target == null) {
            return true;
        }
        final Location spawn = this.preferences.spawn();
        if (spawn == null) {
            sender.sendMessage("No valid spawn is configured.");
            return true;
        }
        target.teleportAsync(spawn).thenAccept(success -> {
            if (success) {
                target.sendMessage("Teleported to spawn.");
            }
        });
        return true;
    }

    private boolean setSpawn(final CommandSender sender) {
        if (!this.essentialsCommandEnabled(sender, "setspawn")) {
            return true;
        }
        final Player player = this.self(sender);
        if (player == null) {
            return true;
        }
        this.preferences.setSpawn(player.getLocation());
        this.preferences.save(this.workerExecutor);
        sender.sendMessage("HunterCore spawn set to your location.");
        return true;
    }

    private boolean back(final CommandSender sender) {
        if (!this.essentialsCommandEnabled(sender, "back")) {
            return true;
        }
        final Player player = this.self(sender);
        if (player == null) {
            return true;
        }
        final Location location = this.backLocations.get(player.getUniqueId());
        if (location == null) {
            player.sendMessage("No previous location is available.");
            return true;
        }
        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                player.sendMessage("Returned to your previous location.");
            }
        });
        return true;
    }

    private boolean hat(final CommandSender sender) {
        if (!this.essentialsCommandEnabled(sender, "hat")) {
            return true;
        }
        final Player player = this.self(sender);
        if (player == null) {
            return true;
        }
        final ItemStack hand = player.getInventory().getItemInMainHand();
        final ItemStack helmet = player.getInventory().getHelmet();
        player.getInventory().setHelmet(isAir(hand) ? null : hand.clone());
        player.getInventory().setItemInMainHand(helmet == null ? new ItemStack(Material.AIR) : helmet.clone());
        player.updateInventory();
        player.sendMessage("Swapped your helmet and main hand item.");
        return true;
    }

    private boolean craft(final CommandSender sender) {
        if (!this.essentialsCommandEnabled(sender, "craft")) {
            return true;
        }
        final Player player = this.self(sender);
        if (player == null) {
            return true;
        }
        player.openWorkbench(player.getLocation(), true);
        return true;
    }

    private boolean enderChest(final CommandSender sender, final String[] args) {
        if (!this.essentialsCommandEnabled(sender, "enderchest")) {
            return true;
        }
        final Player viewer = this.self(sender);
        if (viewer == null) {
            return true;
        }
        final Player owner;
        if (args.length == 0) {
            owner = viewer;
        } else {
            if (!sender.hasPermission("huntertools.command.enderchest.other")) {
                sender.sendMessage(Bukkit.permissionMessage());
                return true;
            }
            owner = this.player(args[0], sender);
            if (owner == null) {
                return true;
            }
        }
        viewer.openInventory(owner.getEnderChest());
        if (!viewer.equals(owner)) {
            viewer.sendMessage("Opened " + owner.getName() + "'s ender chest.");
        }
        return true;
    }

    private boolean trash(final CommandSender sender) {
        if (!this.essentialsCommandEnabled(sender, "trash")) {
            return true;
        }
        final Player player = this.self(sender);
        if (player == null) {
            return true;
        }
        final Inventory inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "Trash");
        player.openInventory(inventory);
        player.sendMessage("Items left in this inventory will be discarded when it closes.");
        return true;
    }

    private boolean fakePlayer(final CommandSender sender, final String label, final String[] args) {
        if (!this.require(sender, "huntertools.command.fakeplayer")) {
            return true;
        }
        if (args.length == 0) {
            this.sendHelp(sender, "fakeplayer");
            return true;
        }
        return this.actorManager != null && this.actorManager.fakePlayerCommand(sender, label, args);
    }

    private boolean realFakePlayer(final CommandSender sender, final String label, final String[] args) {
        if (!this.require(sender, "huntertools.command.hplayer")) {
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof final Player player) {
                this.openActorListWorkbench(player, REAL_FAKE_PLAYERS);
                return true;
            }
            this.sendHelp(sender, "player");
            return true;
        }
        return this.realFakePlayerManager != null && this.realFakePlayerManager.command(sender, label, args);
    }

    private boolean npc(final CommandSender sender, final String label, final String[] args) {
        if (!this.require(sender, "huntertools.command.npc")) {
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof final Player player) {
                this.openActorListWorkbench(player, NPCS);
                return true;
            }
            this.sendHelp(sender, "npc");
            return true;
        }
        return this.actorManager != null && this.actorManager.npcCommand(sender, label, args);
    }

    private boolean essentialsCommandEnabled(final CommandSender sender, final String command) {
        if (!this.preferences.moduleEnabled(ESSENTIALS)) {
            sender.sendMessage("HunterCore essentials module is disabled in preferences.yml.");
            return false;
        }
        if (!this.preferences.commandEnabled(ESSENTIALS, command)) {
            sender.sendMessage("HunterCore command " + command + " is disabled in preferences.yml.");
            return false;
        }
        return true;
    }

    private boolean managementCommandEnabled(final CommandSender sender, final String command) {
        if (!this.preferences.moduleEnabled(MANAGEMENT)) {
            sender.sendMessage("HunterCore management module is disabled in preferences.yml.");
            return false;
        }
        if (!this.preferences.commandEnabled(MANAGEMENT, command)) {
            sender.sendMessage("HunterCore management command " + command + " is disabled in preferences.yml.");
            return false;
        }
        return true;
    }

    private boolean require(final CommandSender sender, final String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return false;
        }
        return true;
    }

    private Player target(final CommandSender sender, final String[] args, final int index) {
        return args.length > index ? this.player(args[index], sender) : this.self(sender);
    }

    private Player self(final CommandSender sender) {
        if (sender instanceof final Player player) {
            return player;
        }
        sender.sendMessage("Only players can use this without a target.");
        return null;
    }

    private Player player(final String name, final CommandSender sender) {
        final Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            sender.sendMessage("Player not found: " + name);
            return null;
        }
        return player;
    }

    private World world(final CommandSender sender, final String[] args) {
        if (args.length > 0) {
            final World world = Bukkit.getWorld(args[0]);
            if (world == null) {
                sender.sendMessage("World not found: " + args[0]);
            }
            return world;
        }
        if (sender instanceof final Player player) {
            return player.getWorld();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    private String renderMotdLine(final String line, final ServerListPingEvent event) {
        final MetricsSnapshot current = this.snapshot;
        return line
            .replace("%online%", Integer.toString(event.getNumPlayers()))
            .replace("%max%", Integer.toString(event.getMaxPlayers()))
            .replace("%tps%", MetricsSnapshot.formatTps(current.tps1()))
            .replace("%mspt%", String.format(Locale.ROOT, "%.1f", current.mspt()))
            .replace("%version%", Bukkit.getMinecraftVersion());
    }

    private static GameMode parseGameMode(final String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static Boolean parseToggle(final String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "on", "enable", "enabled", "true", "yes" -> Boolean.TRUE;
            case "off", "disable", "disabled", "false", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static boolean validCpuMode(final String input) {
        if (input == null) {
            return false;
        }
        final String normalized = input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return normalized.equals("single-thread")
            || normalized.equals("high-clock")
            || normalized.equals("high-core")
            || normalized.equals("multi-thread")
            || normalized.equals("single")
            || normalized.equals("multi")
            || normalized.equals("stable")
            || normalized.equals("performance")
            || normalized.equals("clock")
            || normalized.equals("core")
            || normalized.equals("balanced");
    }

    private static String normalizeCpuMode(final String input) {
        final String normalized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "high-clock", "clock" -> "high-clock";
            case "high-core", "core" -> "high-core";
            case "multi-thread", "multi", "performance" -> "multi-thread";
            default -> "single-thread";
        };
    }

    private List<String> adminCompletions(final String[] args) {
        if (args.length == 1) {
            return matching(args[0], List.of("help", "reload", "modules", "module", "command", "plugins", "memory", "gc", "threads", "optimize", "ncr", "nochatreports", "chatreports", "motd", "web", "ai"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return matching(args[1], HunterHelp.topics());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("motd")) {
            return matching(args[1], List.of("status", "line1", "line2", "max"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("optimize")) {
            return matching(args[1], List.of("status", "single-thread", "high-clock", "high-core", "multi-thread"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("ncr") || args[0].equalsIgnoreCase("nochatreports") || args[0].equalsIgnoreCase("chatreports"))) {
            return matching(args[1], List.of("status", "on", "off", "convert", "query", "demand", "debug", "message"));
        }
        if (args.length == 3
            && (args[0].equalsIgnoreCase("ncr") || args[0].equalsIgnoreCase("nochatreports") || args[0].equalsIgnoreCase("chatreports"))
            && List.of("convert", "query", "demand", "debug").contains(args[1].toLowerCase(Locale.ROOT))) {
            return matching(args[2], List.of("on", "off"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("motd") && args[1].equalsIgnoreCase("max")) {
            return matching(args[2], List.of("default", "100", "500", "1000"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("web")) {
            return matching(args[1], List.of("status", "restart", "bind", "address", "port", "map", "public-map", "user", "remove", "users", "allow", "execution"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("web") && (args[1].equalsIgnoreCase("bind") || args[1].equalsIgnoreCase("address"))) {
            return matching(args[2], List.of("127.0.0.1", "0.0.0.0"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("web") && args[1].equalsIgnoreCase("port")) {
            return matching(args[2], List.of("8088", "8090", "8100"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("web") && args[1].equalsIgnoreCase("map")) {
            return matching(args[2], List.of("http://%host%:8100/"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("web") && args[1].equalsIgnoreCase("public-map")) {
            return matching(args[2], List.of("on", "off"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("web") && args[1].equalsIgnoreCase("user")) {
            return matching(args[3], List.of("admin", "player"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("web") && args[1].equalsIgnoreCase("allow")) {
            return matching(args[3], List.of("inherit", "none", "*", "help", "list", "spawn", "tps", "htps"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("web") && args[1].equalsIgnoreCase("execution")) {
            return matching(args[3], List.of("on", "off"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ai")) {
            return matching(args[1], List.of("status", "enable", "disable", "model", "base-url", "key", "clear-key", "env", "prefix", "chat", "npc", "temperature", "max-tokens", "test"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && (args[1].equalsIgnoreCase("chat") || args[1].equalsIgnoreCase("npc"))) {
            return matching(args[2], List.of("on", "off"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && args[1].equalsIgnoreCase("model")) {
            return matching(args[2], List.of("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1", "gpt-4o", "o4-mini"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && (args[1].equalsIgnoreCase("base-url") || args[1].equalsIgnoreCase("url"))) {
            return matching(args[2], List.of("https://api.openai.com/v1", "http://127.0.0.1:11434/v1"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && (args[1].equalsIgnoreCase("env") || args[1].equalsIgnoreCase("api-key-env"))) {
            return matching(args[2], List.of("OPENAI_API_KEY", "HUNTERCORE_AI_API_KEY"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && args[1].equalsIgnoreCase("prefix")) {
            return matching(args[2], List.of("@ai", "AI", "ai"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && args[1].equalsIgnoreCase("temperature")) {
            return matching(args[2], List.of("0.2", "0.7", "1.0"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && (args[1].equalsIgnoreCase("max-tokens") || args[1].equalsIgnoreCase("maxtokens"))) {
            return matching(args[2], List.of("256", "512", "1024"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ai") && args[1].equalsIgnoreCase("test")) {
            return matching(args[2], List.of("Say HunterCore AI is ready.", "用中文简短介绍服务器状态"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("module")) {
            return matching(args[1], MODULES);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("command")) {
            return matching(args[1], List.of(ESSENTIALS, MANAGEMENT, FAKE_PLAYERS, REAL_FAKE_PLAYERS, NPCS));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("command")) {
            final String module = args[1].toLowerCase(Locale.ROOT);
            if (module.equals(ESSENTIALS)) {
                return matching(args[2], HunterToolsPreferences.essentialsCommands());
            }
            if (module.equals(MANAGEMENT)) {
                return matching(args[2], HunterToolsPreferences.managementCommands());
            }
            if (module.equals(FAKE_PLAYERS) || module.equals(NPCS)) {
                return matching(args[2], HunterToolsPreferences.actorCommands());
            }
            if (module.equals(REAL_FAKE_PLAYERS)) {
                return matching(args[2], HunterToolsPreferences.realFakePlayerCommands());
            }
        }
        if ((args.length == 3 && args[0].equalsIgnoreCase("module")) || (args.length == 4 && args[0].equalsIgnoreCase("command"))) {
            return matching(args[args.length - 1], List.of("on", "off"));
        }
        return List.of();
    }

    private List<String> onlinePlayerNames() {
        if (this.preferences.booleanValue("optimizations.enabled", true) && this.preferences.booleanValue("optimizations.hunter-tools.player-cache", true)) {
            return this.cachedPlayerNames;
        }
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    MetricsSnapshot metricsSnapshot() {
        return this.snapshot;
    }

    void publishSyntheticChat(final String sender, final String source, final String message) {
        if (this.webPanelManager != null) {
            this.webPanelManager.observeSyntheticChat(sender, source, message);
        }
    }

    void observeWebChat(final String sender, final String message) {
        if (this.realFakePlayerManager != null) {
            this.realFakePlayerManager.observeWebChat(sender, message);
        }
    }

    int actorLiveCount(final String module) {
        if (module.equals(REAL_FAKE_PLAYERS)) {
            return this.realFakePlayerManager == null ? 0 : this.realFakePlayerManager.liveCount();
        }
        return this.actorManager == null ? 0 : this.actorManager.liveCount(module);
    }

    List<HunterActorManager.ActorView> actorViews(final String module) {
        return this.actorManager == null ? List.of() : this.actorManager.views(module);
    }

    List<HunterRealFakePlayerManager.RealFakePlayerView> realFakePlayerViews() {
        return this.realFakePlayerManager == null ? List.of() : this.realFakePlayerManager.views();
    }

    List<HunterRealFakePlayerManager.PendingRiskApprovalView> pendingRiskApprovalViews() {
        return this.realFakePlayerManager == null ? List.of() : this.realFakePlayerManager.pendingApprovalViews();
    }

    boolean setActorClickCommand(final String module, final String id, final String command) {
        if (module.equals(REAL_FAKE_PLAYERS)) {
            return this.realFakePlayerManager != null && this.realFakePlayerManager.setClickCommand(id, command);
        }
        return this.actorManager != null && this.actorManager.setClickCommand(module, id, command);
    }

    boolean setActorAi(final String module, final String id, final boolean enabled, final String persona) {
        if (module.equals(REAL_FAKE_PLAYERS)) {
            return this.realFakePlayerManager != null && this.realFakePlayerManager.setAi(id, enabled, persona);
        }
        return this.actorManager != null && this.actorManager.setActorAi(module, id, enabled, persona);
    }

    CompletableFuture<String> testAiPrompt(final String prompt) {
        return this.aiManager == null
            ? CompletableFuture.failedFuture(new IllegalStateException("HunterCore AI manager is not available."))
            : this.aiManager.completeTest(prompt);
    }

    boolean aiApiKeyConfigured() {
        return this.aiManager != null && this.aiManager.apiKeyConfigured();
    }

    private String language() {
        return this.preferences == null ? HunterCoreProvider.get().language() : this.preferences.language();
    }

    private String text(final String zhCn, final String enUs) {
        return HunterLanguage.choose(this.language(), zhCn, enUs);
    }

    private void sendHelp(final CommandSender sender, final String... args) {
        HunterHelp.send(sender, this.language(), args);
    }

    private void sendUsage(final CommandSender sender, final String usage, final String zhCn, final String enUs) {
        sender.sendMessage(ChatColor.GRAY + usage + ChatColor.DARK_GRAY + " - " + ChatColor.WHITE + this.text(zhCn, enUs));
    }

    private void sendCommandHelp(final CommandSender sender, final String command, final String[] args) {
        final String topic = helpTopic(command);
        if (args.length >= 2) {
            this.sendHelp(sender, topic + " " + HunterToolsPreferences.normalize(args[1]));
            return;
        }
        this.sendHelp(sender, topic);
    }

    private static boolean isHelp(final String value) {
        final String normalized = HunterToolsPreferences.normalize(value);
        return normalized.equals("help") || normalized.equals("?") || normalized.equals("usage");
    }

    private static String helpTopic(final String command) {
        return switch (HunterToolsPreferences.normalize(command)) {
            case "htps" -> "tps";
            case "gms", "gmc", "gma", "gmsp" -> "gm";
            case "bc" -> "broadcast";
            case "cc" -> "clearchat";
            case "workbench", "wb" -> "craft";
            case "ec" -> "enderchest";
            case "disposal" -> "trash";
            case "player" -> "player";
            case "npc" -> "npc";
            default -> HunterToolsPreferences.normalize(command);
        };
    }

    private void sendCommandOverride(final Player player, final String target) {
        final List<String> lines = this.preferences.stringList(
            "modules.command-overrides.messages." + target,
            HunterToolsPreferences.defaultCommandOverrideLines(target)
        );
        for (final String line : lines) {
            player.sendMessage(color(this.renderCommandOverrideLine(line, player)));
        }
    }

    private String renderCommandOverrideLine(final String line, final Player player) {
        final String pluginCount = String.valueOf(Bukkit.getPluginManager().getPlugins().length);
        return (line == null ? "" : line)
            .replace("%player%", player.getName())
            .replace("%player_uuid%", player.getUniqueId().toString())
            .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()))
            .replace("%server%", this.preferences.stringValue("modules.web-panel.server-name", "HunterCore"))
            .replace("%version%", Bukkit.getVersion())
            .replace("%plugins%", pluginCount)
            .replace("%plugin_count%", pluginCount);
    }

    private List<String> sidebarLines() {
        return this.preferences.stringList("modules.sidebar.lines", HunterToolsPreferences.defaultSidebarLines());
    }

    private static PlayerView playerView(final Player player) {
        return new PlayerView(player.getUniqueId(), player.getName(), player.getWorld().getName(), player.getPing());
    }

    private static String renderDisplayLine(final String template, final MetricsSnapshot snapshot, final PlayerView player, final String serverName) {
        final String source = template == null ? "" : template;
        return source
            .replace("%player%", player.name())
            .replace("%world%", player.world())
            .replace("%ping%", String.valueOf(player.ping()))
            .replace("%server%", serverName == null || serverName.isBlank() ? "HunterCore" : serverName)
            .replace("%version%", Bukkit.getVersion())
            .replace("%tps_color%", tpsLegacyColor(snapshot.tps1()))
            .replace("%tps%", MetricsSnapshot.formatTps(snapshot.tps1()))
            .replace("%tps_1%", MetricsSnapshot.formatTps(snapshot.tps1()))
            .replace("%tps_5%", MetricsSnapshot.formatTps(snapshot.tps5()))
            .replace("%tps_15%", MetricsSnapshot.formatTps(snapshot.tps15()))
            .replace("%mspt%", String.format(Locale.ROOT, "%.1f", snapshot.mspt()))
            .replace("%online%", String.valueOf(snapshot.onlinePlayers()))
            .replace("%max%", String.valueOf(snapshot.maxPlayers()))
            .replace("%memory%", snapshot.memoryLine());
    }

    private boolean canUseOp(final Player player) {
        return player.isOp() || player.hasPermission("minecraft.command.op") || player.hasPermission("bukkit.command.op");
    }

    private static List<String> matching(final String prefix, final Collection<String> values) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private static String webAllowedLine(final HunterToolsPreferences.WebUser user) {
        if (!user.allowedCommandsConfigured()) {
            return "inherit";
        }
        if (user.allowedCommands().isEmpty()) {
            return "none";
        }
        return String.join(",", user.allowedCommands());
    }

    private static String normalizeWebCommand(final String command) {
        return command.replaceFirst("^/+", "").trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    }

    private static String commandRoot(final String message) {
        final String root = normalizeWebCommand(message);
        final int namespace = root.indexOf(':');
        return namespace >= 0 && namespace + 1 < root.length() ? root.substring(namespace + 1) : root;
    }

    private static String[] commandArguments(final String message) {
        final String command = message.replaceFirst("^/+", "").trim();
        final int space = command.indexOf(' ');
        if (space < 0 || space + 1 >= command.length()) {
            return new String[0];
        }
        final String arguments = command.substring(space + 1).trim();
        return arguments.isBlank() ? new String[0] : arguments.split("\\s+");
    }

    private static boolean isAir(final ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static String color(final String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static NamedTextColor tpsColor(final double tps) {
        if (tps >= 18.0D) {
            return NamedTextColor.GREEN;
        }
        if (tps >= 15.0D) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private static ChatColor colorCode(final double tps) {
        if (tps >= 18.0D) {
            return ChatColor.GREEN;
        }
        if (tps >= 15.0D) {
            return ChatColor.YELLOW;
        }
        return ChatColor.RED;
    }

    private static String tpsLegacyColor(final double tps) {
        if (tps >= 18.0D) {
            return "&a";
        }
        if (tps >= 15.0D) {
            return "&e";
        }
        return "&c";
    }

    private record PlayerView(UUID uuid, String name, String world, int ping) {
    }

    private record GuiChatSession(String kind, @Nullable String module, @Nullable String id) {
    }

    private record GuiConfirmSession(String action, @Nullable String module, @Nullable String id) {
    }

    private record GuiCommand(
        Material material,
        String zhName,
        String enName,
        List<String> zhLore,
        List<String> enLore,
        String command,
        boolean prompt,
        String zhPrompt,
        String enPrompt
    ) {
    }

    private enum GuiPage {
        MAIN,
        PROFILE,
        INVENTORY_PREVIEW,
        SETTINGS,
        ADMIN,
        ADMIN_SYSTEM,
        ADMIN_PLUGINS,
        ADMIN_MODULES,
        ADMIN_PREFERENCES,
        ADMIN_OPTIMIZE,
        ADMIN_CHAT_REPORTS,
        ADMIN_AI,
        ADMIN_WEB,
        ADMIN_MOTD,
        ADMIN_COMMANDS,
        TOOLS,
        COMMAND_CENTER,
        ACTOR_LIST,
        ACTOR_DETAIL,
        STORY,
        TITLE_LIST,
        CONFIRM
    }

    private static final class GuiHolder implements InventoryHolder {
        private final GuiPage page;
        private final String module;
        private final String id;
        private final String action;
        private Inventory inventory;

        private GuiHolder(final GuiPage page, @Nullable final String module, @Nullable final String id, @Nullable final String action) {
            this.page = page;
            this.module = module;
            this.id = id;
            this.action = action;
        }

        private void attach(final Inventory inventory) {
            this.inventory = inventory;
        }

        private GuiPage page() {
            return this.page;
        }

        private @Nullable String module() {
            return this.module;
        }

        private @Nullable String id() {
            return this.id;
        }

        private @Nullable String action() {
            return this.action;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    private static final class SidebarBoard {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private List<String> entries;

        private SidebarBoard(final Scoreboard scoreboard, final Objective objective, final List<String> entries) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.entries = entries;
        }
    }
}
