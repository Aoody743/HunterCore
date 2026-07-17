package org.huntercore.plugins.tpa;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.HunterLanguage;
import org.huntercore.api.gui.HunterGuiActionContext;
import org.huntercore.api.gui.HunterGuiConfirmationResult;
import org.huntercore.api.gui.HunterGuiOpenResult;
import org.huntercore.api.gui.HunterGuiPage;
import org.huntercore.api.gui.HunterGuiPagination;
import org.huntercore.api.gui.HunterGuiRegistration;
import org.huntercore.api.gui.HunterGuiRenderContext;
import org.huntercore.api.gui.HunterGuiRoute;
import org.huntercore.api.gui.HunterGuiScreen;
import org.huntercore.api.gui.HunterGuiView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HunterTpaPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final long REQUEST_TTL_MILLIS = 60_000L;
    private static final String DEFAULT_HOME = "home";
    private static final String TELEPORT_GUI_TITLE = ChatColor.DARK_AQUA + "HunterTPA · Teleport";
    private static final String HOMES_GUI_TITLE = ChatColor.DARK_GREEN + "HunterTPA · 我的家";
    private static final String DELETE_HOME_GUI_TITLE = ChatColor.DARK_RED + "HunterTPA · Delete Home";
    private static final String REQUESTS_GUI_TITLE = ChatColor.DARK_PURPLE + "HunterTPA · Requests";
    private static final String SHARED_TELEPORT_SCREEN = "hunter-tpa:teleport";
    private static final String SHARED_HOMES_SCREEN = "hunter-tpa:homes";
    private static final String SHARED_REQUESTS_SCREEN = "hunter-tpa:requests";
    private static final String SHARED_DELETE_HOME_SCREEN = "hunter-tpa:delete-home";
    private static final String SHARED_HOME_ARGUMENT = "home";
    private static final String SHARED_PAGE_ARGUMENT = "page";
    private static final String SHARED_DELETE_HOME_CONFIRMATION_PREFIX = "hunter-tpa:delete-home:";
    private static final Duration SHARED_DELETE_HOME_CONFIRMATION_TTL = Duration.ofSeconds(40L);
    private static final int SHARED_LIST_PAGE_SIZE = 36;
    private static final String ACTION_OPEN_HOMES = "hunter-tpa:open-homes";
    private static final String ACTION_OPEN_TELEPORT = "hunter-tpa:open-teleport";
    private static final String ACTION_OPEN_REQUESTS = "hunter-tpa:open-requests";
    private static final String ACTION_RUN_SPAWN = "hunter-tpa:run-spawn";
    private static final String ACTION_RUN_BACK = "hunter-tpa:run-back";
    private static final String ACTION_TOGGLE_REQUESTS = "hunter-tpa:toggle-requests";
    private static final String ACTION_RANDOM_TELEPORT = "hunter-tpa:random-teleport";
    private static final String ACTION_CLOSE = "hunter-tpa:close";
    private static final String ACTION_REQUEST_PLAYER = "hunter-tpa:request-player";
    private static final String ACTION_CREATE_HOME = "hunter-tpa:create-home";
    private static final String ACTION_SET_HOME = "hunter-tpa:set-home";
    private static final String ACTION_REFRESH_HOMES = "hunter-tpa:refresh-homes";
    private static final String ACTION_UNAVAILABLE_HOME = "hunter-tpa:unavailable-home";
    private static final String ACTION_HOME = "hunter-tpa:home";
    private static final String ACTION_ACCEPT_REQUEST = "hunter-tpa:accept-request";
    private static final String ACTION_DENY_REQUEST = "hunter-tpa:deny-request";
    private static final String ACTION_CANCEL_OUTGOING = "hunter-tpa:cancel-outgoing";
    private static final String ACTION_REFRESH_REQUESTS = "hunter-tpa:refresh-requests";
    private static final String ACTION_CONFIRM_HOME_DELETE = "hunter-tpa:confirm-home-delete";
    private static final String ACTION_CANCEL_HOME_DELETE = "hunter-tpa:cancel-home-delete";
    private static final String ACTION_PREVIOUS_PAGE = "hunter-tpa:previous-page";
    private static final String ACTION_NEXT_PAGE = "hunter-tpa:next-page";

    private final Map<UUID, TeleportRequest> incoming = new HashMap<>();
    private final Map<UUID, UUID> outgoing = new HashMap<>();
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, PendingHomeDelete> pendingHomeDeletes = new HashMap<>();
    private HunterGuiRegistration sharedGuiRegistration;
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
        this.sharedGuiRegistration = HunterCoreProvider.get().gui().register(this, List.of(
            new SharedTeleportGuiScreen(),
            new SharedHomesGuiScreen(),
            new SharedRequestsGuiScreen(),
            new SharedDeleteHomeGuiScreen()
        ));
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getScheduler().runTaskTimer(this, this::expireRequests, 20L * 10L, 20L * 10L);
    }

    @Override
    public void onDisable() {
        if (this.sharedGuiRegistration != null) {
            this.sharedGuiRegistration.close();
            this.sharedGuiRegistration = null;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof TpaGuiHolder) {
                player.closeInventory();
            }
        }
        this.getServer().getScheduler().cancelTasks(this);
        this.incoming.clear();
        this.outgoing.clear();
        this.pendingTeleports.clear();
        this.cooldowns.clear();
        this.pendingHomeDeletes.clear();
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
        if (this.openSharedGui(player, this.sharedTeleportRoute())) {
            return true;
        }
        final TpaGuiHolder holder = new TpaGuiHolder(GuiScreen.TELEPORT);
        final Inventory inventory = Bukkit.createInventory(holder, 54, TELEPORT_GUI_TITLE);
        inventory.setItem(4, item(Material.ENDER_PEARL, this.text("传送中心", "Teleport Center"), List.of(this.text("左键玩家：传送到对方", "Left-click player: teleport to them"), this.text("右键玩家：让对方传送到你", "Right-click player: invite them to you"))));
        inventory.setItem(45, item(Material.RED_BED, this.text("我的家", "My Homes"), List.of("/homes")));
        holder.bind(45, GuiAction.of(GuiActionType.OPEN_HOMES));
        inventory.setItem(46, item(Material.COMPASS, "Spawn", List.of("/spawn")));
        holder.bind(46, GuiAction.of(GuiActionType.RUN_SPAWN));
        inventory.setItem(47, item(Material.CLOCK, "Back", List.of("/back")));
        holder.bind(47, GuiAction.of(GuiActionType.RUN_BACK));
        inventory.setItem(48, this.requestsMenuItem(player));
        holder.bind(48, GuiAction.of(GuiActionType.OPEN_REQUESTS));
        inventory.setItem(49, item(this.requestsDisabled(player) ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK, this.requestsDisabled(player) ? this.text("TPA 已关闭", "TPA disabled") : this.text("TPA 已开启", "TPA enabled"), List.of(this.text("点击切换是否接收传送请求。", "Click to toggle incoming teleport requests."))));
        holder.bind(49, GuiAction.of(GuiActionType.TOGGLE_REQUESTS));
        inventory.setItem(51, item(Material.GRASS_BLOCK, "RTP", List.of(this.text("随机传送到当前世界的安全位置。", "Random teleport to a safe location in this world."))));
        holder.bind(51, GuiAction.of(GuiActionType.RANDOM_TELEPORT));
        inventory.setItem(53, item(Material.BARRIER, this.text("关闭", "Close"), List.of()));
        holder.bind(53, GuiAction.of(GuiActionType.CLOSE));
        inventory.setItem(52, this.outgoingRequestItem(player));
        holder.bind(52, GuiAction.of(GuiActionType.OPEN_REQUESTS));
        int slot = 9;
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) {
                break;
            }
            if (online.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            inventory.setItem(slot, item(Material.PLAYER_HEAD, online.getName(), List.of(
                this.text("世界：", "World: ") + online.getWorld().getName(),
                this.text("左键：/tpa ", "Left: /tpa ") + online.getName(),
                this.text("右键：/tpahere ", "Right: /tpahere ") + online.getName()
            )));
            holder.bind(slot, GuiAction.withPayload(GuiActionType.REQUEST_PLAYER, online.getUniqueId().toString()));
            slot++;
        }
        player.openInventory(inventory);
        return true;
    }

    private boolean openHomesGui(final Player player) {
        if (this.openSharedGui(player, this.sharedHomesRoute())) {
            return true;
        }
        final TpaGuiHolder holder = new TpaGuiHolder(GuiScreen.HOMES);
        final Inventory inventory = Bukkit.createInventory(holder, 54, HOMES_GUI_TITLE);
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
            inventory.setItem(slot, item(material, home, List.of(
                world == null ? this.text("世界：未知或已删除", "World: unknown or deleted") : this.text("世界：", "World: ") + world.getName(),
                location == null ? this.text("坐标：未知", "Coords: unknown") : this.text("坐标：", "Coords: ") + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(),
                currentWorld ? this.text("当前世界 Home", "Home in your current world") : "",
                this.text("左键：传送", "Left-click: teleport"),
                this.text("右键：重命名提示", "Right-click: rename hint"),
                this.text("Shift 右键：删除", "Shift-right-click: delete")
            ), currentWorld));
            holder.bind(slot, location == null || world == null
                ? GuiAction.of(GuiActionType.UNAVAILABLE_HOME)
                : GuiAction.withPayload(GuiActionType.HOME, home));
            slot++;
        }
        inventory.setItem(45, item(Material.LIME_BED, this.text("新建 Home", "New Home"), List.of(this.text("保存当前位置为下一个 home。", "Save this location as the next home."))));
        holder.bind(45, GuiAction.of(GuiActionType.CREATE_HOME));
        inventory.setItem(46, item(Material.ENDER_PEARL, this.text("传送中心", "Teleport Center"), List.of("/tpgui")));
        holder.bind(46, GuiAction.of(GuiActionType.OPEN_TELEPORT));
        inventory.setItem(47, item(Material.NAME_TAG, this.text("设置 default home", "Set default home"), List.of("/sethome home")));
        holder.bind(47, GuiAction.withPayload(GuiActionType.SET_HOME, DEFAULT_HOME));
        inventory.setItem(48, item(Material.NAME_TAG, this.text("设置 home1", "Set home1"), List.of("/sethome home1")));
        holder.bind(48, GuiAction.withPayload(GuiActionType.SET_HOME, "home1"));
        inventory.setItem(49, item(Material.NAME_TAG, this.text("刷新", "Refresh"), List.of(this.text("重新加载 Home GUI。", "Refresh this homes GUI."))));
        holder.bind(49, GuiAction.of(GuiActionType.REFRESH_HOMES));
        inventory.setItem(50, item(Material.NAME_TAG, this.text("设置 home2", "Set home2"), List.of("/sethome home2")));
        holder.bind(50, GuiAction.withPayload(GuiActionType.SET_HOME, "home2"));
        inventory.setItem(51, item(Material.NAME_TAG, this.text("设置 home3", "Set home3"), List.of("/sethome home3")));
        holder.bind(51, GuiAction.withPayload(GuiActionType.SET_HOME, "home3"));
        inventory.setItem(52, item(Material.NAME_TAG, this.text("设置 home4", "Set home4"), List.of("/sethome home4")));
        holder.bind(52, GuiAction.withPayload(GuiActionType.SET_HOME, "home4"));
        inventory.setItem(53, item(Material.BARRIER, this.text("关闭", "Close"), List.of()));
        holder.bind(53, GuiAction.of(GuiActionType.CLOSE));
        player.openInventory(inventory);
        return true;
    }

    private void openDeleteHomeGui(final Player player, final String home) {
        if (!this.homeExists(player, home)) {
            player.sendMessage(this.text("这个家已不存在，已刷新列表。", "That home no longer exists. The list was refreshed."));
            this.openHomesGui(player);
            return;
        }
        this.pendingHomeDeletes.put(
            player.getUniqueId(),
            new PendingHomeDelete(home, System.currentTimeMillis() + SHARED_DELETE_HOME_CONFIRMATION_TTL.toMillis())
        );
        final TpaGuiHolder holder = new TpaGuiHolder(GuiScreen.DELETE_HOME);
        final Inventory inventory = Bukkit.createInventory(holder, 27, DELETE_HOME_GUI_TITLE);
        inventory.setItem(11, item(Material.LIME_WOOL, this.text("确认删除 ", "Confirm delete ") + home, List.of(
            this.text("这个操作不可撤销。", "This cannot be undone."),
            this.text("确认窗口将在 40 秒后失效。", "This confirmation expires after 40 seconds.")
        )));
        holder.bind(11, GuiAction.of(GuiActionType.CONFIRM_HOME_DELETE));
        inventory.setItem(13, item(Material.RED_BED, home, List.of(this.text("即将删除这个家。", "This home will be deleted."))));
        inventory.setItem(15, item(Material.RED_WOOL, this.text("取消", "Cancel"), List.of(this.text("返回家列表。", "Return to homes."))));
        holder.bind(15, GuiAction.of(GuiActionType.CANCEL_HOME_DELETE));
        player.openInventory(inventory);
    }

    private boolean openRequestsGui(final Player player) {
        if (this.openSharedGui(player, this.sharedRequestsRoute())) {
            return true;
        }
        final TpaGuiHolder holder = new TpaGuiHolder(GuiScreen.REQUESTS);
        final Inventory inventory = Bukkit.createInventory(holder, 27, REQUESTS_GUI_TITLE);
        final TeleportRequest incomingRequest = this.incomingRequestFor(player.getUniqueId());
        final TeleportRequest outgoingRequest = this.outgoingRequestFor(player);

        inventory.setItem(4, item(Material.ENCHANTED_BOOK, this.text("Requests", "Requests"), List.of(
            incomingRequest == null ? this.text("No incoming request.", "No incoming request.") : this.requestSummary(incomingRequest, true),
            outgoingRequest == null ? this.text("No outgoing request.", "No outgoing request.") : this.requestSummary(outgoingRequest, false)
        )));
        inventory.setItem(11, incomingRequest == null
            ? item(Material.GRAY_DYE, this.text("Accept", "Accept"), List.of(this.text("No incoming request.", "No incoming request.")))
            : item(Material.LIME_WOOL, this.text("Accept", "Accept"), List.of(this.requestSummary(incomingRequest, true))));
        if (incomingRequest != null) {
            holder.bind(11, GuiAction.of(GuiActionType.ACCEPT_REQUEST));
        }
        inventory.setItem(13, incomingRequest == null
            ? item(Material.GRAY_DYE, this.text("Inbox", "Inbox"), List.of(this.text("Nothing to review.", "Nothing to review.")))
            : item(Material.PLAYER_HEAD, this.text("Requester", "Requester"), List.of(this.requestSummary(incomingRequest, true))));
        inventory.setItem(15, incomingRequest == null
            ? item(Material.GRAY_DYE, this.text("Deny", "Deny"), List.of(this.text("No incoming request.", "No incoming request.")))
            : item(Material.RED_WOOL, this.text("Deny", "Deny"), List.of(this.requestSummary(incomingRequest, true))));
        if (incomingRequest != null) {
            holder.bind(15, GuiAction.of(GuiActionType.DENY_REQUEST));
        }
        inventory.setItem(21, outgoingRequest == null
            ? item(Material.GRAY_DYE, this.text("Outgoing", "Outgoing"), List.of(this.text("No outgoing request.", "No outgoing request.")))
            : item(Material.CLOCK, this.text("Cancel outgoing", "Cancel outgoing"), List.of(this.requestSummary(outgoingRequest, false))));
        if (outgoingRequest != null) {
            holder.bind(21, GuiAction.of(GuiActionType.CANCEL_OUTGOING));
        }
        inventory.setItem(23, item(Material.COMPASS, this.text("Refresh", "Refresh"), List.of(this.text("Reload the request state.", "Reload the request state."))));
        holder.bind(23, GuiAction.of(GuiActionType.REFRESH_REQUESTS));
        inventory.setItem(26, item(Material.ARROW, this.text("Back", "Back"), List.of("/tpgui")));
        holder.bind(26, GuiAction.of(GuiActionType.OPEN_TELEPORT));
        player.openInventory(inventory);
        return true;
    }

    private boolean openSharedGui(final Player player, final HunterGuiRoute route) {
        final HunterGuiRegistration registration = this.sharedGuiRegistration;
        if (registration == null || !registration.active()) {
            return false;
        }
        final HunterGuiOpenResult result = registration.open(player, route);
        if (result.opened()) {
            return true;
        }
        this.getLogger().fine("HunterTPA shared GUI unavailable for " + player.getName() + ": " + result.name());
        return false;
    }

    private HunterGuiRoute sharedTeleportRoute() {
        return this.sharedTeleportRoute(0);
    }

    private HunterGuiRoute sharedTeleportRoute(final int page) {
        return this.sharedPagedRoute(SHARED_TELEPORT_SCREEN, page);
    }

    private HunterGuiRoute sharedHomesRoute() {
        return this.sharedHomesRoute(0);
    }

    private HunterGuiRoute sharedHomesRoute(final int page) {
        return this.sharedPagedRoute(SHARED_HOMES_SCREEN, page);
    }

    private HunterGuiRoute sharedRequestsRoute() {
        return HunterGuiRoute.of(SHARED_REQUESTS_SCREEN);
    }

    private HunterGuiRoute sharedDeleteHomeRoute(final String home) {
        return HunterGuiRoute.of(SHARED_DELETE_HOME_SCREEN, Map.of(SHARED_HOME_ARGUMENT, home));
    }

    private HunterGuiRoute sharedPagedRoute(final String screenId, final int page) {
        if (page <= 0) {
            return HunterGuiRoute.of(screenId);
        }
        return HunterGuiRoute.of(screenId, Map.of(SHARED_PAGE_ARGUMENT, Integer.toString(page)));
    }

    private HunterGuiView renderSharedTeleportGui(final HunterGuiRenderContext context) {
        final boolean chinese = this.usesChinese(context);
        final Player player = context.player();
        final HunterGuiView.Builder view = HunterGuiView.builder(
            Component.text(this.sharedText(chinese, "HunterTPA · 传送中心", "HunterTPA · Teleport Center")),
            6
        );
        view.item(4, this.item(Material.ENDER_PEARL, this.sharedText(chinese, "传送中心", "Teleport Center"), List.of(
            this.sharedText(chinese, "左键玩家：传送到对方", "Left-click player: teleport to them"),
            this.sharedText(chinese, "右键玩家：让对方传送到你", "Right-click player: invite them to you")
        )));
        view.button(45, this.item(Material.RED_BED, this.sharedText(chinese, "我的家", "My Homes"), List.of("/homes")), ACTION_OPEN_HOMES,
            action -> action.navigate(this.sharedHomesRoute()));
        view.button(46, this.item(Material.COMPASS, "Spawn", List.of("/spawn")), ACTION_RUN_SPAWN, action -> {
            action.player().performCommand("spawn");
            action.refresh();
        });
        view.button(47, this.item(Material.CLOCK, "Back", List.of("/back")), ACTION_RUN_BACK, action -> {
            action.player().performCommand("back");
            action.refresh();
        });
        view.button(48, this.sharedRequestsMenuItem(player, chinese), ACTION_OPEN_REQUESTS,
            action -> action.navigate(this.sharedRequestsRoute()));
        final boolean requestsDisabled = this.requestsDisabled(player);
        view.button(
            49,
            this.item(
                requestsDisabled ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                requestsDisabled ? this.sharedText(chinese, "TPA 已关闭", "TPA disabled") : this.sharedText(chinese, "TPA 已开启", "TPA enabled"),
                List.of(this.sharedText(chinese, "点击切换是否接收传送请求。", "Click to toggle incoming teleport requests."))
            ),
            ACTION_TOGGLE_REQUESTS,
            action -> {
                this.toggleRequests(action.player());
                action.refresh();
            }
        );
        view.button(51, this.item(Material.GRASS_BLOCK, "RTP", List.of(
            this.sharedText(chinese, "随机传送到当前世界的安全位置。", "Random teleport to a safe location in this world.")
        )), ACTION_RANDOM_TELEPORT, action -> {
            this.randomTeleport(action.player());
            action.close();
        });
        view.button(52, this.sharedOutgoingRequestItem(player, chinese), ACTION_OPEN_REQUESTS,
            action -> action.navigate(this.sharedRequestsRoute()));
        view.button(53, this.item(Material.BARRIER, this.sharedText(chinese, "关闭", "Close"), List.of()), ACTION_CLOSE, action -> {
            this.playGuiSound(action.player(), Sound.UI_BUTTON_CLICK);
            action.close();
        });

        final List<Player> targets = new ArrayList<>();
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                targets.add(online);
            }
        }
        final HunterGuiPage<Player> targetPage = HunterGuiPagination.page(
            targets,
            this.sharedRoutePage(context.route()),
            SHARED_LIST_PAGE_SIZE
        );
        this.addSharedPagination(view, chinese, targetPage, this::sharedTeleportRoute);

        int slot = 9;
        for (final Player online : targetPage.items()) {
            final UUID targetId = online.getUniqueId();
            final String targetName = online.getName();
            final String targetWorld = online.getWorld().getName();
            view.button(slot, this.item(Material.PLAYER_HEAD, targetName, List.of(
                this.sharedText(chinese, "世界：", "World: ") + targetWorld,
                this.sharedText(chinese, "左键：/tpa ", "Left: /tpa ") + targetName,
                this.sharedText(chinese, "右键：/tpahere ", "Right: /tpahere ") + targetName
            )), ACTION_REQUEST_PLAYER, action -> this.requestSharedTeleport(action, targetId));
            slot++;
        }
        return view.build();
    }

    private void requestSharedTeleport(final HunterGuiActionContext context, final UUID targetId) {
        final Player requester = context.player();
        final ClickType click = context.clickType();
        if (!click.isLeftClick() && !click.isRightClick()) {
            requester.sendMessage(this.sharedText(this.usesChinese(context), "请使用左键或右键发送传送请求。", "Use left-click or right-click to send a teleport request."));
            context.refresh();
            return;
        }
        final Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline() || target.getUniqueId().equals(requester.getUniqueId())) {
            requester.sendMessage(this.sharedText(this.usesChinese(context), "该玩家已不在线，已刷新列表。", "That player is no longer online. The list was refreshed."));
            context.refresh();
            return;
        }
        this.requestTeleport(
            requester,
            target,
            click.isRightClick() ? TeleportType.TARGET_TO_REQUESTER : TeleportType.TO_TARGET
        );
        context.close();
    }

    private void addSharedPagination(
        final HunterGuiView.Builder view,
        final boolean chinese,
        final HunterGuiPage<?> page,
        final IntFunction<HunterGuiRoute> routeForPage
    ) {
        final String pageSummary = this.sharedText(
            chinese,
            "第 " + (page.pageIndex() + 1) + " / " + page.pageCount() + " 页 · 共 " + page.totalItems() + " 项",
            "Page " + (page.pageIndex() + 1) + " / " + page.pageCount() + " · " + page.totalItems() + " total"
        );
        if (page.hasPrevious()) {
            view.button(0, this.item(Material.ARROW, this.sharedText(chinese, "上一页", "Previous page"), List.of(pageSummary)), ACTION_PREVIOUS_PAGE,
                action -> action.navigate(routeForPage.apply(page.pageIndex() - 1)));
        } else {
            view.item(0, this.item(Material.GRAY_DYE, this.sharedText(chinese, "已是第一页", "First page"), List.of(pageSummary)));
        }
        if (page.hasNext()) {
            view.button(8, this.item(Material.ARROW, this.sharedText(chinese, "下一页", "Next page"), List.of(pageSummary)), ACTION_NEXT_PAGE,
                action -> action.navigate(routeForPage.apply(page.pageIndex() + 1)));
        } else {
            view.item(8, this.item(Material.GRAY_DYE, this.sharedText(chinese, "已是最后一页", "Last page"), List.of(pageSummary)));
        }
    }

    private void returnToSharedScreen(final HunterGuiActionContext context, final HunterGuiRoute fallback) {
        if (!context.back()) {
            context.navigate(fallback);
        }
    }

    private HunterGuiView renderSharedHomesGui(final HunterGuiRenderContext context) {
        final boolean chinese = this.usesChinese(context);
        final Player player = context.player();
        final HunterGuiView.Builder view = HunterGuiView.builder(
            Component.text(this.sharedText(chinese, "HunterTPA · 我的家", "HunterTPA · My Homes")),
            6
        );
        view.item(4, this.item(Material.RED_BED, this.sharedText(chinese, "我的家", "My Homes"), List.of(
            this.sharedText(chinese, "左键传送，右键重命名提示，Shift 右键删除。", "Left-click to teleport, right-click rename hint, shift-right-click to delete."),
            this.sharedText(chinese, "/homes list 可输出文字列表。", "/homes list prints a text list.")
        )));

        final List<String> canonicalHomes = this.homeNames(player).stream()
            .filter(HunterTpaPlugin::isCanonicalHomeName)
            .toList();
        final HunterGuiPage<String> homesPage = HunterGuiPagination.page(
            canonicalHomes,
            this.sharedRoutePage(context.route()),
            SHARED_LIST_PAGE_SIZE
        );
        this.addSharedPagination(view, chinese, homesPage, this::sharedHomesRoute);

        int slot = 9;
        for (final String home : homesPage.items()) {
            final Location location = this.loadHome(player, home);
            final World world = location == null ? null : location.getWorld();
            final Material material = home.equals(DEFAULT_HOME) ? Material.RED_BED : world == null ? Material.BARRIER : switch (world.getEnvironment()) {
                case NETHER -> Material.NETHERRACK;
                case THE_END -> Material.END_STONE;
                default -> Material.GRASS_BLOCK;
            };
            final boolean currentWorld = world != null && world.equals(player.getWorld());
            final ItemStack homeItem = this.item(material, home, List.of(
                world == null
                    ? this.sharedText(chinese, "世界：未知或已删除", "World: unknown or deleted")
                    : this.sharedText(chinese, "世界：", "World: ") + world.getName(),
                location == null
                    ? this.sharedText(chinese, "坐标：未知", "Coords: unknown")
                    : this.sharedText(chinese, "坐标：", "Coords: ") + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(),
                currentWorld ? this.sharedText(chinese, "当前世界 Home", "Home in your current world") : "",
                this.sharedText(chinese, "左键：传送", "Left-click: teleport"),
                this.sharedText(chinese, "右键：重命名提示", "Right-click: rename hint"),
                this.sharedText(chinese, "Shift 右键：删除", "Shift-right-click: delete")
            ), currentWorld);
            if (location == null || world == null) {
                view.button(slot, homeItem, ACTION_UNAVAILABLE_HOME, action -> {
                    action.player().sendMessage(this.sharedText(this.usesChinese(action), "这个家不可用。", "That home is not available."));
                    action.refresh();
                });
            } else {
                view.button(slot, homeItem, ACTION_HOME, action -> this.handleSharedHomeAction(action, home));
            }
            slot++;
        }

        view.button(45, this.item(Material.LIME_BED, this.sharedText(chinese, "新建 Home", "New Home"), List.of(
            this.sharedText(chinese, "保存当前位置为下一个 home。", "Save this location as the next home.")
        )), ACTION_CREATE_HOME, action -> {
            this.setHome(action.player(), new String[] {this.nextHomeName(action.player())});
            action.refresh();
        });
        view.button(46, this.item(Material.ENDER_PEARL, this.sharedText(chinese, "传送中心", "Teleport Center"), List.of("/tpgui")), ACTION_OPEN_TELEPORT,
            action -> this.returnToSharedScreen(action, this.sharedTeleportRoute()));
        view.button(47, this.item(Material.NAME_TAG, this.sharedText(chinese, "设置 default home", "Set default home"), List.of("/sethome home")), ACTION_SET_HOME,
            action -> this.setSharedHome(action, DEFAULT_HOME));
        view.button(48, this.item(Material.NAME_TAG, this.sharedText(chinese, "设置 home1", "Set home1"), List.of("/sethome home1")), ACTION_SET_HOME,
            action -> this.setSharedHome(action, "home1"));
        view.button(49, this.item(Material.NAME_TAG, this.sharedText(chinese, "刷新", "Refresh"), List.of(
            this.sharedText(chinese, "重新加载 Home GUI。", "Refresh this homes GUI.")
        )), ACTION_REFRESH_HOMES, HunterGuiActionContext::refresh);
        view.button(50, this.item(Material.NAME_TAG, this.sharedText(chinese, "设置 home2", "Set home2"), List.of("/sethome home2")), ACTION_SET_HOME,
            action -> this.setSharedHome(action, "home2"));
        view.button(51, this.item(Material.NAME_TAG, this.sharedText(chinese, "设置 home3", "Set home3"), List.of("/sethome home3")), ACTION_SET_HOME,
            action -> this.setSharedHome(action, "home3"));
        view.button(52, this.item(Material.NAME_TAG, this.sharedText(chinese, "设置 home4", "Set home4"), List.of("/sethome home4")), ACTION_SET_HOME,
            action -> this.setSharedHome(action, "home4"));
        view.button(53, this.item(Material.BARRIER, this.sharedText(chinese, "关闭", "Close"), List.of()), ACTION_CLOSE, action -> {
            this.playGuiSound(action.player(), Sound.UI_BUTTON_CLICK);
            action.close();
        });
        return view.build();
    }

    private void setSharedHome(final HunterGuiActionContext context, final String home) {
        this.setHome(context.player(), new String[] {home});
        context.refresh();
    }

    private void handleSharedHomeAction(final HunterGuiActionContext context, final String home) {
        final Player player = context.player();
        final boolean chinese = this.usesChinese(context);
        if (!this.homeExists(player, home)) {
            player.sendMessage(this.sharedText(chinese, "这个家已不存在，已刷新列表。", "That home no longer exists. The list was refreshed."));
            context.refresh();
            return;
        }

        final ClickType click = context.clickType();
        if (click.isShiftClick() && click.isRightClick()) {
            context.armConfirmation(
                this.sharedDeleteHomeConfirmationId(home),
                SHARED_DELETE_HOME_CONFIRMATION_TTL,
                confirmation -> this.homeExists(confirmation.player(), home)
            );
            context.navigate(this.sharedDeleteHomeRoute(home));
            return;
        }
        if (click.isRightClick()) {
            player.sendMessage(this.sharedText(
                chinese,
                "重命名 Home 请使用：/sethome <新名字> 后删除旧 Home。",
                "Rename homes by using /sethome <newName> and deleting the old home."
            ));
            this.playGuiSound(player, Sound.UI_BUTTON_CLICK);
            context.refresh();
            return;
        }
        if (!click.isLeftClick()) {
            player.sendMessage(this.sharedText(chinese, "请使用左键或右键操作 Home。", "Use left-click or right-click to manage this home."));
            context.refresh();
            return;
        }
        if (this.loadHome(player, home) == null) {
            player.sendMessage(this.sharedText(chinese, "这个家不可用，已刷新列表。", "That home is unavailable. The list was refreshed."));
            context.refresh();
            return;
        }
        this.home(player, new String[] {home});
        context.close();
    }

    private HunterGuiView renderSharedDeleteHomeGui(final HunterGuiRenderContext context) {
        final boolean chinese = this.usesChinese(context);
        final String home = this.sharedRouteHome(context.route());
        final boolean homeExists = home != null && this.homeExists(context.player(), home);
        final HunterGuiView.Builder view = HunterGuiView.builder(
            Component.text(this.sharedText(chinese, "HunterTPA · 删除 Home", "HunterTPA · Delete Home")),
            3
        );
        if (homeExists) {
            view.button(11, this.item(Material.LIME_WOOL, this.sharedText(chinese, "确认删除 ", "Confirm delete ") + home, List.of(
                this.sharedText(chinese, "这个操作不可撤销。", "This cannot be undone."),
                this.sharedText(chinese, "确认窗口将在 40 秒后失效。", "This confirmation expires after 40 seconds.")
            )), ACTION_CONFIRM_HOME_DELETE, action -> this.confirmSharedHomeDelete(action, home));
        } else {
            view.item(11, this.item(Material.GRAY_DYE, this.sharedText(chinese, "Home 不可用", "Home unavailable"), List.of(
                this.sharedText(chinese, "该 Home 已不存在或确认已失效。", "This home no longer exists or the confirmation is no longer valid.")
            )));
        }
        view.item(13, this.item(homeExists ? Material.RED_BED : Material.BARRIER, homeExists ? home : this.sharedText(chinese, "未知 Home", "Unknown home"), List.of(
            homeExists ? this.sharedText(chinese, "即将删除这个家。", "This home will be deleted.") : this.sharedText(chinese, "请返回家列表并重新选择。", "Return to the homes list and choose again.")
        )));
        view.button(15, this.item(Material.RED_WOOL, this.sharedText(chinese, "取消", "Cancel"), List.of(
            this.sharedText(chinese, "返回家列表。", "Return to homes.")
        )), ACTION_CANCEL_HOME_DELETE, action -> {
            final String routeHome = this.sharedRouteHome(action.route());
            if (routeHome != null) {
                action.clearConfirmation(this.sharedDeleteHomeConfirmationId(routeHome));
            }
            this.playGuiSound(action.player(), Sound.UI_BUTTON_CLICK);
            this.returnToSharedScreen(action, this.sharedHomesRoute());
        });
        return view.build();
    }

    private void confirmSharedHomeDelete(final HunterGuiActionContext context, final String home) {
        final Player player = context.player();
        final boolean chinese = this.usesChinese(context);
        final HunterGuiConfirmationResult confirmation = context.consumeConfirmation(this.sharedDeleteHomeConfirmationId(home));
        if (!confirmation.confirmed() || !this.homeExists(player, home)) {
            player.sendMessage(this.sharedText(
                chinese,
                "删除确认已过期，或 Home 状态已变化。",
                "The delete confirmation expired or the home changed."
            ));
            this.returnToSharedScreen(context, this.sharedHomesRoute());
            return;
        }
        this.deleteHome(player, new String[] {home});
        this.playGuiSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        this.returnToSharedScreen(context, this.sharedHomesRoute());
    }

    private HunterGuiView renderSharedRequestsGui(final HunterGuiRenderContext context) {
        final boolean chinese = this.usesChinese(context);
        final Player player = context.player();
        final TeleportRequest incomingRequest = this.incomingRequestFor(player.getUniqueId());
        final TeleportRequest outgoingRequest = this.outgoingRequestFor(player);
        final HunterGuiView.Builder view = HunterGuiView.builder(
            Component.text(this.sharedText(chinese, "HunterTPA · 请求", "HunterTPA · Requests")),
            3
        );

        view.item(4, this.item(Material.ENCHANTED_BOOK, this.sharedText(chinese, "传送请求", "Requests"), List.of(
            incomingRequest == null
                ? this.sharedText(chinese, "没有收到的请求。", "No incoming request.")
                : this.sharedRequestSummary(incomingRequest, true, chinese),
            outgoingRequest == null
                ? this.sharedText(chinese, "没有发出的请求。", "No outgoing request.")
                : this.sharedRequestSummary(outgoingRequest, false, chinese)
        )));
        if (incomingRequest == null) {
            view.item(11, this.item(Material.GRAY_DYE, this.sharedText(chinese, "接受", "Accept"), List.of(
                this.sharedText(chinese, "没有收到的请求。", "No incoming request.")
            )));
            view.item(13, this.item(Material.GRAY_DYE, this.sharedText(chinese, "收件箱", "Inbox"), List.of(
                this.sharedText(chinese, "没有需要处理的请求。", "Nothing to review.")
            )));
            view.item(15, this.item(Material.GRAY_DYE, this.sharedText(chinese, "拒绝", "Deny"), List.of(
                this.sharedText(chinese, "没有收到的请求。", "No incoming request.")
            )));
        } else {
            view.button(11, this.item(Material.LIME_WOOL, this.sharedText(chinese, "接受", "Accept"), List.of(
                this.sharedRequestSummary(incomingRequest, true, chinese)
            )), ACTION_ACCEPT_REQUEST, action -> {
                this.answerRequest(action.player(), new String[0], true);
                action.refresh();
            });
            view.item(13, this.item(Material.PLAYER_HEAD, this.sharedText(chinese, "请求者", "Requester"), List.of(
                this.sharedRequestSummary(incomingRequest, true, chinese)
            )));
            view.button(15, this.item(Material.RED_WOOL, this.sharedText(chinese, "拒绝", "Deny"), List.of(
                this.sharedRequestSummary(incomingRequest, true, chinese)
            )), ACTION_DENY_REQUEST, action -> {
                this.answerRequest(action.player(), new String[0], false);
                action.refresh();
            });
        }
        if (outgoingRequest == null) {
            view.item(21, this.item(Material.GRAY_DYE, this.sharedText(chinese, "发出请求", "Outgoing"), List.of(
                this.sharedText(chinese, "没有发出的请求。", "No outgoing request.")
            )));
        } else {
            view.button(21, this.item(Material.CLOCK, this.sharedText(chinese, "取消发出的请求", "Cancel outgoing"), List.of(
                this.sharedRequestSummary(outgoingRequest, false, chinese)
            )), ACTION_CANCEL_OUTGOING, action -> {
                this.cancelRequest(action.player());
                action.refresh();
            });
        }
        view.button(23, this.item(Material.COMPASS, this.sharedText(chinese, "刷新", "Refresh"), List.of(
            this.sharedText(chinese, "重新加载请求状态。", "Reload the request state.")
        )), ACTION_REFRESH_REQUESTS, HunterGuiActionContext::refresh);
        view.button(26, this.item(Material.ARROW, this.sharedText(chinese, "返回", "Back"), List.of("/tpgui")), ACTION_OPEN_TELEPORT,
            action -> this.returnToSharedScreen(action, this.sharedTeleportRoute()));
        return view.build();
    }

    private ItemStack sharedRequestsMenuItem(final Player player, final boolean chinese) {
        final TeleportRequest incomingRequest = this.incomingRequestFor(player.getUniqueId());
        return this.item(
            incomingRequest == null ? Material.BOOK : Material.ENCHANTED_BOOK,
            this.sharedText(chinese, "传送请求", "Requests"),
            List.of(incomingRequest == null
                ? this.sharedText(chinese, "打开传送请求收件箱。", "Open the request inbox.")
                : this.sharedRequestSummary(incomingRequest, true, chinese))
        );
    }

    private ItemStack sharedOutgoingRequestItem(final Player player, final boolean chinese) {
        final TeleportRequest outgoingRequest = this.outgoingRequestFor(player);
        return this.item(
            outgoingRequest == null ? Material.GRAY_DYE : Material.CLOCK,
            this.sharedText(chinese, "发出请求", "Outgoing"),
            List.of(outgoingRequest == null
                ? this.sharedText(chinese, "没有发出的请求。", "No outgoing request.")
                : this.sharedRequestSummary(outgoingRequest, false, chinese))
        );
    }

    private String sharedRequestSummary(final TeleportRequest request, final boolean incomingView, final boolean chinese) {
        final UUID otherId = incomingView ? request.requester() : request.target();
        final Player other = Bukkit.getPlayer(otherId);
        final String otherName = other == null
            ? this.sharedText(chinese, "未知玩家", "Unknown")
            : other.getName();
        final String direction;
        if (request.type() == TeleportType.TO_TARGET) {
            direction = incomingView
                ? this.sharedText(chinese, "传送到你", "to you")
                : this.sharedText(chinese, "传送到对方", "to target");
        } else {
            direction = incomingView
                ? this.sharedText(chinese, "让你传送到请求者", "to requester")
                : this.sharedText(chinese, "让对方传送到你", "to you");
        }
        final long seconds = Math.max(0L, (request.expiresAt() - System.currentTimeMillis() + 999L) / 1000L);
        return otherName + " · " + direction + " · " + seconds + (chinese ? "秒" : "s");
    }

    private ItemStack requestsMenuItem(final Player player) {
        final TeleportRequest incomingRequest = this.incomingRequestFor(player.getUniqueId());
        return item(
            incomingRequest == null ? Material.BOOK : Material.ENCHANTED_BOOK,
            this.text("Requests", "Requests"),
            List.of(incomingRequest == null ? this.text("Open the request inbox.", "Open the request inbox.") : this.requestSummary(incomingRequest, true))
        );
    }

    private ItemStack outgoingRequestItem(final Player player) {
        final TeleportRequest outgoingRequest = this.outgoingRequestFor(player);
        return item(
            outgoingRequest == null ? Material.GRAY_DYE : Material.CLOCK,
            this.text("Outgoing", "Outgoing"),
            List.of(outgoingRequest == null ? this.text("No outgoing request.", "No outgoing request.") : this.requestSummary(outgoingRequest, false))
        );
    }

    private @Nullable TeleportRequest outgoingRequestFor(final Player player) {
        final UUID targetId = this.outgoing.get(player.getUniqueId());
        if (targetId == null) {
            return null;
        }
        final TeleportRequest request = this.incomingRequestFor(targetId);
        if (request == null || !request.requester().equals(player.getUniqueId())) {
            this.outgoing.remove(player.getUniqueId(), targetId);
            return null;
        }
        return request;
    }

    private @Nullable TeleportRequest incomingRequestFor(final UUID targetId) {
        final TeleportRequest request = this.incoming.get(targetId);
        if (request == null || !request.target().equals(targetId) || request.isExpired()) {
            return null;
        }
        return request;
    }

    private String requestSummary(final TeleportRequest request, final boolean incomingView) {
        final UUID otherId = incomingView ? request.requester() : request.target();
        final Player other = Bukkit.getPlayer(otherId);
        final String direction = request.type() == TeleportType.TO_TARGET
            ? (incomingView ? "to you" : "to target")
            : (incomingView ? "to requester" : "to you");
        final long seconds = Math.max(0L, (request.expiresAt() - System.currentTimeMillis() + 999L) / 1000L);
        return (other == null ? "Unknown" : other.getName()) + " · " + direction + " · " + seconds + "s";
    }

    private boolean requestTeleport(final Player requester, final String[] args, final TeleportType type) {
        if (args.length != 1) {
            if (args.length == 0) {
                return this.openTeleportGui(requester);
            }
            requester.sendMessage(type == TeleportType.TO_TARGET ? "/tpa <player>" : "/tpahere <player>");
            return true;
        }
        return this.requestTeleport(requester, Bukkit.getPlayerExact(args[0]), type);
    }

    private boolean requestTeleport(final Player requester, final @Nullable Player target, final TeleportType type) {
        if (!this.checkCooldown(requester)) {
            return true;
        }
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
            this.outgoing.remove(oldIncoming.requester(), target.getUniqueId());
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
        final UUID targetId = target.getUniqueId();
        TeleportRequest request = this.incoming.get(targetId);
        if (args.length == 1) {
            final Player requester = Bukkit.getPlayerExact(args[0]);
            if (requester == null || request == null || !request.requester().equals(requester.getUniqueId())) {
                request = null;
            }
        }

        if (request == null) {
            target.sendMessage(this.text("你没有待处理的传送请求。", "You do not have a pending teleport request."));
            return true;
        }
        if (request.isExpired()) {
            this.clearTeleportRequest(request);
            target.sendMessage(this.text("你没有待处理的传送请求。", "You do not have a pending teleport request."));
            return true;
        }

        this.clearTeleportRequest(request);

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
        final UUID requesterId = requester.getUniqueId();
        final UUID targetId = this.outgoing.get(requesterId);
        if (targetId == null) {
            requester.sendMessage(this.text("你没有发出的传送请求。", "You do not have an outgoing teleport request."));
            return true;
        }
        final TeleportRequest request = this.incoming.get(targetId);
        if (request == null || !request.requester().equals(requesterId) || request.isExpired()) {
            this.outgoing.remove(requesterId, targetId);
            if (request != null && request.requester().equals(requesterId) && request.isExpired()) {
                this.incoming.remove(targetId, request);
            }
            requester.sendMessage(this.text("你没有发出的传送请求。", "You do not have an outgoing teleport request."));
            return true;
        }
        this.clearTeleportRequest(request);
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
            this.outgoing.remove(incomingRequest.requester(), uuid);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        final Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof TpaGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != topInventory) {
            return;
        }
        final GuiAction action = holder.actionAt(event.getRawSlot());
        if (action != null) {
            this.handleGuiAction(player, action, event.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TpaGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getPlayer() instanceof final Player player
            && event.getInventory().getHolder() instanceof final TpaGuiHolder holder
            && holder.screen() == GuiScreen.DELETE_HOME) {
            this.pendingHomeDeletes.remove(player.getUniqueId());
        }
    }

    private void handleGuiAction(final Player player, final GuiAction action, final ClickType click) {
        switch (action.type()) {
            case OPEN_HOMES -> this.openHomesGui(player);
            case OPEN_TELEPORT -> this.openTeleportGui(player);
            case OPEN_REQUESTS, REFRESH_REQUESTS -> this.openRequestsGui(player);
            case RUN_SPAWN -> player.performCommand("spawn");
            case RUN_BACK -> player.performCommand("back");
            case TOGGLE_REQUESTS -> {
                this.toggleRequests(player);
                this.openTeleportGui(player);
            }
            case RANDOM_TELEPORT -> this.randomTeleport(player);
            case CLOSE -> {
                this.playGuiSound(player, Sound.UI_BUTTON_CLICK);
                player.closeInventory();
            }
            case REQUEST_PLAYER -> this.requestTeleportFromGui(player, action.payload(), click);
            case CREATE_HOME -> {
                this.setHome(player, new String[] {this.nextHomeName(player)});
                this.openHomesGui(player);
            }
            case SET_HOME -> {
                if (action.payload() == null) {
                    return;
                }
                this.setHome(player, new String[] {action.payload()});
                this.openHomesGui(player);
            }
            case REFRESH_HOMES -> this.openHomesGui(player);
            case UNAVAILABLE_HOME -> player.sendMessage(this.text("这个家不可用。", "That home is not available."));
            case HOME -> this.handleHomeAction(player, action.payload(), click);
            case ACCEPT_REQUEST -> {
                this.answerRequest(player, new String[0], true);
                this.openRequestsGui(player);
            }
            case DENY_REQUEST -> {
                this.answerRequest(player, new String[0], false);
                this.openRequestsGui(player);
            }
            case CANCEL_OUTGOING -> {
                this.cancelRequest(player);
                this.openRequestsGui(player);
            }
            case CONFIRM_HOME_DELETE -> this.confirmHomeDelete(player);
            case CANCEL_HOME_DELETE -> {
                this.pendingHomeDeletes.remove(player.getUniqueId());
                this.playGuiSound(player, Sound.UI_BUTTON_CLICK);
                this.openHomesGui(player);
            }
        }
    }

    private void requestTeleportFromGui(final Player player, final @Nullable String targetIdText, final ClickType click) {
        if (targetIdText == null) {
            return;
        }
        final UUID targetId;
        try {
            targetId = UUID.fromString(targetIdText);
        } catch (final IllegalArgumentException ignored) {
            return;
        }
        this.requestTeleport(player, Bukkit.getPlayer(targetId), click.isRightClick() ? TeleportType.TARGET_TO_REQUESTER : TeleportType.TO_TARGET);
        player.closeInventory();
    }

    private void handleHomeAction(final Player player, final @Nullable String home, final ClickType click) {
        if (home == null) {
            return;
        }
        if (click.isShiftClick() && click.isRightClick()) {
            this.openDeleteHomeGui(player, home);
            return;
        }
        if (click.isRightClick()) {
            player.sendMessage(this.text("重命名 Home 请使用：/sethome <新名字> 后删除旧 Home。", "Rename homes by using /sethome <newName> and deleting the old home."));
            this.playGuiSound(player, Sound.UI_BUTTON_CLICK);
            return;
        }
        this.home(player, new String[] {home});
        player.closeInventory();
    }

    private void confirmHomeDelete(final Player player) {
        final PendingHomeDelete pending = this.pendingHomeDeletes.remove(player.getUniqueId());
        if (pending == null || pending.isExpired(System.currentTimeMillis()) || !this.homeExists(player, pending.home())) {
            player.sendMessage(this.text(
                "删除确认已过期，或 Home 状态已变化。",
                "The delete confirmation expired or the home changed."
            ));
            this.openHomesGui(player);
            return;
        }
        this.deleteHome(player, new String[] {pending.home()});
        this.playGuiSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        this.openHomesGui(player);
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
            final TeleportRequest request = this.incoming.get(oldTarget);
            if (request != null && request.requester().equals(requesterId)) {
                this.incoming.remove(oldTarget, request);
            }
        }
    }

    private void clearTeleportRequest(final TeleportRequest request) {
        this.incoming.remove(request.target(), request);
        this.outgoing.remove(request.requester(), request.target());
    }

    private void expireRequests() {
        final long now = System.currentTimeMillis();
        final List<TeleportRequest> expired = this.incoming.values().stream()
            .filter(request -> request.expiresAt() <= now)
            .toList();
        for (final TeleportRequest request : expired) {
            if (this.incoming.remove(request.target(), request)) {
                this.outgoing.remove(request.requester(), request.target());
                final Player requester = Bukkit.getPlayer(request.requester());
                if (requester != null) {
                    requester.sendMessage(this.text("你的传送请求已过期。", "Your teleport request expired."));
                }
            }
        }
        this.pendingHomeDeletes.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
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

    private boolean usesChinese(final HunterGuiRenderContext context) {
        return "zh".equalsIgnoreCase(context.presentation().locale().getLanguage());
    }

    private String sharedText(final boolean chinese, final String zhCn, final String enUs) {
        return chinese ? zhCn : enUs;
    }

    private @Nullable String sharedRouteHome(final HunterGuiRoute route) {
        if (route.arguments().size() != 1) {
            return null;
        }
        return route.argument(SHARED_HOME_ARGUMENT).filter(HunterTpaPlugin::isCanonicalHomeName).orElse(null);
    }

    private int sharedRoutePage(final HunterGuiRoute route) {
        final String page = route.argument(SHARED_PAGE_ARGUMENT).orElse("0");
        if (page.isEmpty() || !page.chars().allMatch(character -> character >= '0' && character <= '9')) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(page));
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private String sharedDeleteHomeConfirmationId(final String home) {
        if (!isCanonicalHomeName(home)) {
            throw new IllegalArgumentException("Home confirmation requires a canonical home name.");
        }
        return SHARED_DELETE_HOME_CONFIRMATION_PREFIX + home;
    }

    private boolean homeExists(final Player player, final String home) {
        return isCanonicalHomeName(home) && this.homes.contains(homePath(player, home));
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

    private static boolean isCanonicalHomeName(final @Nullable String input) {
        return input != null && !input.isBlank() && input.equals(homeName(input));
    }

    private static String homePath(final Player player, final String name) {
        return "homes." + player.getUniqueId() + "." + homeName(name);
    }

    private static String togglePath(final Player player) {
        return "settings." + player.getUniqueId() + ".requests-disabled";
    }

    private final class SharedTeleportGuiScreen implements HunterGuiScreen {
        @Override
        public @NotNull String id() {
            return SHARED_TELEPORT_SCREEN;
        }

        @Override
        public @NotNull HunterGuiView render(@NotNull final HunterGuiRenderContext context) {
            return HunterTpaPlugin.this.renderSharedTeleportGui(context);
        }
    }

    private final class SharedHomesGuiScreen implements HunterGuiScreen {
        @Override
        public @NotNull String id() {
            return SHARED_HOMES_SCREEN;
        }

        @Override
        public @NotNull HunterGuiView render(@NotNull final HunterGuiRenderContext context) {
            return HunterTpaPlugin.this.renderSharedHomesGui(context);
        }
    }

    private final class SharedRequestsGuiScreen implements HunterGuiScreen {
        @Override
        public @NotNull String id() {
            return SHARED_REQUESTS_SCREEN;
        }

        @Override
        public @NotNull HunterGuiView render(@NotNull final HunterGuiRenderContext context) {
            return HunterTpaPlugin.this.renderSharedRequestsGui(context);
        }
    }

    private final class SharedDeleteHomeGuiScreen implements HunterGuiScreen {
        @Override
        public @NotNull String id() {
            return SHARED_DELETE_HOME_SCREEN;
        }

        @Override
        public @NotNull HunterGuiView render(@NotNull final HunterGuiRenderContext context) {
            return HunterTpaPlugin.this.renderSharedDeleteHomeGui(context);
        }
    }

    private enum GuiScreen {
        TELEPORT,
        HOMES,
        DELETE_HOME,
        REQUESTS
    }

    private enum GuiActionType {
        OPEN_HOMES,
        OPEN_TELEPORT,
        OPEN_REQUESTS,
        RUN_SPAWN,
        RUN_BACK,
        TOGGLE_REQUESTS,
        RANDOM_TELEPORT,
        CLOSE,
        REQUEST_PLAYER,
        CREATE_HOME,
        SET_HOME,
        REFRESH_HOMES,
        UNAVAILABLE_HOME,
        HOME,
        ACCEPT_REQUEST,
        DENY_REQUEST,
        CANCEL_OUTGOING,
        REFRESH_REQUESTS,
        CONFIRM_HOME_DELETE,
        CANCEL_HOME_DELETE
    }

    private record GuiAction(GuiActionType type, @Nullable String payload) {
        private static GuiAction of(final GuiActionType type) {
            return new GuiAction(type, null);
        }

        private static GuiAction withPayload(final GuiActionType type, final String payload) {
            return new GuiAction(type, payload);
        }
    }

    private static final class TpaGuiHolder implements InventoryHolder {
        private final GuiScreen screen;
        private final Map<Integer, GuiAction> actions = new HashMap<>();

        private TpaGuiHolder(final GuiScreen screen) {
            this.screen = screen;
        }

        private GuiScreen screen() {
            return this.screen;
        }

        private void bind(final int slot, final GuiAction action) {
            this.actions.put(slot, action);
        }

        private @Nullable GuiAction actionAt(final int slot) {
            return this.actions.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private enum TeleportType {
        TO_TARGET,
        TARGET_TO_REQUESTER
    }

    private record PendingHomeDelete(String home, long expiresAt) {
        private boolean isExpired(final long now) {
            return now >= this.expiresAt;
        }
    }

    private record TeleportRequest(UUID requester, UUID target, TeleportType type, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() >= this.expiresAt;
        }
    }

    private record PendingTeleport(Location origin, Location destination, String successMessage, Runnable afterSuccess) {
    }
}
