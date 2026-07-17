package org.huntercore.plugins.auth;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.HunterLanguage;
import org.huntercore.api.gui.HunterGuiOpenResult;
import org.huntercore.api.gui.HunterGuiPresentation;
import org.huntercore.api.gui.HunterGuiRegistration;
import org.huntercore.api.gui.HunterGuiRegistrationOptions;
import org.huntercore.api.gui.HunterGuiRenderContext;
import org.huntercore.api.gui.HunterGuiRoute;
import org.huntercore.api.gui.HunterGuiScreen;
import org.huntercore.api.gui.HunterGuiTheme;
import org.huntercore.api.gui.HunterGuiView;
import org.huntercore.api.huntengine.HuntEngineService;
import org.huntercore.api.huntengine.HuntEngineServices;
import org.jetbrains.annotations.NotNull;

public final class HunterAuthPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final String GUI_TITLE = "HunterAuth Workbench";
    private static final String PASSWORD_GUI_TITLE = "HunterAuth Password";
    private static final String SHARED_AUTH_GUI_SCREEN = "hunter-auth:access";
    private static final String SHARED_PASSWORD_GUI_SCREEN = "hunter-auth:password";
    private static final String ACTION_SHARED_LOGIN = "hunter-auth:login-input";
    private static final String ACTION_SHARED_REGISTER = "hunter-auth:register-input";
    private static final String ACTION_SHARED_WEB = "hunter-auth:web-registration";
    private static final String ACTION_SHARED_HELP = "hunter-auth:help";
    private static final String ACTION_SHARED_CHANGE_PASSWORD = "hunter-auth:change-password-input";
    private static final String ACTION_SHARED_CLOSE = "hunter-auth:close";
    private static final Key HUNTERCORE_GUI_FONT = Key.key("huntercore", "gui");
    private static final String AUTH_PANEL_GLYPH = "\uE010";
    private static final int CMD_AUTH_DIGIT = 210000;
    private static final int CMD_AUTH_LOGIN = 210001;
    private static final int CMD_AUTH_REGISTER = 210002;
    private static final int CMD_AUTH_CHAT = 210003;
    private static final int CMD_AUTH_CONFIRM = 210004;
    private static final int CMD_AUTH_BACKSPACE = 210005;
    private static final int CMD_AUTH_CLEAR = 210006;
    private static final int CMD_AUTH_WEB = 210007;
    private static final int CMD_AUTH_HELP = 210008;
    private static final int CMD_AUTH_PASSWORD = 210009;
    private static final int CMD_AUTH_CHANGE = 210010;
    private static final int CMD_AUTH_CLOSE = 210011;
    private static final int CMD_AUTH_PACK = 210012;
    private static final int CMD_AUTH_SHIELD = 210013;
    private static final Set<String> ALLOWED_COMMANDS = Set.of("/login", "/l", "/register", "/reg");
    private static final Map<Integer, Integer> PIN_DIGIT_SLOTS = Map.of(
        10, 1, 11, 2, 12, 3,
        19, 4, 20, 5, 21, 6,
        28, 7, 29, 8, 30, 9,
        38, 0
    );

    private final SecureRandom random = new SecureRandom();
    // AsyncPlayerChatEvent may run away from the server thread. Keep every per-player state
    // collection safe to read there; GUI values themselves are still only mutated on the main
    // thread after the event has been cancelled.
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, GuiSession> guiSessions = new ConcurrentHashMap<>();
    private final Map<UUID, ResourcePackState> resourcePackStates = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> loginFailures = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockedUntil = new ConcurrentHashMap<>();
    private final Set<UUID> chatObservationBlocked = ConcurrentHashMap.newKeySet();
    private final Set<UUID> capturedChatInputs = ConcurrentHashMap.newKeySet();
    // The shared GUI runtime intentionally keeps its holder private. Retaining the exact
    // inventory identity lets HunterAuth preserve its unauthenticated inventory lock without
    // trusting titles, materials, or a broad per-player "GUI open" flag.
    private final Map<UUID, Inventory> sharedGuiInventories = new ConcurrentHashMap<>();
    private HunterGuiRegistration sharedGuiRegistration;
    private File usersFile;
    private YamlConfiguration users;
    private File sharedPreferencesFile;
    private YamlConfiguration sharedPreferences;
    private long sharedPreferencesModified;

    @Override
    public void onEnable() {
        this.getConfig().addDefault("enabled", false);
        this.getConfig().addDefault("online-mode-bypass", true);
        this.getConfig().addDefault("registration-required", true);
        this.getConfig().addDefault("web-registration-required", false);
        this.getConfig().addDefault("gui-enabled", true);
        this.getConfig().addDefault("open-gui-on-join", true);
        this.getConfig().addDefault("resource-pack-gui", true);
        this.getConfig().addDefault("resource-pack-prompt-on-join", true);
        this.getConfig().addDefault("minimum-password-length", 6);
        this.getConfig().addDefault("login-timeout-seconds", 90);
        this.getConfig().addDefault("max-login-attempts", 5);
        this.getConfig().addDefault("lockout-seconds", 60);
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();

        this.getDataFolder().mkdirs();
        this.usersFile = new File(this.getDataFolder(), "users.yml");
        this.reloadUsers();
        this.sharedPreferencesFile = Bukkit.getPluginsFolder().toPath().resolve("HunterCore").resolve("preferences.yml").toFile();
        this.reloadSharedPreferences();

        for (final String command : List.of("register", "login", "logout", "changepassword")) {
            final org.bukkit.command.PluginCommand pluginCommand = this.getCommand(command);
            if (pluginCommand != null) {
                pluginCommand.setExecutor(this);
            }
        }
        this.registerSharedGui();
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (this.sharedGuiRegistration != null) {
            this.sharedGuiRegistration.close();
            this.sharedGuiRegistration = null;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.closeManagedInventory(player);
        }
        this.authenticated.clear();
        this.pendingInputs.clear();
        this.capturedChatInputs.clear();
        this.chatObservationBlocked.clear();
        this.sharedGuiInventories.clear();
        this.clearGuiSessions();
        this.resourcePackStates.clear();
        this.loginFailures.clear();
        this.lockedUntil.clear();
    }

    private void registerSharedGui() {
        try {
            this.sharedGuiRegistration = HunterCoreProvider.get().gui().register(
                this,
                List.of(new SharedAuthGuiScreen(), new SharedPasswordGuiScreen()),
                new HunterGuiRegistrationOptions((player, route) -> this.sharedGuiPresentation(player))
            );
        } catch (final RuntimeException exception) {
            // A standalone HunterAuth install must retain its legacy GUI instead of failing to
            // enable merely because the optional HunterCore runtime is unavailable or miswired.
            this.sharedGuiRegistration = null;
            this.getLogger().warning("HunterAuth could not register the shared GUI; using the legacy GUI fallback.");
        }
    }

    private HunterGuiPresentation sharedGuiPresentation(final Player player) {
        final Locale locale = player.locale() == null ? Locale.ENGLISH : player.locale();
        return this.useEnhancedAuthGui(player)
            ? HunterGuiPresentation.resourcePack(locale)
            : HunterGuiPresentation.vanilla(locale);
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
            case "register" -> args.length == 0 && this.guiEnabled()
                ? this.openAuthGuiCommand(player)
                : this.register(player, args);
            case "login" -> args.length == 0 && this.guiEnabled()
                ? this.openAuthGuiCommand(player)
                : this.login(player, args);
            case "logout" -> this.logout(player);
            case "changepassword" -> args.length == 0 && this.guiEnabled()
                ? this.openPasswordGui(player)
                : this.changePassword(player, args);
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(final AsyncPlayerPreLoginEvent event) {
        if (this.shouldBypass() || !this.registrationRequired() || !this.webRegistrationRequired()) {
            return;
        }
        if (this.isRegistered(event.getUniqueId(), event.getName())) {
            return;
        }
        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            this.text(
                "请先在网页面板注册玩家名和密码后再进服，注册密码就是游戏登录密码：\n" + this.registrationUrl(),
                "Please register your player name and password on the web panel before joining; that password is your in-game login password:\n" + this.registrationUrl()
            )
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (this.shouldBypass()) {
            this.markAuthenticated(player);
            return;
        }
        if (!this.registrationRequired() && !this.isRegistered(player)) {
            this.markAuthenticated(player);
            player.sendMessage(this.text("此服务器未强制注册，已自动通过登录。", "Registration is not required on this server; you are logged in automatically."));
            return;
        }
        this.markUnauthenticated(player);
        final boolean resourcePackRequested = this.requestAuthResourcePack(player);
        this.sendLoginPrompt(player);
        if (this.guiEnabled() && this.setting("open-gui-on-join", true)) {
            this.getServer().getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && !this.isAuthenticated(player) && !this.guiSessions.containsKey(player.getUniqueId())
                    && !this.pendingInputs.containsKey(player.getUniqueId())) {
                    this.openAuthGui(player);
                }
            }, resourcePackRequested ? 80L : 10L);
        }
        this.scheduleLoginTimeout(player);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        this.authenticated.remove(playerId);
        this.pendingInputs.remove(playerId);
        this.capturedChatInputs.remove(playerId);
        this.chatObservationBlocked.remove(playerId);
        this.sharedGuiInventories.remove(playerId);
        this.clearGuiSession(playerId);
        this.resourcePackStates.remove(playerId);
        this.loginFailures.remove(playerId);
        this.lockedUntil.remove(playerId);
    }

    @EventHandler
    public void onResourcePackStatus(final PlayerResourcePackStatusEvent event) {
        final Player player = event.getPlayer();
        if (!this.resourcePackStates.containsKey(player.getUniqueId()) || this.isAuthenticated(player)) {
            return;
        }
        final String status = event.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED")) {
            this.resourcePackStates.put(player.getUniqueId(), ResourcePackState.READY);
            player.sendMessage(this.text("HunterCore 登录界面材质包已加载。", "HunterCore login UI resource pack loaded."));
            this.openAuthGuiSoon(player);
            return;
        }
        if (status.equals("ACCEPTED") || status.equals("DOWNLOADED")) {
            this.resourcePackStates.put(player.getUniqueId(), ResourcePackState.ACCEPTED);
            return;
        }
        if (status.equals("DECLINED") || status.equals("FAILED_DOWNLOAD") || status.equals("FAILED_RELOAD")
            || status.equals("INVALID_URL") || status.equals("DISCARDED")) {
            this.resourcePackStates.put(player.getUniqueId(), ResourcePackState.FALLBACK);
            player.sendMessage(this.text("未使用登录界面材质包，已切换为普通登录界面。", "Using the classic login GUI because the resource pack was not loaded."));
            this.openAuthGuiSoon(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(final PlayerMoveEvent event) {
        if (this.isAuthenticated(event.getPlayer()) || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(final AsyncPlayerChatEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        final PendingInput pending = this.pendingInputs.remove(playerId);
        if (pending != null) {
            // Keep a marker until the main-thread handler has consumed this message. This is
            // deliberately separate from pendingInputs because the latter is removed here.
            this.capturedChatInputs.add(playerId);
            event.setCancelled(true);
            final String message = event.getMessage().trim();
            this.getServer().getScheduler().runTask(this, () -> {
                try {
                    final Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        this.handlePendingInput(player, pending, message);
                    }
                } finally {
                    this.capturedChatInputs.remove(playerId);
                }
            });
            return;
        }
        if (this.chatObservationBlocked.contains(playerId)) {
            event.setCancelled(true);
            this.getServer().getScheduler().runTask(this, () -> {
                final Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && !this.isAuthenticated(player)) {
                    player.sendMessage(this.text("请先登录再聊天。", "Please log in before chatting."));
                    this.openAuthGuiSoon(player);
                }
            });
        }
    }

    /**
     * A lock-free cross-plugin guard for observers such as HunterTools. It intentionally only
     * reads concurrent state, so it is safe to call from AsyncPlayerChatEvent listeners.
     */
    public boolean shouldSuppressChatObservation(@NotNull final UUID playerId) {
        return this.chatObservationBlocked.contains(playerId)
            || this.pendingInputs.containsKey(playerId)
            || this.capturedChatInputs.contains(playerId);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        if (this.isAuthenticated(event.getPlayer())) {
            return;
        }
        final String lower = event.getMessage().toLowerCase(Locale.ROOT);
        final String root = lower.split(" ", 2)[0];
        if (!ALLOWED_COMMANDS.contains(root)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(this.text("请先登录再使用命令。", "Please log in before using commands."));
            this.openAuthGuiSoon(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        final Inventory topInventory = event.getView().getTopInventory();
        final boolean authGui = topInventory.getHolder() instanceof AuthGuiHolder;
        final boolean passwordGui = topInventory.getHolder() instanceof PasswordGuiHolder;
        final boolean sharedGui = this.isSharedAuthGui(player, topInventory);
        if (!this.isAuthenticated(player) && !authGui && !sharedGui) {
            event.setCancelled(true);
            player.sendMessage(this.text("请先登录再操作背包。", "Please log in before using inventories."));
            this.openAuthGuiSoon(player);
            return;
        }
        if (!authGui && !passwordGui) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != topInventory) {
            return;
        }
        if (passwordGui) {
            this.handlePasswordGuiClick(player, event.getRawSlot());
            return;
        }
        if (this.isAuthenticated(player)) {
            player.closeInventory();
            return;
        }
        final GuiSession session = this.guiSessions.computeIfAbsent(
            player.getUniqueId(),
            ignored -> new GuiSession(this.isRegistered(player) ? InputMode.LOGIN : InputMode.REGISTER)
        );
        final int slot = event.getRawSlot();
        final int digit = this.digitForSlot(slot);
        if (digit >= 0) {
            if (session.current().length() < 64) {
                session.append(digit);
            }
            this.renderAuthGui(player, topInventory, session);
            return;
        }
        switch (slot) {
            case 14 -> {
                session.mode(InputMode.LOGIN);
                session.reset();
                this.renderAuthGui(player, topInventory, session);
            }
            case 15 -> {
                session.mode(InputMode.REGISTER);
                session.reset();
                this.renderAuthGui(player, topInventory, session);
            }
            case 16 -> this.startGuiInput(player, session.mode());
            case 37 -> {
                session.backspace();
                this.renderAuthGui(player, topInventory, session);
            }
            case 39 -> {
                session.reset();
                this.renderAuthGui(player, topInventory, session);
            }
            case 32 -> this.submitGuiPassword(player, session, topInventory);
            case 41 -> player.sendMessage(this.text("网页注册地址：", "Web registration URL: ") + this.registrationUrl());
            case 43 -> this.sendLoginPrompt(player);
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        final Inventory topInventory = event.getView().getTopInventory();
        final boolean authGui = topInventory.getHolder() instanceof AuthGuiHolder;
        final boolean passwordGui = topInventory.getHolder() instanceof PasswordGuiHolder;
        final boolean sharedGui = this.isSharedAuthGui(player, topInventory);
        if (!this.isAuthenticated(player) && !authGui && !sharedGui) {
            event.setCancelled(true);
            player.sendMessage(this.text("请先登录再操作背包。", "Please log in before using inventories."));
            this.openAuthGuiSoon(player);
            return;
        }
        if (authGui || passwordGui || sharedGui) {
            // Match InventoryClickEvent: no drag may move an item into or through a managed UI.
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(final PlayerInteractEvent event) {
        if (!this.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(this.text("请先登录再交互。", "Please log in before interacting."));
            this.openAuthGuiSoon(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDropItem(final PlayerDropItemEvent event) {
        if (!this.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(this.text("请先登录再丢弃物品。", "Please log in before dropping items."));
            this.openAuthGuiSoon(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPickupItem(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof final Player player && !this.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamageByEntity(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof final Player player && !this.isAuthenticated(player)) {
            event.setCancelled(true);
            player.sendMessage(this.text("请先登录再攻击。", "Please log in before attacking."));
            this.openAuthGuiSoon(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(final EntityDamageEvent event) {
        if (event.getEntity() instanceof final Player player && !this.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeleport(final PlayerTeleportEvent event) {
        if (!this.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onSwapHands(final PlayerSwapHandItemsEvent event) {
        if (!this.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof final Player player)) {
            return;
        }
        final Inventory topInventory = event.getView().getTopInventory();
        if (this.sharedGuiInventories.remove(player.getUniqueId(), topInventory)) {
            this.clearGuiSession(player.getUniqueId());
            return;
        }
        final InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof AuthGuiHolder) && !(holder instanceof PasswordGuiHolder)) {
            return;
        }
        this.clearGuiSession(player.getUniqueId());
    }

    private boolean register(final Player player, final String[] args) {
        if (this.authDisabled()) {
            player.sendMessage(this.text("HunterAuth 当前未启用。", "HunterAuth is currently disabled."));
            return true;
        }
        if (this.onlineMode()) {
            return this.registerOnlineModeAccount(player, args);
        }
        if (!this.registrationRequired()) {
            player.sendMessage(this.text("此服务器未强制注册账号密码。", "This server does not require account registration."));
            this.markAuthenticated(player);
            return true;
        }
        if (this.isRegistered(player)) {
            player.sendMessage(this.text("你已经注册。请使用 /login 或 /changepassword。", "You are already registered. Use /login or /changepassword."));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("/register <password> <password>");
            return true;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage(this.text("两次密码不一致。", "Passwords do not match."));
            return true;
        }
        if (!this.passwordLongEnough(args[0], player)) {
            return true;
        }

        if (!this.registerPassword(player, args[0])) {
            player.sendMessage(this.text("该玩家名已经注册，请使用 /login。", "That player name is already registered; use /login."));
            return true;
        }
        this.markAuthenticated(player);
        this.clearGuiSession(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(this.text("注册成功，并已登录。", "Registered and logged in."));
        return true;
    }

    private boolean login(final Player player, final String[] args) {
        if (this.authDisabled()) {
            player.sendMessage(this.text("HunterAuth 当前未启用。", "HunterAuth is currently disabled."));
            return true;
        }
        if (this.onlineMode()) {
            return this.claimOnlineModeWebAccount(player, args);
        }
        if (!this.isRegistered(player)) {
            if (!this.registrationRequired()) {
                this.markAuthenticated(player);
                player.sendMessage(this.text("此服务器未强制注册，已自动登录。", "Registration is not required; you are logged in automatically."));
                return true;
            }
            player.sendMessage(this.text("你还没有注册。请使用 /register <password> <password>。", "You are not registered. Use /register <password> <password>."));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("/login <password>");
            return true;
        }
        final long remainingLock = this.lockRemainingMillis(player);
        if (remainingLock > 0L) {
            player.sendMessage(this.text("登录失败次数过多，请等待 ", "Too many failed login attempts. Wait ") + Math.max(1L, (remainingLock + 999L) / 1000L) + "s.");
            return true;
        }
        if (!this.verifyPassword(player, args[0])) {
            this.recordLoginFailure(player);
            player.sendMessage(this.text("密码错误。", "Incorrect password."));
            return true;
        }
        this.markAuthenticated(player);
        this.loginFailures.remove(player.getUniqueId());
        this.lockedUntil.remove(player.getUniqueId());
        this.pendingInputs.remove(player.getUniqueId());
        this.clearGuiSession(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(this.text("登录成功。", "Logged in."));
        return true;
    }

    private boolean registerOnlineModeAccount(final Player player, final String[] args) {
        if (args.length != 2) {
            player.sendMessage("/register <password> <password>");
            return true;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage(this.text("两次密码不一致。", "Passwords do not match."));
            return true;
        }
        if (!this.passwordLongEnough(args[0], player)) {
            return true;
        }

        final OnlineAccountResult result = this.registerOnlineModePassword(player, args[0]);
        if (result == OnlineAccountResult.CONFLICT) {
            player.sendMessage(this.text(
                "该名字已关联到其他已验证账号，请联系管理员。",
                "This name is already associated with another verified account; contact an administrator."
            ));
            return true;
        }
        if (result != OnlineAccountResult.SUCCESS) {
            player.sendMessage(this.text("无法保存网页登录密码，请稍后再试。", "Could not save the web login password; try again later."));
            return true;
        }
        this.markAuthenticated(player);
        player.sendMessage(this.text(
            "网页版账号已安全认领，网页和游戏现在使用同一密码。",
            "Your web account has been securely claimed; the web and game now use the same password."
        ));
        return true;
    }

    private boolean claimOnlineModeWebAccount(final Player player, final String[] args) {
        if (args.length != 1) {
            player.sendMessage(this.text(
                "使用 /login <网页密码> 领取已有网页注册；若不是你注册的，请使用 /register <新密码> <新密码> 认领。",
                "Use /login <web password> to claim your web registration. If you did not create it, use /register <new password> <new password> to claim it."
            ));
            return true;
        }
        final long remainingLock = this.lockRemainingMillis(player);
        if (remainingLock > 0L) {
            player.sendMessage(this.text("登录失败次数过多，请等待 ", "Too many failed login attempts. Wait ") + Math.max(1L, (remainingLock + 999L) / 1000L) + "s.");
            return true;
        }

        final OnlineAccountResult result = this.claimOnlineModePassword(player, args[0]);
        if (result == OnlineAccountResult.INVALID_PASSWORD) {
            this.recordLoginFailure(player);
            player.sendMessage(this.text("密码错误。", "Incorrect password."));
            return true;
        }
        if (result == OnlineAccountResult.MISSING) {
            player.sendMessage(this.text(
                "未找到可领取的网页注册。请使用 /register <password> <password> 创建或重置网页密码。",
                "No claimable web registration was found. Use /register <password> <password> to create or reset your web password."
            ));
            return true;
        }
        if (result != OnlineAccountResult.SUCCESS) {
            player.sendMessage(this.text("无法领取网页注册，请稍后再试。", "Could not claim the web registration; try again later."));
            return true;
        }
        this.markAuthenticated(player);
        this.loginFailures.remove(player.getUniqueId());
        this.lockedUntil.remove(player.getUniqueId());
        player.sendMessage(this.text("网页注册已领取，账号已绑定到你的正版 UUID。", "Web registration claimed and bound to your online-mode UUID."));
        return true;
    }

    private boolean logout(final Player player) {
        if (this.shouldBypass()) {
            player.sendMessage(this.text("HunterAuth 当前未启用或已被正版模式绕过。", "HunterAuth is disabled or bypassed while the server is in online mode."));
            return true;
        }
        this.markUnauthenticated(player);
        player.sendMessage(this.text("已退出登录。", "Logged out."));
        this.openAuthGuiSoon(player);
        return true;
    }

    private boolean changePassword(final Player player, final String[] args) {
        if (this.authDisabled()) {
            player.sendMessage(this.text("HunterAuth 当前未启用。", "HunterAuth is currently disabled."));
            return true;
        }
        if (this.onlineMode()) {
            return this.changeOnlineModePassword(player, args);
        }
        if (!this.isRegistered(player)) {
            player.sendMessage(this.text("你还没有注册。", "You are not registered."));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("/changepassword <old> <new>");
            return true;
        }
        if (!this.verifyPassword(player, args[0])) {
            player.sendMessage(this.text("旧密码错误。", "Incorrect old password."));
            return true;
        }
        if (!this.passwordLongEnough(args[1], player)) {
            return true;
        }
        if (!this.changePassword(player, args[1])) {
            player.sendMessage(this.text("无法找到已验证的账号记录，请重新登录。", "The verified account record could not be found; log in again."));
            return true;
        }
        this.markAuthenticated(player);
        player.sendMessage(this.text("密码已修改。", "Password changed."));
        return true;
    }

    private boolean changeOnlineModePassword(final Player player, final String[] args) {
        if (!this.isAuthenticated(player)) {
            player.sendMessage(this.text(
                "请先使用 /login 领取网页注册，或使用 /register 创建网页密码后再修改密码。",
                "Claim the web registration with /login, or create a web password with /register before changing it."
            ));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("/changepassword <old> <new>");
            return true;
        }
        if (!this.passwordLongEnough(args[1], player)) {
            return true;
        }

        final OnlineAccountResult result = this.changeCurrentOnlineModePassword(player, args[0], args[1]);
        if (result == OnlineAccountResult.INVALID_PASSWORD) {
            player.sendMessage(this.text("旧密码错误。", "Incorrect old password."));
            return true;
        }
        if (result == OnlineAccountResult.MISSING) {
            this.markUnauthenticated(player);
            player.sendMessage(this.text(
                "未找到已认领的账号记录，请重新使用 /login 或 /register。",
                "No claimed account record was found. Use /login or /register again."
            ));
            return true;
        }
        if (result != OnlineAccountResult.SUCCESS) {
            player.sendMessage(this.text("无法保存新密码，请稍后再试。", "Could not save the new password; try again later."));
            return true;
        }
        this.markAuthenticated(player);
        player.sendMessage(this.text("密码已修改。", "Password changed."));
        return true;
    }

    private boolean openPasswordGui(final Player player) {
        if (this.authDisabled()) {
            player.sendMessage(this.text("HunterAuth is currently disabled.", "HunterAuth is currently disabled."));
            return true;
        }
        if (this.onlineMode()) {
            if (!this.isAuthenticated(player)) {
                this.openAuthGui(player);
                player.sendMessage(this.text(
                    "请先使用 /login 或 /register 认领当前正版 UUID。",
                    "Use /login or /register to claim your current online-mode UUID first."
                ));
                return true;
            }
            return this.openPasswordGuiInventory(player);
        }
        if (!this.isRegistered(player)) {
            player.sendMessage(this.text("You are not registered.", "You are not registered."));
            return true;
        }
        if (!this.isAuthenticated(player)) {
            this.openAuthGui(player);
            player.sendMessage(this.text("Log in first, then change your password.", "Log in first, then change your password."));
            return true;
        }
        return this.openPasswordGuiInventory(player);
    }

    private boolean openPasswordGuiInventory(final Player player) {
        if (this.openSharedGui(player, HunterGuiRoute.of(SHARED_PASSWORD_GUI_SCREEN))) {
            return true;
        }
        final boolean enhanced = this.useEnhancedAuthGui(player);
        final Inventory inventory = Bukkit.createInventory(new PasswordGuiHolder(enhanced), 27, enhanced ? ComponentTitle.HUNTER_AUTH_ENHANCED : ComponentTitle.HUNTER_AUTH_PASSWORD);
        this.renderPasswordGui(player, inventory);
        player.openInventory(inventory);
        return true;
    }

    private boolean openAuthGuiCommand(final Player player) {
        if (this.shouldBypass()) {
            player.sendMessage(this.text("HunterAuth is disabled or bypassed while the server is in online mode.", "HunterAuth is disabled or bypassed while the server is in online mode."));
            return true;
        }
        if (this.isAuthenticated(player)) {
            player.sendMessage(this.text("你已经登录。", "You are already logged in."));
            return true;
        }
        this.openAuthGui(player);
        return true;
    }

    private void openAuthGui(final Player player) {
        if (!this.guiEnabled() || this.isAuthenticated(player)) {
            return;
        }
        if (this.openSharedGui(player, HunterGuiRoute.of(SHARED_AUTH_GUI_SCREEN))) {
            return;
        }
        final GuiSession session = new GuiSession(this.isRegistered(player) ? InputMode.LOGIN : InputMode.REGISTER);
        this.guiSessions.put(player.getUniqueId(), session);
        final boolean enhanced = this.useEnhancedAuthGui(player);
        final Inventory inventory = Bukkit.createInventory(new AuthGuiHolder(enhanced), 54, enhanced ? ComponentTitle.HUNTER_AUTH_ENHANCED : ComponentTitle.HUNTER_AUTH);
        this.renderAuthGui(player, inventory, session);
        player.openInventory(inventory);
    }

    private void openAuthGuiSoon(final Player player) {
        if (!this.guiEnabled()) {
            return;
        }
        this.getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !this.isAuthenticated(player) && !this.pendingInputs.containsKey(player.getUniqueId())
                && !this.guiSessions.containsKey(player.getUniqueId())
                && !this.sharedGuiInventories.containsKey(player.getUniqueId())) {
                this.openAuthGui(player);
            }
        }, 2L);
    }

    /**
     * Opens a shared view when the HunterCore runtime is active and pins the exact inventory
     * instance for HunterAuth's unauthenticated-inventory guard. A failed shared open never
     * clears an existing binding, so the caller can safely use the legacy fallback.
     */
    private boolean openSharedGui(final Player player, final HunterGuiRoute route) {
        final HunterGuiRegistration registration = this.sharedGuiRegistration;
        if (registration == null || !registration.active()) {
            return false;
        }
        final HunterGuiOpenResult result = registration.open(player, route);
        if (!result.opened()) {
            this.getLogger().fine("HunterAuth shared GUI unavailable for " + player.getName() + ": " + result.name());
            return false;
        }
        this.sharedGuiInventories.put(player.getUniqueId(), player.getOpenInventory().getTopInventory());
        return true;
    }

    private boolean isSharedAuthGui(final Player player, final Inventory topInventory) {
        final HunterGuiRegistration registration = this.sharedGuiRegistration;
        return registration != null
            && registration.active()
            && this.sharedGuiInventories.get(player.getUniqueId()) == topInventory;
    }

    /**
     * The shared access screen deliberately starts the existing secure chat capture flow instead
     * of putting a credential into route arguments, item metadata, or a rendered inventory.
     */
    private HunterGuiView renderSharedAuthGui(final HunterGuiRenderContext context) {
        final boolean chinese = this.usesChinese(context);
        final boolean enhanced = context.presentation().theme() == HunterGuiTheme.RESOURCE_PACK;
        final Player player = context.player();
        final HunterGuiView.Builder view = HunterGuiView.builder(
            enhanced ? ComponentTitle.HUNTER_AUTH_ENHANCED : Component.text(this.sharedText(chinese, "HunterAuth 登录", "HunterAuth Access")),
            3
        );
        final boolean registered = this.isRegistered(player);

        view.item(4, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_SHIELD,
            Material.NETHER_STAR,
            "&b",
            this.sharedText(chinese, "HunterAuth", "HunterAuth"),
            List.of(
                registered
                    ? this.sharedText(chinese, "账号状态：已注册", "Account: registered")
                    : this.sharedText(chinese, "账号状态：未注册", "Account: not registered"),
                this.sharedText(chinese, "选择一个安全输入流程；不会在界面中显示密码。", "Choose a secure input flow; credentials are never rendered in this panel."),
                enhanced
                    ? this.sharedText(chinese, "资源包增强界面已启用。", "Resource-pack enhanced UI is enabled.")
                    : this.sharedText(chinese, "普通客户端界面可直接使用。", "This vanilla-safe UI works without a resource pack.")
            )
        ));
        view.button(10, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_LOGIN,
            Material.LIME_DYE,
            "&a",
            this.sharedText(chinese, "登录", "Log in"),
            List.of(
                this.sharedText(chinese, "使用已有密码进入服务器。", "Use your existing password to enter the server."),
                this.sharedText(chinese, "点击后在聊天栏安全输入，不会广播。", "Click to enter it securely in chat; it will not be broadcast.")
            )
        ), ACTION_SHARED_LOGIN, action -> this.startSharedInput(action.player(), InputMode.LOGIN, action));
        view.button(12, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_REGISTER,
            Material.WRITABLE_BOOK,
            "&b",
            this.sharedText(chinese, "注册", "Register"),
            List.of(
                this.sharedText(chinese, "创建或认领当前玩家名的登录密码。", "Create or claim the login credential for this player name."),
                this.sharedText(chinese, "点击后在聊天栏安全输入两次，不会广播。", "Click to enter it twice securely in chat; it will not be broadcast.")
            )
        ), ACTION_SHARED_REGISTER, action -> this.startSharedInput(action.player(), InputMode.REGISTER, action));
        view.button(14, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_WEB,
            Material.MAP,
            "&b",
            this.sharedText(chinese, "网页注册", "Web registration"),
            List.of(this.sharedText(chinese, "在网页面板创建的密码可用于游戏登录。", "A password created on the web panel can also log in to the game."))
        ), ACTION_SHARED_WEB, action -> action.player().sendMessage(
            this.sharedText(this.usesChinese(action), "网页注册地址：", "Web registration URL: ") + this.registrationUrl()
        ));
        view.button(16, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_HELP,
            Material.PAPER,
            "&f",
            this.sharedText(chinese, "登录帮助", "Login help"),
            List.of(this.sharedText(chinese, "显示当前账号的下一步操作。", "Show the next step for this account."))
        ), ACTION_SHARED_HELP, action -> this.sendLoginPrompt(action.player()));
        view.button(22, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_CLOSE,
            Material.BARRIER,
            "&c",
            this.sharedText(chinese, "关闭", "Close"),
            List.of(this.sharedText(chinese, "关闭此面板。", "Close this panel."))
        ), ACTION_SHARED_CLOSE, action -> action.close());
        return view.build();
    }

    private HunterGuiView renderSharedPasswordGui(final HunterGuiRenderContext context) {
        final boolean chinese = this.usesChinese(context);
        final boolean enhanced = context.presentation().theme() == HunterGuiTheme.RESOURCE_PACK;
        final Player player = context.player();
        final HunterGuiView.Builder view = HunterGuiView.builder(
            enhanced ? ComponentTitle.HUNTER_AUTH_ENHANCED : Component.text(this.sharedText(chinese, "HunterAuth 修改密码", "HunterAuth Password")),
            3
        );

        view.item(4, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_CHANGE,
            Material.TRIPWIRE_HOOK,
            "&b",
            this.sharedText(chinese, "修改密码", "Change password"),
            List.of(
                this.sharedText(chinese, "当前账号：", "Current account: ") + player.getName(),
                this.sharedText(chinese, "密码只会在受保护的输入流程中处理。", "Credentials are handled only by the protected input flow.")
            )
        ));
        view.button(11, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_CHAT,
            Material.OAK_SIGN,
            "&b",
            this.sharedText(chinese, "安全输入", "Secure input"),
            List.of(
                this.sharedText(chinese, "输入旧密码和新密码；不会广播。", "Enter the old and new credentials; they will not be broadcast."),
                this.sharedText(chinese, "输入 cancel 可取消。", "Type cancel to abort.")
            )
        ), ACTION_SHARED_CHANGE_PASSWORD, action -> {
            if (!this.isAuthenticated(action.player())) {
                action.player().sendMessage(this.sharedText(this.usesChinese(action), "请先登录。", "Log in before changing your password."));
                action.close();
                this.openAuthGuiSoon(action.player());
                return;
            }
            this.startGuiInput(action.player(), InputMode.CHANGE_PASSWORD);
        });
        view.button(15, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_HELP,
            Material.PAPER,
            "&f",
            this.sharedText(chinese, "命令帮助", "Command help"),
            List.of(this.sharedText(chinese, "显示修改密码命令用法。", "Show the password-change command usage."))
        ), ACTION_SHARED_HELP, action -> action.player().sendMessage("/changepassword <old> <new>"));
        view.button(22, this.sharedGuiItem(
            enhanced,
            CMD_AUTH_CLOSE,
            Material.BARRIER,
            "&c",
            this.sharedText(chinese, "关闭", "Close"),
            List.of(this.sharedText(chinese, "关闭此面板。", "Close this panel."))
        ), ACTION_SHARED_CLOSE, action -> action.close());
        return view.build();
    }

    private void startSharedInput(final Player player, final InputMode mode, final HunterGuiRenderContext context) {
        if (this.isAuthenticated(player)) {
            player.sendMessage(this.sharedText(this.usesChinese(context), "你已经登录。", "You are already logged in."));
            if (context instanceof final org.huntercore.api.gui.HunterGuiActionContext action) {
                action.close();
            }
            return;
        }
        this.startGuiInput(player, mode);
    }

    private boolean usesChinese(final HunterGuiRenderContext context) {
        return "zh".equalsIgnoreCase(context.presentation().locale().getLanguage());
    }

    private String sharedText(final boolean chinese, final String zhCn, final String enUs) {
        return chinese ? zhCn : enUs;
    }

    private ItemStack sharedGuiItem(
        final boolean enhanced,
        final int customModelData,
        final Material vanillaMaterial,
        final String enhancedColor,
        final String name,
        final List<String> lore
    ) {
        return enhanced
            ? this.enhancedButton(customModelData, enhancedColor + name, lore)
            : item(vanillaMaterial, name, lore);
    }

    private void renderAuthGui(final Player player, final Inventory inventory, final GuiSession session) {
        if (this.enhancedInventory(inventory)) {
            this.renderEnhancedAuthGui(player, inventory, session);
            return;
        }
        inventory.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        for (final Map.Entry<Integer, Integer> entry : PIN_DIGIT_SLOTS.entrySet()) {
            inventory.setItem(entry.getKey(), this.digitItem(entry.getValue()));
        }
        inventory.setItem(4, item(
            Material.NETHER_STAR,
            this.text("HunterAuth 登录中心", "HunterAuth Login Center"),
            List.of(
                this.isRegistered(player) ? this.text("账号状态：已注册", "Account: registered") : this.text("账号状态：未注册", "Account: not registered"),
                this.text("点击数字输入 PIN，也可以用聊天输入普通密码。", "Click digits for a PIN, or use chat input for a normal password.")
            )
        ));
        inventory.setItem(14, item(
            session.mode() == InputMode.LOGIN ? Material.LIME_DYE : Material.EMERALD,
            this.text("登录模式", "Login mode"),
            List.of(this.text("点击切换到登录。", "Click to switch to login."))
        ));
        inventory.setItem(15, item(
            session.mode() == InputMode.REGISTER ? Material.LIME_DYE : Material.WRITABLE_BOOK,
            this.text("注册模式", "Register mode"),
            List.of(this.text("点击切换到注册。", "Click to switch to register."))
        ));
        inventory.setItem(16, item(
            Material.OAK_SIGN,
            this.text("聊天栏安全输入", "Secure chat input"),
            List.of(
                session.mode() == InputMode.LOGIN ? "/login <password>" : "/register <password> <password>",
                this.text("点击后关闭 GUI，在聊天栏输入密码，不会广播。", "Click to close the GUI and type your password in chat; it will not be broadcast.")
            )
        ));
        inventory.setItem(23, item(
            Material.CRAFTING_TABLE,
            session.mode() == InputMode.LOGIN ? this.text("输入登录 PIN", "Enter login PIN") : this.text("输入注册 PIN", "Enter register PIN"),
            List.of(
                this.text("已输入：", "Entered: ") + "*".repeat(session.current().length()),
                session.firstPassword() == null ? this.text("点击左侧数字键输入。", "Click the left keypad digits.") : this.text("请再次输入同样的 PIN。", "Enter the same PIN again."),
                this.text("最短长度：", "Minimum length: ") + this.intSetting("minimum-password-length", 4)
            )
        ));
        inventory.setItem(32, item(Material.EMERALD_BLOCK, this.text("确认", "Confirm"), List.of(this.text("提交当前输入。", "Submit the current input."))));
        inventory.setItem(37, item(Material.ARROW, this.text("退格", "Backspace"), List.of(this.text("删除最后一位。", "Delete the last digit."))));
        inventory.setItem(39, item(Material.BARRIER, this.text("清空", "Clear"), List.of(this.text("清空当前输入。", "Clear current input."))));
        inventory.setItem(41, item(Material.MAP, this.text("网页注册", "Web registration"), List.of(this.registrationUrl())));
        inventory.setItem(43, item(Material.PAPER, this.text("命令帮助", "Command help"), List.of("/login <password>", "/register <password> <password>", "/changepassword <old> <new>")));
    }

    private void renderEnhancedAuthGui(final Player player, final Inventory inventory, final GuiSession session) {
        inventory.clear();
        for (final Map.Entry<Integer, Integer> entry : PIN_DIGIT_SLOTS.entrySet()) {
            inventory.setItem(entry.getKey(), this.digitItem(entry.getValue(), true));
        }
        final boolean registered = this.isRegistered(player);
        final boolean loginMode = session.mode() == InputMode.LOGIN;
        final String entered = "*".repeat(session.current().length());
        inventory.setItem(4, this.enhancedButton(CMD_AUTH_SHIELD, "&bHunterAuth", List.of(
            registered ? "&7账号状态: &a已注册" : "&7账号状态: &e未注册",
            this.resourcePackReady(player) ? "&7界面: &b资源包增强" : "&7界面: &f普通模式",
            "&8拒绝材质包也可以继续登录"
        )));
        inventory.setItem(14, this.enhancedButton(CMD_AUTH_LOGIN, loginMode ? "&a登录模式" : "&7登录模式", List.of(
            "&7使用已有密码进入服务器",
            loginMode ? "&a当前已选择" : "&e点击切换"
        )));
        inventory.setItem(15, this.enhancedButton(CMD_AUTH_REGISTER, !loginMode ? "&a注册模式" : "&7注册模式", List.of(
            "&7创建服务器登录密码",
            !loginMode ? "&a当前已选择" : "&e点击切换"
        )));
        inventory.setItem(16, this.enhancedButton(CMD_AUTH_CHAT, "&b安全聊天输入", List.of(
            loginMode ? "&f/login <password>" : "&f/register <password> <password>",
            "&7点击后关闭 GUI，在聊天栏输入",
            "&8不会广播给其他玩家"
        )));
        inventory.setItem(23, this.enhancedButton(CMD_AUTH_PASSWORD, loginMode ? "&b输入登录密码" : "&b输入注册密码", List.of(
            "&7已输入: &f" + (entered.isBlank() ? "-" : entered),
            session.firstPassword() == null ? "&7点击左侧键盘输入" : "&e请再次输入相同密码",
            "&7最小长度: &f" + this.intSetting("minimum-password-length", 6)
        )));
        inventory.setItem(32, this.enhancedButton(CMD_AUTH_CONFIRM, "&a确认", List.of("&7提交当前输入")));
        inventory.setItem(37, this.enhancedButton(CMD_AUTH_BACKSPACE, "&e退格", List.of("&7删除最后一位")));
        inventory.setItem(39, this.enhancedButton(CMD_AUTH_CLEAR, "&c清空", List.of("&7清空当前输入")));
        inventory.setItem(41, this.enhancedButton(CMD_AUTH_WEB, "&b网页登录", List.of(this.registrationUrl())));
        inventory.setItem(43, this.enhancedButton(CMD_AUTH_HELP, "&f命令帮助", List.of(
            "&f/login <password>",
            "&f/register <password> <password>",
            "&f/changepassword <old> <new>"
        )));
        inventory.setItem(49, this.enhancedButton(CMD_AUTH_PACK, "&b界面资源包", List.of(
            "&7已集成 HunterCore 默认资源包",
            "&7如果拒绝安装，会自动使用普通界面"
        )));
    }

    private void renderPasswordGui(final Player player, final Inventory inventory) {
        if (this.enhancedInventory(inventory)) {
            this.renderEnhancedPasswordGui(player, inventory);
            return;
        }
        inventory.clear();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()));
        }
        inventory.setItem(4, item(Material.TRIPWIRE_HOOK, this.text("Change password", "Change password"), List.of(
            this.text("Logged in as: ", "Logged in as: ") + player.getName(),
            this.text("Open the secure input flow below to rotate the account password.", "Open the secure input flow below to rotate the account password.")
        )));
        inventory.setItem(11, item(Material.OAK_SIGN, this.text("Secure chat input", "Secure chat input"), List.of(
            "/changepassword <old> <new>",
            this.text("Type oldPassword newPassword in chat. Type cancel to abort.", "Type oldPassword newPassword in chat. Type cancel to abort.")
        )));
        inventory.setItem(15, item(Material.PAPER, this.text("Command help", "Command help"), List.of("/changepassword <old> <new>")));
        inventory.setItem(22, item(Material.BARRIER, this.text("Close", "Close"), List.of(this.text("Close this panel.", "Close this panel."))));
    }

    private void renderEnhancedPasswordGui(final Player player, final Inventory inventory) {
        inventory.clear();
        inventory.setItem(4, this.enhancedButton(CMD_AUTH_CHANGE, "&b修改密码", List.of(
            "&7当前账号: &f" + player.getName(),
            "&7使用安全输入流程更换密码"
        )));
        inventory.setItem(11, this.enhancedButton(CMD_AUTH_CHAT, "&b安全聊天输入", List.of(
            "&f/changepassword <old> <new>",
            "&7点击后在聊天栏输入旧密码和新密码",
            "&8输入 cancel 可取消"
        )));
        inventory.setItem(15, this.enhancedButton(CMD_AUTH_HELP, "&f命令帮助", List.of("&f/changepassword <old> <new>")));
        inventory.setItem(22, this.enhancedButton(CMD_AUTH_CLOSE, "&c关闭", List.of("&7关闭此面板")));
    }

    private void handlePasswordGuiClick(final Player player, final int slot) {
        switch (slot) {
            case 11 -> this.startGuiInput(player, InputMode.CHANGE_PASSWORD);
            case 22 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void submitGuiPassword(final Player player, final GuiSession session, final Inventory inventory) {
        final String password = session.current();
        if (password.isBlank()) {
            player.sendMessage(this.text("请先点击玻璃输入密码。", "Click the glass panes to enter a password first."));
            return;
        }
        if (session.mode() == InputMode.LOGIN) {
            this.login(player, new String[] {password});
            return;
        }
        if (session.firstPassword() == null) {
            session.firstPassword(password);
            session.current("");
            player.sendMessage(this.text("请再次输入同样的密码并确认。", "Enter the same password again and confirm."));
            this.renderAuthGui(player, inventory, session);
            return;
        }
        this.register(player, new String[] {session.firstPassword(), password});
    }

    private int digitForSlot(final int slot) {
        return PIN_DIGIT_SLOTS.getOrDefault(slot, -1);
    }

    private void startGuiInput(final Player player, final InputMode mode) {
        player.closeInventory();
        this.pendingInputs.put(player.getUniqueId(), new PendingInput(mode));
        if (mode == InputMode.CHANGE_PASSWORD) {
            player.sendMessage(this.text("Type oldPassword newPassword in chat. Type cancel to abort.", "Type oldPassword newPassword in chat. Type cancel to abort."));
            return;
        }
        player.sendMessage(mode == InputMode.LOGIN
            ? this.text("请直接在聊天栏输入密码，不会广播。也可以使用 /login <password>。", "Type your password in chat; it will not be broadcast. You can also use /login <password>.")
            : this.text("请在聊天栏输入两次密码，用空格分开，不会广播。也可以使用 /register <password> <password>。", "Type password twice separated by a space; it will not be broadcast. You can also use /register <password> <password>."));
    }

    private void handlePendingInput(final Player player, final PendingInput pending, final String message) {
        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(this.text("Cancelled the current input flow.", "Cancelled the current input flow."));
            if (pending.mode() == InputMode.CHANGE_PASSWORD) {
                this.openPasswordGui(player);
            } else {
                this.openAuthGuiSoon(player);
            }
            return;
        }
        if (pending.mode() == InputMode.LOGIN) {
            this.login(player, new String[] {message});
            return;
        }
        if (pending.mode() == InputMode.CHANGE_PASSWORD) {
            final String[] parts = message.split("\\s+", 2);
            if (parts.length != 2) {
                player.sendMessage(this.text("Password change needs oldPassword newPassword separated by a space.", "Password change needs oldPassword newPassword separated by a space."));
                this.pendingInputs.put(player.getUniqueId(), pending);
                return;
            }
            this.changePassword(player, parts);
            return;
        }
        final String[] parts = message.split("\\s+", 2);
        if (parts.length != 2) {
            player.sendMessage(this.text("注册需要输入两次密码，用空格分开。", "Registration needs the password twice, separated by a space."));
            this.pendingInputs.put(player.getUniqueId(), pending);
            return;
        }
        this.register(player, parts);
    }

    private void sendLoginPrompt(final Player player) {
        if (this.isRegistered(player)) {
            player.sendMessage(this.text("请使用 /login <password> 登录。", "Please log in with /login <password>."));
        } else if (this.registrationRequired() && this.webRegistrationRequired()) {
            player.sendMessage(this.text("请先在网页面板注册账号后再登录，网页密码就是游戏密码：" + this.registrationUrl(), "Please register on the web panel before logging in; the web password is your game password: " + this.registrationUrl()));
        } else if (this.registrationRequired()) {
            player.sendMessage(this.text("请使用 /register <password> <password> 注册。", "Please register with /register <password> <password>."));
        } else {
            player.sendMessage(this.text("此服务器未强制注册账号密码。", "This server does not require account registration."));
        }
    }

    private void scheduleLoginTimeout(final Player player) {
        final int timeoutSeconds = this.intSetting("login-timeout-seconds", 90);
        if (timeoutSeconds <= 0) {
            return;
        }
        this.getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !this.isAuthenticated(player)) {
                player.kick(Component.text(this.text("登录超时，请重新进入服务器。", "Login timed out. Please rejoin the server.")));
            }
        }, Math.max(20L, timeoutSeconds * 20L));
    }

    private long lockRemainingMillis(final Player player) {
        final long until = this.lockedUntil.getOrDefault(player.getUniqueId(), 0L);
        final long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            this.lockedUntil.remove(player.getUniqueId());
            return 0L;
        }
        return remaining;
    }

    private void recordLoginFailure(final Player player) {
        final int attempts = this.loginFailures.merge(player.getUniqueId(), 1, Integer::sum);
        final int maxAttempts = Math.max(1, this.intSetting("max-login-attempts", 5));
        if (attempts >= maxAttempts) {
            final int lockoutSeconds = Math.max(1, this.intSetting("lockout-seconds", 60));
            this.lockedUntil.put(player.getUniqueId(), System.currentTimeMillis() + lockoutSeconds * 1000L);
            this.loginFailures.put(player.getUniqueId(), 0);
            player.sendMessage(this.text("登录失败次数过多，已临时锁定。", "Too many failed login attempts; login is temporarily locked."));
        }
    }

    private boolean shouldBypass() {
        return this.authDisabled() || this.onlineModeBypass();
    }

    private boolean authDisabled() {
        return !this.setting("enabled", true);
    }

    private boolean onlineMode() {
        return Bukkit.getOnlineMode();
    }

    private boolean onlineModeBypass() {
        return this.onlineMode() && this.setting("online-mode-bypass", true);
    }

    private boolean registrationRequired() {
        return this.setting("registration-required", true);
    }

    private boolean webRegistrationRequired() {
        return this.setting("web-registration-required", false);
    }

    private boolean guiEnabled() {
        return this.setting("gui-enabled", true);
    }

    private boolean resourcePackGuiEnabled() {
        return this.setting("resource-pack-gui", true);
    }

    private boolean requestAuthResourcePack(final Player player) {
        if (!this.resourcePackGuiEnabled() || !this.setting("resource-pack-prompt-on-join", true)) {
            return false;
        }
        final HuntEngineService huntEngine = HuntEngineServices.get();
        final var pack = huntEngine.resourcePack();
        if (!huntEngine.available() || !pack.configured() || !pack.published()) {
            this.resourcePackStates.put(player.getUniqueId(), ResourcePackState.FALLBACK);
            return false;
        }
        this.resourcePackStates.put(player.getUniqueId(), ResourcePackState.REQUESTED);
        player.sendMessage(this.text(
            "正在请求安装 HunterCore 登录界面材质包；如果拒绝，会自动使用普通登录界面。",
            "Requesting the HunterCore login UI resource pack. If you decline, the classic login GUI will be used."
        ));
        final var requested = huntEngine.requestResourcePack(player);
        if (requested.success()) {
            return true;
        }
        this.resourcePackStates.put(player.getUniqueId(), ResourcePackState.FALLBACK);
        this.getLogger().fine("HuntEngine did not request the HunterAuth resource pack for " + player.getName() + ": " + requested.message());
        return false;
    }

    private boolean useEnhancedAuthGui(final Player player) {
        return this.resourcePackGuiEnabled() && this.resourcePackReady(player);
    }

    private boolean resourcePackReady(final Player player) {
        return HuntEngineServices.get().resourcePackReady(player.getUniqueId())
            || this.resourcePackStates.get(player.getUniqueId()) == ResourcePackState.READY;
    }

    private boolean enhancedInventory(final Inventory inventory) {
        final InventoryHolder holder = inventory.getHolder();
        return holder instanceof AuthGuiHolder authGuiHolder && authGuiHolder.enhanced()
            || holder instanceof PasswordGuiHolder passwordGuiHolder && passwordGuiHolder.enhanced();
    }

    private boolean isAuthenticated(final Player player) {
        return this.shouldBypass() || this.authenticated.contains(player.getUniqueId());
    }

    private void markAuthenticated(final Player player) {
        final UUID playerId = player.getUniqueId();
        this.authenticated.add(playerId);
        this.chatObservationBlocked.remove(playerId);
        this.pendingInputs.remove(playerId);
        this.clearGuiSession(playerId);
        this.closeManagedInventory(player);
    }

    private void markUnauthenticated(final Player player) {
        final UUID playerId = player.getUniqueId();
        this.authenticated.remove(playerId);
        this.pendingInputs.remove(playerId);
        this.clearGuiSession(playerId);
        this.chatObservationBlocked.add(playerId);
    }

    private void clearGuiSession(final UUID playerId) {
        final GuiSession session = this.guiSessions.remove(playerId);
        if (session != null) {
            session.wipe();
        }
    }

    private void clearGuiSessions() {
        this.guiSessions.values().forEach(GuiSession::wipe);
        this.guiSessions.clear();
    }

    private void closeManagedInventory(final Player player) {
        final Inventory topInventory = player.getOpenInventory().getTopInventory();
        final InventoryHolder holder = topInventory.getHolder();
        if (holder instanceof AuthGuiHolder
            || holder instanceof PasswordGuiHolder
            || this.sharedGuiInventories.get(player.getUniqueId()) == topInventory) {
            player.closeInventory();
        }
    }

    private boolean isRegistered(final Player player) {
        return this.isRegistered(player.getUniqueId(), player.getName());
    }

    private synchronized boolean isRegistered(final UUID uuid, final String username) {
        synchronized (this.usersFileLock()) {
            // Web registration writes this file from another plugin, so correctness is more
            // important than retaining a possibly stale in-memory YAML snapshot here.
            this.reloadUsers();
            return !this.accountCandidates(uuid, username).isEmpty();
        }
    }

    private synchronized boolean verifyPassword(final Player player, final String password) {
        synchronized (this.usersFileLock()) {
            this.reloadUsers();
            final List<HunterAuthAccountResolver.Account> candidates = this.accountCandidates(player.getUniqueId(), player.getName());
            if (candidates.isEmpty() || !this.passwordMatches(candidates.getFirst(), password)) {
                return false;
            }
            // A name-based legacy/web record must be durably moved to the player's UUID before
            // this successful password check can become an authenticated game session.
            return this.bindVerifiedAccount(player, candidates.getFirst());
        }
    }

    private boolean passwordLongEnough(final String password, final Player player) {
        final int minimumLength = this.intSetting("minimum-password-length", 6);
        if (password.length() < minimumLength) {
            player.sendMessage(this.text("密码太短，至少需要 ", "Password is too short. Minimum length: ") + minimumLength + ".");
            return false;
        }
        if (password.length() > 256) {
            player.sendMessage(this.text("密码不能超过 256 个字符。", "Password cannot exceed 256 characters."));
            return false;
        }
        if (!HunterAuthCredentialPolicy.replayable(password)) {
            player.sendMessage(this.text(
                "密码不能包含空白或控制字符。",
                "Passwords cannot contain whitespace or control characters."
            ));
            return false;
        }
        final Set<String> commonPasswords = Set.of("password", "qwerty", "minecraft", "admin", "letmein");
        final String lowerPassword = password.toLowerCase(Locale.ROOT);
        if (commonPasswords.contains(lowerPassword)) {
            player.sendMessage(this.text("这个密码过于常见，请更换。", "That password is too common; choose another one."));
            return false;
        }
        return true;
    }

    private synchronized boolean registerPassword(final Player player, final String password) {
        synchronized (this.usersFileLock()) {
            this.reloadUsers();
            if (!this.accountCandidates(player.getUniqueId(), player.getName()).isEmpty()) {
                return false;
            }
            this.writePassword(path(player), player.getName(), password);
            this.users.set(path(player) + ".identity-uuid", player.getUniqueId().toString());
            this.users.set(path(player) + ".identity-trusted", true);
            this.users.set(path(player) + ".registered-from", "game");
            this.users.set(path(player) + ".registered-at", java.time.Instant.now().toString());
            return this.saveUsers();
        }
    }

    private synchronized OnlineAccountResult registerOnlineModePassword(final Player player, final String password) {
        synchronized (this.usersFileLock()) {
            this.reloadUsers();
            final List<HunterAuthOnlineBindingPolicy.Account> accounts = this.onlineBindingAccounts();
            if (HunterAuthOnlineBindingPolicy.hasConflictingTrustedName(player.getUniqueId(), player.getName(), accounts)) {
                return OnlineAccountResult.CONFLICT;
            }
            for (final HunterAuthOnlineBindingPolicy.Account pending : HunterAuthOnlineBindingPolicy.pendingWebAccounts(
                player.getUniqueId(), player.getName(), accounts
            )) {
                this.users.set("users." + pending.key(), null);
            }

            final String targetPath = path(player);
            this.writePassword(targetPath, player.getName(), password);
            this.users.set(targetPath + ".identity-uuid", player.getUniqueId().toString());
            this.users.set(targetPath + ".identity-trusted", true);
            this.users.set(targetPath + ".registered-from", "game");
            this.users.set(targetPath + ".registered-at", java.time.Instant.now().toString());
            return this.saveUsers() ? OnlineAccountResult.SUCCESS : OnlineAccountResult.FAILED;
        }
    }

    private synchronized OnlineAccountResult claimOnlineModePassword(final Player player, final String password) {
        synchronized (this.usersFileLock()) {
            this.reloadUsers();
            final HunterAuthAccountResolver.Account current = this.currentOnlineModeAccount(player);
            if (current != null) {
                if (!this.passwordMatches(current, password)) {
                    return OnlineAccountResult.INVALID_PASSWORD;
                }
                return this.markCurrentOnlineModeAccountTrusted(player) ? OnlineAccountResult.SUCCESS : OnlineAccountResult.FAILED;
            }
            final List<HunterAuthOnlineBindingPolicy.Account> pending = HunterAuthOnlineBindingPolicy.pendingWebAccounts(
                player.getUniqueId(), player.getName(), this.onlineBindingAccounts()
            );
            if (pending.size() != 1) {
                return OnlineAccountResult.MISSING;
            }
            final HunterAuthOnlineBindingPolicy.Account pendingAccount = pending.getFirst();
            final HunterAuthAccountResolver.Account account = new HunterAuthAccountResolver.Account(
                pendingAccount.key(), pendingAccount.name(), false
            );
            if (!this.passwordMatches(account, password)) {
                return OnlineAccountResult.INVALID_PASSWORD;
            }
            return this.bindPendingWebAccount(player, account) ? OnlineAccountResult.SUCCESS : OnlineAccountResult.FAILED;
        }
    }

    private synchronized OnlineAccountResult changeCurrentOnlineModePassword(
        final Player player,
        final String oldPassword,
        final String newPassword
    ) {
        synchronized (this.usersFileLock()) {
            this.reloadUsers();
            final HunterAuthOnlineBindingPolicy.Account current = HunterAuthOnlineBindingPolicy.currentTrustedAccount(
                player.getUniqueId(), this.onlineBindingAccounts()
            );
            if (current == null) {
                return OnlineAccountResult.MISSING;
            }
            final HunterAuthAccountResolver.Account account = new HunterAuthAccountResolver.Account(
                current.key(), current.name(), true
            );
            if (!this.passwordMatches(account, oldPassword)) {
                return OnlineAccountResult.INVALID_PASSWORD;
            }
            final String targetPath = "users." + current.key();
            this.writePassword(targetPath, player.getName(), newPassword);
            this.users.set(targetPath + ".identity-uuid", player.getUniqueId().toString());
            this.users.set(targetPath + ".identity-trusted", true);
            return this.saveUsers() ? OnlineAccountResult.SUCCESS : OnlineAccountResult.FAILED;
        }
    }

    private boolean bindPendingWebAccount(final Player player, final HunterAuthAccountResolver.Account account) {
        final String sourcePath = "users." + account.key();
        final ConfigurationSection source = this.users.getConfigurationSection(sourcePath);
        if (source == null) {
            return false;
        }
        final String targetPath = path(player);
        if (this.users.contains(targetPath + ".hash")) {
            this.users.set(targetPath + ".salt", source.getString("salt", ""));
            this.users.set(targetPath + ".hash", source.getString("hash", ""));
            this.users.set(targetPath + ".registered-from", source.getString("registered-from", "web-panel"));
            this.users.set(targetPath + ".registered-at", source.getString("registered-at", java.time.Instant.now().toString()));
            this.users.set(sourcePath, null);
            this.users.set(targetPath + ".name", player.getName());
            this.users.set(targetPath + ".identity-uuid", player.getUniqueId().toString());
            this.users.set(targetPath + ".identity-trusted", true);
            this.users.set(targetPath + ".bound-at", java.time.Instant.now().toString());
            return this.saveUsers();
        }

        final Map<String, Object> values = new HashMap<>(source.getValues(true));
        this.users.set(sourcePath, null);
        final ConfigurationSection target = this.users.createSection(targetPath);
        values.forEach(target::set);
        target.set("name", player.getName());
        target.set("identity-uuid", player.getUniqueId().toString());
        target.set("identity-trusted", true);
        target.set("previous-uuid", account.key());
        target.set("bound-at", java.time.Instant.now().toString());
        return this.saveUsers();
    }

    private HunterAuthAccountResolver.Account currentOnlineModeAccount(final Player player) {
        final String key = player.getUniqueId().toString();
        final String accountPath = "users." + key;
        if (!this.users.isString(accountPath + ".hash")) {
            return null;
        }
        return new HunterAuthAccountResolver.Account(
            key,
            this.users.getString(accountPath + ".name", player.getName()),
            this.users.getBoolean(accountPath + ".identity-trusted", false)
        );
    }

    private boolean markCurrentOnlineModeAccountTrusted(final Player player) {
        final String targetPath = path(player);
        boolean changed = false;
        if (!player.getName().equals(this.users.getString(targetPath + ".name", ""))) {
            this.users.set(targetPath + ".name", player.getName());
            changed = true;
        }
        if (!player.getUniqueId().toString().equals(this.users.getString(targetPath + ".identity-uuid", ""))
            || !this.users.getBoolean(targetPath + ".identity-trusted", false)) {
            this.users.set(targetPath + ".identity-uuid", player.getUniqueId().toString());
            this.users.set(targetPath + ".identity-trusted", true);
            this.users.set(targetPath + ".bound-at", java.time.Instant.now().toString());
            changed = true;
        }
        if (this.removeDuplicatePendingWebAccounts(player.getUniqueId().toString(), player.getName())) {
            changed = true;
        }
        return !changed || this.saveUsers();
    }

    private synchronized boolean changePassword(final Player player, final String password) {
        synchronized (this.usersFileLock()) {
            this.reloadUsers();
            final List<HunterAuthAccountResolver.Account> candidates = this.accountCandidates(player.getUniqueId(), player.getName());
            if (candidates.isEmpty()) {
                return false;
            }
            final HunterAuthAccountResolver.Account account = candidates.getFirst();
            this.writePassword("users." + account.key(), player.getName(), password);
            return this.saveUsers();
        }
    }

    private void writePassword(final String accountPath, final String username, final String password) {
        final byte[] salt = new byte[SALT_BYTES];
        this.random.nextBytes(salt);
        this.users.set(accountPath + ".name", username);
        this.users.set(accountPath + ".salt", Base64.getEncoder().encodeToString(salt));
        this.users.set(accountPath + ".hash", hash(password, salt));
    }

    private synchronized boolean saveUsers() {
        this.users.set("schema-version", 2);
        final HunterAuthUsersPersistence.Result<YamlConfiguration> result = HunterAuthUsersPersistence.persist(
            this.users,
            () -> YamlConfiguration.loadConfiguration(this.usersFile),
            configuration -> atomicSave(configuration, this.usersFile)
        );
        this.users = result.state();
        if (!result.saved()) {
            this.getLogger().severe("Failed to save users.yml: " + result.failure().getMessage());
        }
        return result.saved();
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

    private synchronized void reloadUsers() {
        this.users = YamlConfiguration.loadConfiguration(this.usersFile);
    }

    private List<HunterAuthAccountResolver.Account> accountCandidates(final UUID uuid, final String username) {
        final ConfigurationSection section = this.users.getConfigurationSection("users");
        if (section == null) {
            return List.of();
        }
        final List<HunterAuthAccountResolver.Account> accounts = new java.util.ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final String accountPath = "users." + key;
            if (!this.users.isString(accountPath + ".hash")) {
                continue;
            }
            accounts.add(new HunterAuthAccountResolver.Account(
                key,
                this.users.getString(accountPath + ".name", ""),
                this.users.getBoolean(accountPath + ".identity-trusted", false)
            ));
        }
        return HunterAuthAccountResolver.candidates(uuid, username, accounts);
    }

    private List<HunterAuthOnlineBindingPolicy.Account> onlineBindingAccounts() {
        final ConfigurationSection section = this.users.getConfigurationSection("users");
        if (section == null) {
            return List.of();
        }
        final List<HunterAuthOnlineBindingPolicy.Account> accounts = new java.util.ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final String accountPath = "users." + key;
            if (!this.users.isString(accountPath + ".hash")) {
                continue;
            }
            accounts.add(new HunterAuthOnlineBindingPolicy.Account(
                key,
                this.users.getString(accountPath + ".name", ""),
                this.users.getBoolean(accountPath + ".identity-trusted", false),
                this.users.getString(accountPath + ".registered-from", "")
            ));
        }
        return List.copyOf(accounts);
    }

    private boolean passwordMatches(final HunterAuthAccountResolver.Account account, final String password) {
        final String accountPath = "users." + account.key();
        final String encodedSalt = this.users.getString(accountPath + ".salt");
        final String encodedExpected = this.users.getString(accountPath + ".hash");
        if (encodedSalt == null || encodedExpected == null) {
            return false;
        }
        try {
            final byte[] expected = Base64.getDecoder().decode(encodedExpected);
            final byte[] actual = Base64.getDecoder().decode(hash(password, Base64.getDecoder().decode(encodedSalt)));
            return MessageDigest.isEqual(expected, actual);
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean bindVerifiedAccount(final Player player, final HunterAuthAccountResolver.Account account) {
        final String targetKey = player.getUniqueId().toString();
        final String targetPath = "users." + targetKey;
        final String sourcePath = "users." + account.key();
        if (account.key().equalsIgnoreCase(targetKey)) {
            boolean changed = false;
            if (!player.getName().equals(this.users.getString(targetPath + ".name", ""))
                || !player.getUniqueId().toString().equals(this.users.getString(targetPath + ".identity-uuid", ""))
                || !this.users.getBoolean(targetPath + ".identity-trusted", false)) {
                this.users.set(targetPath + ".name", player.getName());
                this.users.set(targetPath + ".identity-uuid", player.getUniqueId().toString());
                this.users.set(targetPath + ".identity-trusted", true);
                changed = true;
            }
            if (this.removeDuplicatePendingWebAccounts(targetKey, player.getName())) {
                changed = true;
            }
            if (changed) {
                return this.saveUsers();
            }
            return true;
        }
        if (this.users.contains(targetPath + ".hash")) {
            return false;
        }
        final ConfigurationSection source = this.users.getConfigurationSection(sourcePath);
        if (source == null) {
            return false;
        }
        final Map<String, Object> values = new HashMap<>(source.getValues(true));
        this.users.set(sourcePath, null);
        final ConfigurationSection target = this.users.createSection(targetPath);
        values.forEach(target::set);
        target.set("name", player.getName());
        target.set("identity-uuid", player.getUniqueId().toString());
        target.set("identity-trusted", true);
        target.set("previous-uuid", account.key());
        target.set("bound-at", java.time.Instant.now().toString());
        this.removeDuplicatePendingWebAccounts(targetKey, player.getName());
        return this.saveUsers();
    }

    private boolean removeDuplicatePendingWebAccounts(final String retainedKey, final String username) {
        final ConfigurationSection section = this.users.getConfigurationSection("users");
        if (section == null) {
            return false;
        }
        boolean changed = false;
        for (final String key : new java.util.ArrayList<>(section.getKeys(false))) {
            if (key.equalsIgnoreCase(retainedKey)) {
                continue;
            }
            final String candidatePath = "users." + key;
            final String candidateName = this.users.getString(candidatePath + ".name", "");
            if (!this.users.getBoolean(candidatePath + ".identity-trusted", false)
                && this.users.getString(candidatePath + ".registered-from", "").equalsIgnoreCase("web-panel")
                && key.equalsIgnoreCase(HunterAuthAccountResolver.offlineUuid(candidateName).toString())
                && candidateName.equalsIgnoreCase(username)) {
                this.users.set("users." + key, null);
                changed = true;
            }
        }
        return changed;
    }

    private Object usersFileLock() {
        return this.usersFile.toPath().toAbsolutePath().normalize().toString().intern();
    }

    private boolean setting(final String key, final boolean fallback) {
        this.refreshSharedPreferences();
        final String path = "modules.auth." + key;
        if (this.sharedPreferences != null && this.sharedPreferences.contains(path)) {
            return this.sharedPreferences.getBoolean(path, fallback);
        }
        return this.getConfig().getBoolean(key, fallback);
    }

    private String stringSetting(final String key, final String fallback) {
        this.refreshSharedPreferences();
        final String path = "modules.auth." + key;
        if (this.sharedPreferences != null && this.sharedPreferences.contains(path)) {
            return this.sharedPreferences.getString(path, fallback);
        }
        return this.getConfig().getString(key, fallback);
    }

    private int intSetting(final String key, final int fallback) {
        this.refreshSharedPreferences();
        final String path = "modules.auth." + key;
        if (this.sharedPreferences != null && this.sharedPreferences.contains(path)) {
            return this.sharedPreferences.getInt(path, fallback);
        }
        return this.getConfig().getInt(key, fallback);
    }

    private String registrationUrl() {
        final String configured = this.stringSetting("registration-url", "").trim();
        if (!configured.isBlank()) {
            return configured;
        }
        this.refreshSharedPreferences();
        if (this.sharedPreferences != null) {
            final String external = this.sharedPreferences.getString("modules.web-panel.external-url", "").trim();
            if (!external.isBlank()) {
                return external.endsWith("/") ? external + "#register" : external + "/#register";
            }
            final String bind = this.sharedPreferences.getString("modules.web-panel.bind-address", "127.0.0.1");
            final int port = this.sharedPreferences.getInt("modules.web-panel.port", 8088);
            return "http://" + bind + ":" + port + "/#register";
        }
        return "http://127.0.0.1:8088/#register";
    }

    private void reloadSharedPreferences() {
        this.sharedPreferences = this.sharedPreferencesFile.exists()
            ? YamlConfiguration.loadConfiguration(this.sharedPreferencesFile)
            : new YamlConfiguration();
        this.sharedPreferencesModified = this.sharedPreferencesFile.exists() ? this.sharedPreferencesFile.lastModified() : 0L;
    }

    private void refreshSharedPreferences() {
        final long modified = this.sharedPreferencesFile.exists() ? this.sharedPreferencesFile.lastModified() : 0L;
        if (modified != this.sharedPreferencesModified) {
            this.reloadSharedPreferences();
        }
    }

    private String text(final String zhCn, final String enUs) {
        return HunterLanguage.choose(HunterCoreProvider.get().language(), zhCn, enUs);
    }

    private ItemStack digitItem(final int digit, final boolean enhanced) {
        if (!enhanced) {
            return this.digitItem(digit);
        }
        final ItemStack item = this.enhancedButton(CMD_AUTH_DIGIT, "&b" + digit, List.of(
            "&7点击输入此位密码",
            "&8也可以使用聊天栏安全输入"
        ));
        item.setAmount(digit == 0 ? 10 : digit);
        return item;
    }

    private ItemStack digitItem(final int digit) {
        final int amount = digit == 0 ? 10 : digit;
        final Material material = digit == 0 ? Material.BLACK_STAINED_GLASS_PANE : Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        final ItemStack item = new ItemStack(material, amount);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + this.text("数字 ", "Digit ") + digit);
        meta.setLore(List.of(ChatColor.GRAY + this.text("点击输入 ", "Click to input ") + digit + "."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack enhancedButton(final int customModelData, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(Material.PAPER);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(HunterAuthPlugin::color).toList());
        meta.setCustomModelData(customModelData);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).toList());
        item.setItemMeta(meta);
        return item;
    }

    private static String color(final String input) {
        return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNullElse(input, ""));
    }

    private static String path(final Player player) {
        return path(player.getUniqueId());
    }

    private static String path(final UUID uuid) {
        return "users." + uuid;
    }

    private static String hash(final String password, final byte[] salt) {
        try {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            final KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
        } catch (final NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Unable to hash password", ex);
        }
    }

    private enum ResourcePackState {
        REQUESTED,
        ACCEPTED,
        READY,
        FALLBACK
    }

    private enum OnlineAccountResult {
        SUCCESS,
        MISSING,
        INVALID_PASSWORD,
        CONFLICT,
        FAILED
    }

    private enum InputMode {
        LOGIN,
        REGISTER,
        CHANGE_PASSWORD
    }

    private record PendingInput(InputMode mode) {
    }

    private final class SharedAuthGuiScreen implements HunterGuiScreen {
        @Override
        public @NotNull String id() {
            return SHARED_AUTH_GUI_SCREEN;
        }

        @Override
        public @NotNull HunterGuiView render(@NotNull final HunterGuiRenderContext context) {
            return HunterAuthPlugin.this.renderSharedAuthGui(context);
        }
    }

    private final class SharedPasswordGuiScreen implements HunterGuiScreen {
        @Override
        public @NotNull String id() {
            return SHARED_PASSWORD_GUI_SCREEN;
        }

        @Override
        public @NotNull HunterGuiView render(@NotNull final HunterGuiRenderContext context) {
            return HunterAuthPlugin.this.renderSharedPasswordGui(context);
        }
    }

    private record AuthGuiHolder(boolean enhanced) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record PasswordGuiHolder(boolean enhanced) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class GuiSession {
        private InputMode mode;
        private String firstPassword;
        private String current = "";

        GuiSession(final InputMode mode) {
            this.mode = mode;
        }

        private InputMode mode() {
            return this.mode;
        }

        private void mode(final InputMode mode) {
            this.mode = mode;
        }

        private String firstPassword() {
            return this.firstPassword;
        }

        private void firstPassword(final String firstPassword) {
            this.firstPassword = firstPassword;
        }

        private String current() {
            return this.current;
        }

        private void current(final String current) {
            this.current = current;
        }

        private void append(final int digit) {
            this.current += digit;
        }

        private void backspace() {
            if (!this.current.isEmpty()) {
                this.current = this.current.substring(0, this.current.length() - 1);
            }
        }

        private void reset() {
            this.firstPassword = null;
            this.current = "";
        }

        private void wipe() {
            this.firstPassword = null;
            this.current = "";
        }
    }

    private static final class ComponentTitle {
        private static final Component HUNTER_AUTH = Component.text(GUI_TITLE);
        private static final Component HUNTER_AUTH_PASSWORD = Component.text(PASSWORD_GUI_TITLE);
        private static final Component HUNTER_AUTH_ENHANCED = Component.text(AUTH_PANEL_GLYPH).font(HUNTERCORE_GUI_FONT);

        private ComponentTitle() {
        }
    }
}

