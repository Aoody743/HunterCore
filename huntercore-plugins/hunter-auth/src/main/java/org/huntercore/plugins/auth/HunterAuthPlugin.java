package org.huntercore.plugins.auth;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.HunterLanguage;
import org.jetbrains.annotations.NotNull;

public final class HunterAuthPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final String GUI_TITLE = ChatColor.DARK_AQUA + "HunterAuth";
    private static final Set<String> ALLOWED_COMMANDS = Set.of("/login", "/l", "/register", "/reg");

    private final SecureRandom random = new SecureRandom();
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();
    private File usersFile;
    private YamlConfiguration users;
    private long usersModified;
    private File sharedPreferencesFile;
    private YamlConfiguration sharedPreferences;
    private long sharedPreferencesModified;

    @Override
    public void onEnable() {
        this.getConfig().addDefault("enabled", true);
        this.getConfig().addDefault("online-mode-bypass", true);
        this.getConfig().addDefault("registration-required", true);
        this.getConfig().addDefault("web-registration-required", false);
        this.getConfig().addDefault("gui-enabled", true);
        this.getConfig().addDefault("open-gui-on-join", true);
        this.getConfig().addDefault("minimum-password-length", 4);
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
        this.getServer().getPluginManager().registerEvents(this, this);
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
            case "register" -> this.register(player, args);
            case "login" -> this.login(player, args);
            case "logout" -> this.logout(player);
            case "changepassword" -> this.changePassword(player, args);
            default -> false;
    };
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(final AsyncPlayerPreLoginEvent event) {
        if (this.shouldBypass() || !this.registrationRequired() || !this.webRegistrationRequired()) {
            return;
        }
        if (this.isRegistered(event.getUniqueId())) {
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
            this.authenticated.add(player.getUniqueId());
            return;
        }
        if (!this.registrationRequired() && !this.isRegistered(player)) {
            this.authenticated.add(player.getUniqueId());
            player.sendMessage(this.text("此服务器未强制注册，已自动通过登录。", "Registration is not required on this server; you are logged in automatically."));
            return;
        }
        this.sendLoginPrompt(player);
        if (this.guiEnabled() && this.setting("open-gui-on-join", true)) {
            this.getServer().getScheduler().runTaskLater(this, () -> {
                if (player.isOnline() && !this.isAuthenticated(player)) {
                    this.openAuthGui(player);
                }
            }, 10L);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.authenticated.remove(event.getPlayer().getUniqueId());
        this.pendingInputs.remove(event.getPlayer().getUniqueId());
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(final AsyncPlayerChatEvent event) {
        final PendingInput pending = this.pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pending != null) {
            event.setCancelled(true);
            final String message = event.getMessage().trim();
            this.getServer().getScheduler().runTask(this, () -> this.handlePendingInput(event.getPlayer(), pending, message));
            return;
        }
        if (!this.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(this.text("请先登录再聊天。", "Please log in before chatting."));
            this.openAuthGuiSoon(event.getPlayer());
        }
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
        if (!(event.getWhoClicked() instanceof final Player player) || !event.getView().title().equals(ComponentTitle.HUNTER_AUTH)) {
            return;
        }
        event.setCancelled(true);
        if (this.isAuthenticated(player)) {
            player.closeInventory();
            return;
        }
        switch (event.getRawSlot()) {
            case 11 -> this.startGuiInput(player, InputMode.LOGIN);
            case 15 -> this.startGuiInput(player, InputMode.REGISTER);
            case 22 -> this.sendLoginPrompt(player);
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof final Player player) || this.isAuthenticated(player) || !this.guiEnabled()) {
            return;
        }
        if (this.pendingInputs.containsKey(player.getUniqueId())) {
            return;
        }
        if (!event.getView().title().equals(ComponentTitle.HUNTER_AUTH)) {
            return;
        }
        this.openAuthGuiSoon(player);
    }

    private boolean register(final Player player, final String[] args) {
        if (this.shouldBypass()) {
            player.sendMessage(this.text("服务器正版模式下 HunterAuth 已绕过。", "HunterAuth is bypassed while the server is in online mode."));
            return true;
        }
        if (!this.registrationRequired()) {
            player.sendMessage(this.text("此服务器未强制注册账号密码。", "This server does not require account registration."));
            this.authenticated.add(player.getUniqueId());
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

        this.setPassword(player, args[0]);
        this.authenticated.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(this.text("注册成功并已登录。", "Registered and logged in."));
        return true;
    }

    private boolean login(final Player player, final String[] args) {
        if (this.shouldBypass()) {
            player.sendMessage(this.text("服务器正版模式下 HunterAuth 已绕过。", "HunterAuth is bypassed while the server is in online mode."));
            return true;
        }
        if (!this.isRegistered(player)) {
            if (!this.registrationRequired()) {
                this.authenticated.add(player.getUniqueId());
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
        if (!this.verifyPassword(player, args[0])) {
            player.sendMessage(this.text("密码错误。", "Incorrect password."));
            return true;
        }
        this.authenticated.add(player.getUniqueId());
        this.pendingInputs.remove(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(this.text("登录成功。", "Logged in."));
        return true;
    }

    private boolean logout(final Player player) {
        if (this.shouldBypass()) {
            player.sendMessage(this.text("服务器正版模式下 HunterAuth 已绕过。", "HunterAuth is bypassed while the server is in online mode."));
            return true;
        }
        this.authenticated.remove(player.getUniqueId());
        player.sendMessage(this.text("已退出登录。", "Logged out."));
        this.openAuthGuiSoon(player);
        return true;
    }

    private boolean changePassword(final Player player, final String[] args) {
        if (this.shouldBypass()) {
            player.sendMessage(this.text("服务器正版模式下 HunterAuth 已绕过。", "HunterAuth is bypassed while the server is in online mode."));
            return true;
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
        this.setPassword(player, args[1]);
        this.authenticated.add(player.getUniqueId());
        player.sendMessage(this.text("密码已修改。", "Password changed."));
        return true;
    }

    private void openAuthGui(final Player player) {
        if (!this.guiEnabled() || this.isAuthenticated(player)) {
            return;
        }
        final Inventory inventory = Bukkit.createInventory(player, 27, ComponentTitle.HUNTER_AUTH);
        inventory.setItem(11, item(Material.EMERALD, this.text("登录", "Login"), List.of("/login <password>", this.text("点击后在聊天栏输入密码。", "Click, then type your password in chat."))));
        inventory.setItem(15, item(Material.WRITABLE_BOOK, this.text("注册", "Register"), List.of("/register <password> <password>", this.text("点击后输入两次密码，用空格分开。", "Click, then type password twice separated by a space."))));
        inventory.setItem(22, item(Material.PAPER, this.text("命令也可用", "Commands still work"), List.of("/login <password>", "/register <password> <password>", "/changepassword <old> <new>")));
        player.openInventory(inventory);
    }

    private void openAuthGuiSoon(final Player player) {
        if (!this.guiEnabled()) {
            return;
        }
        this.getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !this.isAuthenticated(player) && !this.pendingInputs.containsKey(player.getUniqueId())) {
                this.openAuthGui(player);
            }
        }, 2L);
    }

    private void startGuiInput(final Player player, final InputMode mode) {
        player.closeInventory();
        this.pendingInputs.put(player.getUniqueId(), new PendingInput(mode));
        player.sendMessage(mode == InputMode.LOGIN
            ? this.text("请直接在聊天栏输入密码，不会广播。也可以使用 /login <password>。", "Type your password in chat; it will not be broadcast. You can also use /login <password>.")
            : this.text("请在聊天栏输入两次密码，用空格分开，不会广播。也可以使用 /register <password> <password>。", "Type password twice separated by a space; it will not be broadcast. You can also use /register <password> <password>."));
    }

    private void handlePendingInput(final Player player, final PendingInput pending, final String message) {
        if (pending.mode() == InputMode.LOGIN) {
            this.login(player, new String[] {message});
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

    private boolean shouldBypass() {
        return !this.setting("enabled", true) || Bukkit.getOnlineMode() && this.setting("online-mode-bypass", true);
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

    private boolean isAuthenticated(final Player player) {
        return this.shouldBypass() || this.authenticated.contains(player.getUniqueId());
    }

    private boolean isRegistered(final Player player) {
        return this.isRegistered(player.getUniqueId());
    }

    private synchronized boolean isRegistered(final UUID uuid) {
        this.refreshUsers();
        return this.users.contains(path(uuid) + ".hash");
    }

    private synchronized boolean verifyPassword(final Player player, final String password) {
        this.refreshUsers();
        final String path = path(player);
        final String salt = this.users.getString(path + ".salt");
        final String expected = this.users.getString(path + ".hash");
        if (salt == null || expected == null) {
            return false;
        }
        return expected.equals(hash(password, Base64.getDecoder().decode(salt)));
    }

    private boolean passwordLongEnough(final String password, final Player player) {
        if (password.length() >= this.intSetting("minimum-password-length", 4)) {
            return true;
        }
        player.sendMessage(this.text("密码太短。", "Password is too short."));
        return false;
    }

    private synchronized void setPassword(final Player player, final String password) {
        this.refreshUsers();
        final byte[] salt = new byte[SALT_BYTES];
        this.random.nextBytes(salt);
        final String path = path(player);
        this.users.set(path + ".name", player.getName());
        this.users.set(path + ".salt", Base64.getEncoder().encodeToString(salt));
        this.users.set(path + ".hash", hash(password, salt));
        this.saveUsers();
    }

    private synchronized void saveUsers() {
        try {
            this.users.save(this.usersFile);
            this.usersModified = this.usersFile.exists() ? this.usersFile.lastModified() : 0L;
        } catch (final IOException ex) {
            this.getLogger().severe("Failed to save users.yml: " + ex.getMessage());
        }
    }

    private synchronized void reloadUsers() {
        this.users = YamlConfiguration.loadConfiguration(this.usersFile);
        this.usersModified = this.usersFile.exists() ? this.usersFile.lastModified() : 0L;
    }

    private synchronized void refreshUsers() {
        final long modified = this.usersFile.exists() ? this.usersFile.lastModified() : 0L;
        if (modified != this.usersModified) {
            this.reloadUsers();
        }
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

    private static ItemStack item(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + name);
        meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).toList());
        item.setItemMeta(meta);
        return item;
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

    private enum InputMode {
        LOGIN,
        REGISTER
    }

    private record PendingInput(InputMode mode) {
    }

    private static final class ComponentTitle {
        private static final Component HUNTER_AUTH = Component.text(ChatColor.stripColor(GUI_TITLE));

        private ComponentTitle() {
        }
    }
}