/**
 * Keeps an in-memory users.yml update transactional with its file write. This type deliberately
 * has no Bukkit dependency so its failure behavior can be tested without a running server.
 */
final class HunterAuthUsersPersistence {
    private HunterAuthUsersPersistence() {
    }

    static <T> Result<T> persist(
        final T candidate,
        final java.util.function.Supplier<T> reload,
        final Writer<T> writer
    ) {
        try {
            writer.save(candidate);
            return new Result<>(true, candidate, null);
        } catch (final IOException | SecurityException ex) {
            // The caller may already have changed its in-memory configuration. Reloading keeps
            // failed registrations, password changes, and UUID migrations from becoming a
            // phantom state that the next request could observe.
            return new Result<>(false, reload.get(), ex);
        }
    }

    @FunctionalInterface
    interface Writer<T> {
        void save(T candidate) throws IOException;
    }

    record Result<T>(boolean saved, T state, Exception failure) {
    }
}

/**
 * Passwords must survive both Minecraft command/chat input and JSON web login unchanged.
 */
final class HunterAuthCredentialPolicy {
    private HunterAuthCredentialPolicy() {
    }

    static boolean replayable(final String value) {
        return value != null && !value.isBlank() && value.codePoints().noneMatch(codePoint ->
            Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || Character.isISOControl(codePoint)
        );
    }
}
