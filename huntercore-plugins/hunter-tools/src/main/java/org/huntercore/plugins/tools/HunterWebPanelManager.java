package org.huntercore.plugins.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

final class HunterWebPanelManager {
    private static final String SESSION_COOKIE = "HCSESSION";
    private static final String SESSION_HEADER = "X-HunterCore-Session";
    private static final String CSRF_HEADER = "X-HunterCore-CSRF";
    private static final String API_KEY_HEADER = "X-HunterCore-Api-Key";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(15L))
        .build();
    private static final int HASH_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;
    private static final Pattern LUCKPERMS_EDITOR_URL = Pattern.compile("https://luckperms\\.net/editor/[^\\s<>\\]\")]+", Pattern.CASE_INSENSITIVE);
    private static final List<String> MODULES = List.of("tps-display", "sidebar", "motd", "command-overrides", "essentials", "management", "fake-players", "real-fake-players", "npcs", "ai", "auth", "web-panel");
    private static final Map<String, List<String>> MODULE_COMMANDS = Map.of(
        "essentials", HunterToolsPreferences.essentialsCommands(),
        "management", HunterToolsPreferences.managementCommands(),
        "real-fake-players", HunterToolsPreferences.realFakePlayerCommands(),
        "fake-players", HunterToolsPreferences.actorCommands(),
        "npcs", HunterToolsPreferences.actorCommands()
    );

    private final HunterToolsPlugin plugin;
    private final HunterToolsPreferences preferences;
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, CachedResponse> statusCaches = new ConcurrentHashMap<>();
    private final AtomicLong nextPluginOperationAt = new AtomicLong();
    private final Object hunterAuthUsersLock = new Object();
    private final Deque<WebChatLine> chatLines = new ArrayDeque<>();
    private HttpServer server;
    private ExecutorService executor;

    HunterWebPanelManager(final HunterToolsPlugin plugin, final HunterToolsPreferences preferences) {
        this.plugin = plugin;
        this.preferences = preferences;
    }

    synchronized void start() {
        this.stop();
        if (!this.preferences.moduleEnabled("web-panel")) {
            return;
        }

        final String bindAddress = this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1");
        final int port = Math.max(1, Math.min(65535, this.preferences.intValue("modules.web-panel.port", 8088)));
        try {
            this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), 64);
        } catch (final IOException ex) {
            this.plugin.getLogger().severe("Failed to start HunterCore web panel on " + bindAddress + ":" + port + ": " + ex.getMessage());
            return;
        }

        this.executor = this.createExecutor();
        this.server.setExecutor(this.executor);
        this.server.createContext("/", this::handle);
        this.server.start();
        this.plugin.getLogger().info("HunterCore web panel listening on http://" + bindAddress + ":" + port + "/");
    }

    synchronized void restart() {
        this.start();
    }

    synchronized void stop() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
            this.executor = null;
        }
        this.invalidateStatusCaches();
    }

    boolean running() {
        return this.server != null;
    }

    void observeChat(final Player player, final String rawMessage) {
        final String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            return;
        }
        this.addChatLine(player.getName(), "game", message);
    }

    void observeSyntheticChat(final String sender, final String source, final String rawMessage) {
        final String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            return;
        }
        this.addChatLine(sender, source, message);
    }

    String addressLine() {
        final String bindAddress = this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1");
        final int port = Math.max(1, Math.min(65535, this.preferences.intValue("modules.web-panel.port", 8088)));
        return "http://" + bindAddress + ":" + port + "/";
    }

    private void invalidateStatusCaches() {
        this.statusCaches.clear();
    }

    private String cacheBucket(final WebSession session) {
        if (session == null) {
            return "guest";
        }
        return session.admin() ? "admin" : "player";
    }

    private int cacheMillis(final MetricsSnapshot snapshot, final String bucket) {
        final AdaptiveBudget adaptiveBudget = snapshot.adaptiveBudget();
        return switch (bucket) {
            case "admin" -> adaptiveBudget.adminCacheMillis();
            case "player" -> adaptiveBudget.playerCacheMillis();
            default -> adaptiveBudget.guestCacheMillis();
        };
    }

    private String pluginOperationRateLimitError() {
        final long now = System.currentTimeMillis();
        final int minInterval = Math.max(0, this.preferences.intValue("modules.web-panel.plugin-operation-min-interval-millis", 1500));
        final long allowedAt = this.nextPluginOperationAt.get();
        if (allowedAt > now) {
            return "Plugin operations are cooling down for another " + (allowedAt - now) + " ms.";
        }
        this.nextPluginOperationAt.set(now + minInterval);
        return null;
    }

    static String hashPassword(final String password) {
        final byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        final byte[] hash = pbkdf2(password.toCharArray(), salt, HASH_ITERATIONS, HASH_BITS);
        return "pbkdf2$" + HASH_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
    }

    static boolean verifyPassword(final String password, final String stored) {
        if (stored == null || stored.isBlank() || !stored.startsWith("pbkdf2$")) {
            return false;
        }
        final String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            final int iterations = Integer.parseInt(parts[1]);
            final byte[] salt = Base64.getDecoder().decode(parts[2]);
            final byte[] expected = Base64.getDecoder().decode(parts[3]);
            final byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean verifyHunterAuthPassword(final String username, final String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        final Path usersPath = hunterAuthUsersPath();
        if (!Files.isRegularFile(usersPath)) {
            return false;
        }
        final YamlConfiguration users = YamlConfiguration.loadConfiguration(usersPath.toFile());
        final ConfigurationSection section = users.getConfigurationSection("users");
        if (section == null) {
            return false;
        }
        for (final String id : section.getKeys(false)) {
            final String path = "users." + id;
            final String name = users.getString(path + ".name", "");
            if (!id.equalsIgnoreCase(username) && !name.equalsIgnoreCase(username)) {
                continue;
            }
            final String salt = users.getString(path + ".salt");
            final String expectedHash = users.getString(path + ".hash");
            if (salt == null || expectedHash == null) {
                return false;
            }
            try {
                final byte[] expected = Base64.getDecoder().decode(expectedHash);
                final byte[] actual = pbkdf2(password.toCharArray(), Base64.getDecoder().decode(salt), HASH_ITERATIONS, HASH_BITS);
                return MessageDigest.isEqual(expected, actual);
            } catch (final IllegalArgumentException ex) {
                return false;
            }
        }
        return false;
    }

    private static Path hunterAuthUsersPath() {
        return Bukkit.getPluginsFolder().toPath().resolve("HunterAuth").resolve("users.yml");
    }

    private ExecutorService createExecutor() {
        final int configured = this.preferences.intValue("optimizations.hunter-tools.web-panel-workers", 4);
        final int workers = Math.max(1, Math.min(configured, Math.min(8, Runtime.getRuntime().availableProcessors())));
        final AtomicInteger id = new AtomicInteger();
        final ThreadFactory factory = task -> {
            final Thread thread = new Thread(task, "HunterTools web panel " + id.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(workers, factory);
    }

    private void handle(final HttpExchange exchange) {
        try {
            final URI uri = exchange.getRequestURI();
            final String path = normalizePath(uri.getPath());
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                this.sendNoContent(exchange, 204);
                return;
            }
            if (path.equals("/")) {
                this.send(exchange, 200, "text/html; charset=utf-8", webPanelHtml("backend"));
                return;
            }
            if (path.equals("/frontend.html") || path.equals("/remote.html")) {
                this.send(exchange, 200, "text/html; charset=utf-8", webPanelHtml("frontend"));
                return;
            }
            if (path.equals("/assets/app.css")) {
                this.send(exchange, 200, "text/css; charset=utf-8", webAsset("app.css", APP_CSS));
                return;
            }
            if (path.equals("/assets/app.js")) {
                this.send(exchange, 200, "application/javascript; charset=utf-8", webAsset("app.js", APP_JS));
                return;
            }
            if (path.equals("/assets/panel-bg.jpg")) {
                this.sendBytes(exchange, 200, "image/jpeg", webAssetBytes("panel-bg.jpg"));
                return;
            }
            if (path.equals("/assets/server-icon.png")) {
                this.sendServerIcon(exchange);
                return;
            }
            if (path.equals("/health")) {
                this.send(exchange, 200, "text/plain; charset=utf-8", "ok\n");
                return;
            }
            if (path.equals("/api/status")) {
                this.requireMethod(exchange, "GET");
                this.send(exchange, 200, "application/json; charset=utf-8", this.statusJson(exchange));
                return;
            }
            if (path.equals("/api/player/self")) {
                this.requireMethod(exchange, "GET");
                this.playerSelf(exchange);
                return;
            }
            if (path.equals("/api/player/self/inventory")) {
                this.requireMethod(exchange, "GET");
                this.playerInventory(exchange, false, null);
                return;
            }
            if (path.equals("/api/player/self/enderchest")) {
                this.requireMethod(exchange, "GET");
                this.playerEnderChest(exchange, false, null);
                return;
            }
            if (path.equals("/api/admin/dashboard")) {
                this.requireMethod(exchange, "GET");
                this.adminDashboard(exchange);
                return;
            }
            if (path.startsWith("/api/admin/player/")) {
                this.requireMethod(exchange, "GET");
                this.adminPlayerData(exchange, path.substring("/api/admin/player/".length()));
                return;
            }
            if (path.equals("/api/map")) {
                this.requireMethod(exchange, "GET");
                this.send(exchange, 200, "application/json; charset=utf-8", this.mapJson(exchange));
                return;
            }
            if (path.equals("/api/session")) {
                this.requireMethod(exchange, "GET");
                this.send(exchange, 200, "application/json; charset=utf-8", this.sessionJson(exchange));
                return;
            }
            if (path.equals("/api/chat")) {
                this.requireMethod(exchange, "GET");
                this.send(exchange, 200, "application/json; charset=utf-8", this.chatJson());
                return;
            }
            if (path.equals("/api/chat/send")) {
                this.requireMethod(exchange, "POST");
                this.chatSend(exchange);
                return;
            }
            if (path.equals("/api/login")) {
                this.requireMethod(exchange, "POST");
                this.login(exchange);
                return;
            }
            if (path.equals("/api/auth/register")) {
                this.requireMethod(exchange, "POST");
                this.authRegister(exchange);
                return;
            }
            if (path.equals("/api/logout")) {
                this.requireMethod(exchange, "POST");
                this.logout(exchange);
                return;
            }
            if (path.equals("/api/command")) {
                this.requireMethod(exchange, "POST");
                this.command(exchange);
                return;
            }
            if (path.equals("/api/admin/module")) {
                this.requireMethod(exchange, "POST");
                this.adminModule(exchange);
                return;
            }
            if (path.equals("/api/admin/command")) {
                this.requireMethod(exchange, "POST");
                this.adminCommand(exchange);
                return;
            }
            if (path.equals("/api/admin/actor/spawn")) {
                this.requireMethod(exchange, "POST");
                this.adminActorSpawn(exchange);
                return;
            }
            if (path.equals("/api/admin/actor/remove")) {
                this.requireMethod(exchange, "POST");
                this.adminActorRemove(exchange);
                return;
            }
            if (path.equals("/api/admin/actor/click-command")) {
                this.requireMethod(exchange, "POST");
                this.adminActorClickCommand(exchange);
                return;
            }
            if (path.equals("/api/admin/actor/ai")) {
                this.requireMethod(exchange, "POST");
                this.adminActorAi(exchange);
                return;
            }
            if (path.equals("/api/admin/ai-approval")) {
                this.requireMethod(exchange, "POST");
                this.adminAiApproval(exchange);
                return;
            }
            if (path.equals("/api/admin/web-user/save")) {
                this.requireMethod(exchange, "POST");
                this.adminWebUserSave(exchange);
                return;
            }
            if (path.equals("/api/admin/web-user/remove")) {
                this.requireMethod(exchange, "POST");
                this.adminWebUserRemove(exchange);
                return;
            }
            if (path.equals("/api/admin/luckperms")) {
                this.requireMethod(exchange, "POST");
                this.adminLuckPerms(exchange);
                return;
            }
            if (path.equals("/api/admin/web-settings")) {
                this.requireMethod(exchange, "POST");
                this.adminWebSettings(exchange);
                return;
            }
            if (path.equals("/api/admin/command-messages")) {
                this.requireMethod(exchange, "POST");
                this.adminCommandMessages(exchange);
                return;
            }
            if (path.equals("/api/admin/ai-settings")) {
                this.requireMethod(exchange, "POST");
                this.adminAiSettings(exchange);
                return;
            }
            if (path.equals("/api/admin/ai-test")) {
                this.requireMethod(exchange, "POST");
                this.adminAiTest(exchange);
                return;
            }
            if (path.equals("/api/admin/plugin")) {
                this.requireMethod(exchange, "POST");
                this.adminPlugin(exchange);
                return;
            }
            if (path.equals("/api/admin/plugin-update")) {
                this.requireMethod(exchange, "POST");
                this.adminPluginUpdate(exchange);
                return;
            }
            this.send(exchange, 404, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"not_found\"}");
        } catch (final MethodMismatchException ex) {
            this.send(exchange, 405, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"method_not_allowed\"}");
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("HunterCore web panel request failed: " + ex.getMessage());
            this.send(exchange, 500, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"internal_error\"}");
        } finally {
            exchange.close();
        }
    }

    private String statusJson(final HttpExchange exchange) throws InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.session(exchange);
        final boolean detailed = session != null;
        final String bucket = this.cacheBucket(session);
        final CachedResponse cached = this.statusCaches.get(bucket);
        if (cached != null && cached.expiresAtMillis() > System.currentTimeMillis()) {
            return cached.body();
        }

        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final String json = Bukkit.getScheduler().callSyncMethod(this.plugin, () -> this.buildStatusJson(session, detailed)).get(timeout, TimeUnit.SECONDS);
        final int cacheMillis = this.cacheMillis(this.plugin.metricsSnapshot(), bucket);
        if (cacheMillis > 0) {
            this.statusCaches.put(bucket, new CachedResponse(json, System.currentTimeMillis() + cacheMillis));
        }
        return json;
    }

    private void playerSelf(final HttpExchange exchange) throws InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.requireLogin(exchange);
        if (session == null) {
            return;
        }
        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final String json = Bukkit.getScheduler()
            .callSyncMethod(this.plugin, () -> {
                final Player player = this.sessionPlayer(session);
                return player == null
                    ? "{\"ok\":false,\"error\":\"player_offline\"}"
                    : "{\"ok\":true,\"player\":" + this.playerProfileJson(player, session.admin()) + "}";
            })
            .get(timeout, TimeUnit.SECONDS);
        this.send(exchange, json.contains("\"ok\":false") ? 404 : 200, "application/json; charset=utf-8", json);
    }

    private void playerInventory(final HttpExchange exchange, final boolean admin, final String playerName)
        throws InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = admin ? this.adminOperator(exchange) : this.requireLogin(exchange);
        if (session == null) {
            return;
        }
        if (admin) {
            this.audit(session, "view-inventory", playerName);
        }
        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final String json = Bukkit.getScheduler()
            .callSyncMethod(this.plugin, () -> {
                final Player player = admin ? Bukkit.getPlayerExact(playerName) : this.sessionPlayer(session);
                if (player == null) {
                    return "{\"ok\":false,\"error\":\"player_offline\"}";
                }
                return "{\"ok\":true,\"player\":" + this.playerProfileJson(player, true)
                    + ",\"inventory\":" + this.playerInventoryJson(player) + "}";
            })
            .get(timeout, TimeUnit.SECONDS);
        this.send(exchange, json.contains("\"ok\":false") ? 404 : 200, "application/json; charset=utf-8", json);
    }

    private void playerEnderChest(final HttpExchange exchange, final boolean admin, final String playerName)
        throws InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = admin ? this.adminOperator(exchange) : this.requireLogin(exchange);
        if (session == null) {
            return;
        }
        if (admin) {
            this.audit(session, "view-enderchest", playerName);
        }
        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final String json = Bukkit.getScheduler()
            .callSyncMethod(this.plugin, () -> {
                final Player player = admin ? Bukkit.getPlayerExact(playerName) : this.sessionPlayer(session);
                if (player == null) {
                    return "{\"ok\":false,\"error\":\"player_offline\"}";
                }
                return "{\"ok\":true,\"player\":" + this.playerProfileJson(player, true)
                    + ",\"enderChest\":" + this.inventoryContentsJson(player.getEnderChest()) + "}";
            })
            .get(timeout, TimeUnit.SECONDS);
        this.send(exchange, json.contains("\"ok\":false") ? 404 : 200, "application/json; charset=utf-8", json);
    }

    private void adminDashboard(final HttpExchange exchange) throws InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        this.audit(session, "view-dashboard", "server");
        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final String json = Bukkit.getScheduler().callSyncMethod(this.plugin, () -> this.buildStatusJson(session, true)).get(timeout, TimeUnit.SECONDS);
        this.send(exchange, 200, "application/json; charset=utf-8", json);
    }

    private void adminPlayerData(final HttpExchange exchange, final String suffix)
        throws InterruptedException, ExecutionException, TimeoutException {
        final String[] parts = suffix.split("/", 2);
        final String playerName = commandToken(parts[0], 64);
        if (playerName.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_player\"}");
            return;
        }
        if (parts.length == 2 && parts[1].equals("inventory")) {
            this.playerInventory(exchange, true, playerName);
            return;
        }
        if (parts.length == 2 && parts[1].equals("enderchest")) {
            this.playerEnderChest(exchange, true, playerName);
            return;
        }
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        this.audit(session, "view-player", playerName);
        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final String json = Bukkit.getScheduler()
            .callSyncMethod(this.plugin, () -> {
                final Player player = Bukkit.getPlayerExact(playerName);
                return player == null
                    ? "{\"ok\":false,\"error\":\"player_offline\"}"
                    : "{\"ok\":true,\"player\":" + this.playerProfileJson(player, true) + "}";
            })
            .get(timeout, TimeUnit.SECONDS);
        this.send(exchange, json.contains("\"ok\":false") ? 404 : 200, "application/json; charset=utf-8", json);
    }

    private String buildStatusJson(final WebSession session, final boolean detailed) {
        final MetricsSnapshot snapshot = this.plugin.metricsSnapshot();
        final List<WorldPanelStats> worlds = new ArrayList<>();
        for (final World world : Bukkit.getWorlds()) {
            worlds.add(new WorldPanelStats(
                world.getName(),
                world.getPlayers().size(),
                world.getLoadedChunks().length,
                world.getEntityCount(),
                world.getTime()
            ));
        }

        final StringBuilder json = new StringBuilder(2048);
        json.append("{\"ok\":true");
        json.append(",\"session\":").append(session == null ? "null" : sessionJson(session));
        json.append(",\"server\":{");
        field(json, "name", this.webServerName()).append(',');
        field(json, "software", "HunterCore").append(',');
        field(json, "iconUrl", "/assets/server-icon.png").append(',');
        field(json, "version", Bukkit.getVersion()).append(',');
        numberField(json, "online", snapshot.onlinePlayers()).append(',');
        numberField(json, "maxPlayers", snapshot.maxPlayers()).append(',');
        numberField(json, "tps1", snapshot.tps1()).append(',');
        numberField(json, "tps5", snapshot.tps5()).append(',');
        numberField(json, "tps15", snapshot.tps15()).append(',');
        numberField(json, "mspt", snapshot.mspt()).append(',');
        field(json, "memory", snapshot.memoryLine());
        json.append('}');

        json.append(",\"actors\":{");
        numberField(json, "fakePlayers", this.plugin.actorLiveCount("fake-players")).append(',');
        numberField(json, "realFakePlayers", this.plugin.actorLiveCount("real-fake-players")).append(',');
        numberField(json, "npcs", this.plugin.actorLiveCount("npcs"));
        json.append('}');

        json.append(",\"optimization\":{");
        field(json, "mode", this.preferences.stringValue("optimizations.cpu.mode", "single-thread")).append(',');
        numberField(json, "cpuThreads", Runtime.getRuntime().availableProcessors()).append(',');
        field(json, "paperWorkers", System.getProperty("Paper.WorkerThreadCount", "auto")).append(',');
        field(json, "coreWorkers", System.getProperty("DivineMC.WorkerThreadCount", "auto")).append(',');
        field(json, "nettyIoThreads", System.getProperty("io.netty.eventLoopThreads", "auto")).append(',');
        field(json, "forkJoinParallelism", System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "auto")).append(',');
        numberField(json, "hunterToolsWorkers", this.preferences.intValue("optimizations.hunter-tools.render-workers", 4)).append(',');
        booleanField(json, "asyncActorLoad", this.preferences.booleanValue("optimizations.hunter-tools.actor-async-load", true)).append(',');
        booleanField(json, "actorBatchSave", this.preferences.booleanValue("optimizations.hunter-tools.actor-batch-save", true)).append(',');
        booleanField(json, "experimentalRegionTickingAllowed", this.preferences.booleanValue("optimizations.cpu.allow-experimental-region-ticking", false)).append(',');
        booleanField(json, "managedThreading", Boolean.parseBoolean(System.getProperty("huntercore.threading.managed", "true"))).append(',');
        numberField(json, "webPanelWorkers", this.preferences.intValue("optimizations.hunter-tools.web-panel-workers", 4)).append(',');
        field(json, "aiThrottleFactor", snapshot.adaptiveBudget().factorLabel()).append(',');
        numberField(json, "fakePlayerRuntimeIntervalSeconds", snapshot.adaptiveBudget().fakePlayerIntervalSeconds()).append(',');
        numberField(json, "guestStatusCacheMillis", snapshot.adaptiveBudget().guestCacheMillis()).append(',');
        numberField(json, "playerStatusCacheMillis", snapshot.adaptiveBudget().playerCacheMillis()).append(',');
        numberField(json, "adminStatusCacheMillis", snapshot.adaptiveBudget().adminCacheMillis()).append(',');
        numberField(json, "pluginOperationMinIntervalMillis", this.preferences.intValue("modules.web-panel.plugin-operation-min-interval-millis", 1500)).append(',');
        numberField(json, "bundledPluginInstallWorkers", this.preferences.intValue("optimizations.bundled-plugin-parallel-install.max-workers", 4));
        json.append('}');

        json.append(",\"queues\":[");
        boolean firstQueue = true;
        for (final QueuePressure queue : snapshot.queuePressures()) {
            if (!firstQueue) {
                json.append(',');
            }
            firstQueue = false;
            json.append('{');
            field(json, "name", queue.name()).append(',');
            booleanField(json, "active", queue.active()).append(',');
            numberField(json, "queued", queue.queued()).append(',');
            numberField(json, "activeThreads", queue.activeThreads()).append(',');
            numberField(json, "maxThreads", queue.maxThreads()).append(',');
            numberField(json, "remainingCapacity", queue.remainingCapacity()).append(',');
            field(json, "state", queue.state());
            json.append('}');
        }
        json.append(']');

        json.append(",\"hotPaths\":[");
        boolean firstHotPath = true;
        for (final HotPathSample sample : snapshot.hotPathSamples()) {
            if (!firstHotPath) {
                json.append(',');
            }
            firstHotPath = false;
            json.append('{');
            field(json, "category", sample.category()).append(',');
            numberField(json, "score", sample.score()).append(',');
            field(json, "detail", sample.detail());
            json.append('}');
        }
        json.append(']');

        json.append(",\"health\":").append(this.healthJson(snapshot, worlds, detailed));
        json.append(",\"auth\":").append(this.authPublicJson());

        json.append(",\"worlds\":[");
        boolean first = true;
        for (final WorldPanelStats world : worlds) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "name", world.name()).append(',');
            numberField(json, "players", world.players()).append(',');
            numberField(json, "loadedChunks", world.loadedChunks()).append(',');
            numberField(json, "entities", world.entities()).append(',');
            numberField(json, "time", world.time());
            json.append('}');
        }
        json.append(']');

        if (detailed) {
            json.append(",\"players\":[");
            first = true;
            for (final Player player : Bukkit.getOnlinePlayers()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append('{');
                field(json, "name", player.getName()).append(',');
                field(json, "world", player.getWorld().getName()).append(',');
                numberField(json, "level", player.getLevel()).append(',');
                numberField(json, "health", player.getHealth()).append(',');
                numberField(json, "food", player.getFoodLevel()).append(',');
                numberField(json, "ping", player.getPing());
                json.append('}');
            }
            json.append(']');

            json.append(",\"plugins\":").append(this.pluginsJson());
        }
        if (session != null && session.admin()) {
            json.append(",\"modules\":").append(this.modulesJson());
            json.append(",\"actorDetails\":").append(this.actorDetailsJson());
            json.append(",\"aiApprovals\":").append(this.aiApprovalsJson());
            json.append(",\"webUsers\":").append(this.webUsersJson());
            json.append(",\"webSettings\":").append(this.webSettingsJson());
            json.append(",\"commandMessages\":").append(this.commandMessagesJson());
            json.append(",\"aiSettings\":").append(this.aiSettingsJson());
        }
        json.append('}');
        return json.toString();
    }

    private WebSession requireLogin(final HttpExchange exchange) {
        final WebSession session = this.session(exchange);
        if (session == null) {
            this.send(exchange, 401, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"login_required\"}");
        }
        return session;
    }

    private void audit(final WebSession session, final String action, final String target) {
        if (session == null) {
            return;
        }
        this.plugin.getLogger().info("[WebAudit] user=" + session.displayName()
            + " role=" + session.role()
            + " action=" + action
            + " target=" + target);
    }

    private Player sessionPlayer(final WebSession session) {
        Player player = Bukkit.getPlayerExact(session.displayName());
        if (player != null) {
            return player;
        }
        player = Bukkit.getPlayerExact(session.username());
        if (player != null) {
            return player;
        }
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (HunterToolsPreferences.webUserId(online.getName()).equals(session.username())) {
                return online;
            }
        }
        return null;
    }

    private String playerProfileJson(final Player player, final boolean detailed) {
        final StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "name", player.getName()).append(',');
        field(json, "uuid", player.getUniqueId().toString()).append(',');
        booleanField(json, "online", player.isOnline()).append(',');
        field(json, "world", player.getWorld().getName()).append(',');
        numberField(json, "x", player.getLocation().getX()).append(',');
        numberField(json, "y", player.getLocation().getY()).append(',');
        numberField(json, "z", player.getLocation().getZ()).append(',');
        field(json, "gameMode", player.getGameMode().name()).append(',');
        numberField(json, "health", player.getHealth()).append(',');
        numberField(json, "maxHealth", player.getMaxHealth()).append(',');
        numberField(json, "food", player.getFoodLevel()).append(',');
        numberField(json, "saturation", player.getSaturation()).append(',');
        numberField(json, "level", player.getLevel()).append(',');
        numberField(json, "expProgress", player.getExp()).append(',');
        numberField(json, "totalExperience", player.getTotalExperience()).append(',');
        numberField(json, "ping", player.getPing()).append(',');
        booleanField(json, "op", detailed && player.isOp()).append(',');
        field(json, "skinAvatarUrl", "https://crafatar.com/avatars/" + player.getUniqueId() + "?overlay");
        json.append('}');
        return json.toString();
    }

    private String playerInventoryJson(final Player player) {
        final StringBuilder json = new StringBuilder(2048);
        json.append('{');
        json.append("\"storage\":").append(itemArrayJson(player.getInventory().getStorageContents())).append(',');
        json.append("\"armor\":").append(itemArrayJson(player.getInventory().getArmorContents())).append(',');
        json.append("\"extra\":").append(itemArrayJson(player.getInventory().getExtraContents()));
        json.append('}');
        return json.toString();
    }

    private String inventoryContentsJson(final Inventory inventory) {
        return itemArrayJson(inventory.getContents());
    }

    private static String itemArrayJson(final ItemStack[] items) {
        final StringBuilder json = new StringBuilder(items.length * 96);
        json.append('[');
        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            appendItemJson(json, i, items[i]);
        }
        json.append(']');
        return json.toString();
    }

    private static void appendItemJson(final StringBuilder json, final int slot, final ItemStack item) {
        json.append('{');
        numberField(json, "slot", slot).append(',');
        if (item == null || item.getType().isAir()) {
            booleanField(json, "empty", true);
            json.append('}');
            return;
        }
        booleanField(json, "empty", false).append(',');
        field(json, "material", item.getType().name()).append(',');
        field(json, "type", humanItemName(item.getType().name())).append(',');
        numberField(json, "amount", item.getAmount()).append(',');
        numberField(json, "maxStackSize", item.getMaxStackSize()).append(',');
        numberField(json, "durability", item.getDurability()).append(',');
        numberField(json, "maxDurability", item.getType().getMaxDurability()).append(',');
        final ItemMeta meta = item.getItemMeta();
        field(json, "displayName", meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "").append(',');
        booleanField(json, "enchanted", meta != null && meta.hasEnchants()).append(',');
        json.append("\"enchants\":{");
        if (meta != null && meta.hasEnchants()) {
            boolean first = true;
            for (final Map.Entry<org.bukkit.enchantments.Enchantment, Integer> enchantment : meta.getEnchants().entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                numberField(json, enchantment.getKey().getKey().getKey(), enchantment.getValue());
            }
        }
        json.append("},\"lore\":");
        json.append(stringArrayJson(meta != null && meta.hasLore() ? meta.getLore() : List.of()));
        json.append('}');
    }

    private static String humanItemName(final String material) {
        final String[] parts = material.toLowerCase(Locale.ROOT).split("_");
        final StringBuilder name = new StringBuilder(material.length());
        for (final String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.toString();
    }

    private String healthJson(final MetricsSnapshot snapshot, final List<WorldPanelStats> worlds, final boolean detailed) {
        final StringBuilder json = new StringBuilder(768);
        final double memoryPercent = memoryUsagePercent(snapshot);
        if (!this.preferences.booleanValue("modules.web-panel.health.enabled", true)) {
            return "{\"status\":\"disabled\",\"memoryUsagePercent\":" + String.format(Locale.ROOT, "%.3f", memoryPercent) + ",\"alerts\":[]}";
        }

        final List<HealthAlert> alerts = this.healthAlerts(snapshot, worlds, memoryPercent, detailed);
        final String status = alerts.stream().anyMatch(alert -> alert.severity().equals("critical"))
            ? "critical"
            : alerts.isEmpty() ? "ok" : "warning";
        json.append('{');
        field(json, "status", status).append(',');
        numberField(json, "memoryUsagePercent", memoryPercent).append(',');
        json.append("\"alerts\":[");
        boolean first = true;
        for (final HealthAlert alert : alerts) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "severity", alert.severity()).append(',');
            field(json, "label", alert.label()).append(',');
            field(json, "detail", alert.detail());
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    private String authPublicJson() {
        final StringBuilder json = new StringBuilder(256);
        json.append('{');
        booleanField(json, "enabled", this.preferences.booleanValue("modules.auth.enabled", true)).append(',');
        booleanField(json, "registrationRequired", this.preferences.booleanValue("modules.auth.registration-required", true)).append(',');
        booleanField(json, "webRegistrationRequired", this.preferences.booleanValue("modules.auth.web-registration-required", false)).append(',');
        booleanField(json, "webRegistrationEnabled", this.preferences.booleanValue("modules.auth.web-registration-enabled", false)).append(',');
        booleanField(json, "webLoginEnabled", this.preferences.booleanValue("modules.auth.web-login-enabled", false)).append(',');
        numberField(json, "minimumPasswordLength", this.preferences.intValue("modules.auth.minimum-password-length", 4)).append(',');
        field(json, "registrationUrl", this.registrationUrl());
        json.append('}');
        return json.toString();
    }

    private List<HealthAlert> healthAlerts(
        final MetricsSnapshot snapshot,
        final List<WorldPanelStats> worlds,
        final double memoryPercent,
        final boolean detailed
    ) {
        final List<HealthAlert> alerts = new ArrayList<>();
        final double lowTpsWarning = this.preferences.doubleValue("modules.web-panel.health.low-tps-warning", 18.0D);
        final double lowTpsCritical = this.preferences.doubleValue("modules.web-panel.health.low-tps-critical", 15.0D);
        final double highMsptWarning = this.preferences.doubleValue("modules.web-panel.health.high-mspt-warning", 50.0D);
        final double highMsptCritical = this.preferences.doubleValue("modules.web-panel.health.high-mspt-critical", 75.0D);
        final double memoryWarning = this.preferences.doubleValue("modules.web-panel.health.memory-warning-percent", 85.0D);
        final double memoryCritical = this.preferences.doubleValue("modules.web-panel.health.memory-critical-percent", 95.0D);
        final int chunkWarning = Math.max(1, this.preferences.intValue("modules.web-panel.health.loaded-chunks-warning", 12_000));
        final int entityWarning = Math.max(1, this.preferences.intValue("modules.web-panel.health.entities-warning", 4_000));

        if (snapshot.tps1() <= lowTpsCritical) {
            alerts.add(new HealthAlert("critical", "Low TPS", "1m TPS is " + MetricsSnapshot.formatTps(snapshot.tps1()) + "."));
        } else if (snapshot.tps1() <= lowTpsWarning) {
            alerts.add(new HealthAlert("warning", "Low TPS", "1m TPS is " + MetricsSnapshot.formatTps(snapshot.tps1()) + "."));
        }

        if (snapshot.mspt() >= highMsptCritical) {
            alerts.add(new HealthAlert("critical", "High MSPT", String.format(Locale.ROOT, "Average tick time is %.1f ms.", snapshot.mspt())));
        } else if (snapshot.mspt() >= highMsptWarning) {
            alerts.add(new HealthAlert("warning", "High MSPT", String.format(Locale.ROOT, "Average tick time is %.1f ms.", snapshot.mspt())));
        }

        if (memoryPercent >= memoryCritical) {
            alerts.add(new HealthAlert("critical", "Memory pressure", String.format(Locale.ROOT, "%.1f%% of max heap is in use.", memoryPercent)));
        } else if (memoryPercent >= memoryWarning) {
            alerts.add(new HealthAlert("warning", "Memory pressure", String.format(Locale.ROOT, "%.1f%% of max heap is in use.", memoryPercent)));
        }

        for (final WorldPanelStats world : worlds) {
            if (world.loadedChunks() >= chunkWarning) {
                alerts.add(new HealthAlert("warning", "World chunks", world.name() + " has " + world.loadedChunks() + " loaded chunks."));
            }
            if (world.entities() >= entityWarning) {
                alerts.add(new HealthAlert("warning", "World entities", world.name() + " has " + world.entities() + " entities."));
            }
        }

        if (this.preferences.booleanValue("modules.web-panel.health.disabled-plugins-warning", true)) {
            final List<String> disabled = new ArrayList<>();
            for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                if (!plugin.isEnabled()) {
                    disabled.add(plugin.getName());
                }
            }
            if (!disabled.isEmpty()) {
                final String detail = detailed
                    ? String.join(", ", disabled)
                    : disabled.size() + " plugin(s) are disabled.";
                alerts.add(new HealthAlert("warning", "Disabled plugins", detail));
            }
        }
        return alerts;
    }

    private static double memoryUsagePercent(final MetricsSnapshot snapshot) {
        if (snapshot.maxMemory() <= 0L) {
            return 0.0D;
        }
        return Math.min(100.0D, snapshot.usedMemory() * 100.0D / snapshot.maxMemory());
    }

    private String modulesJson() {
        final StringBuilder json = new StringBuilder(1024);
        json.append('[');
        boolean firstModule = true;
        for (final String module : MODULES) {
            if (!firstModule) {
                json.append(',');
            }
            firstModule = false;
            json.append('{');
            field(json, "name", module).append(',');
            booleanField(json, "enabled", this.preferences.moduleEnabled(module)).append(',');
            booleanField(json, "toggleable", !module.equals("web-panel")).append(',');
            json.append("\"commands\":[");
            boolean firstCommand = true;
            for (final String command : MODULE_COMMANDS.getOrDefault(module, List.of())) {
                if (!firstCommand) {
                    json.append(',');
                }
                firstCommand = false;
                json.append('{');
                field(json, "name", command).append(',');
                booleanField(json, "enabled", this.preferences.commandEnabled(module, command));
                json.append('}');
            }
            json.append("]}");
        }
        json.append(']');
        return json.toString();
    }

    private String webUsersJson() {
        final StringBuilder json = new StringBuilder(1024);
        json.append('[');
        boolean first = true;
        for (final String id : this.preferences.webUserIds()) {
            final HunterToolsPreferences.WebUser user = this.preferences.webUser(id);
            if (user == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", user.id()).append(',');
            field(json, "displayName", user.displayName()).append(',');
            field(json, "role", normalizeRole(user.role())).append(',');
            booleanField(json, "admin", normalizeRole(user.role()).equals("admin")).append(',');
            booleanField(json, "passwordConfigured", user.passwordConfigured()).append(',');
            booleanField(json, "commandExecution", user.commandExecution()).append(',');
            booleanField(json, "allowedCommandsConfigured", user.allowedCommandsConfigured()).append(',');
            json.append("\"allowedCommands\":").append(stringArrayJson(user.allowedCommands()));
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String pluginsJson() {
        final Map<String, PluginJarScan> scannedJars = this.pluginJarScanLookup();
        final Map<String, Plugin> loadedPlugins = new HashMap<>();
        for (final Plugin installedPlugin : Bukkit.getPluginManager().getPlugins()) {
            loadedPlugins.put(installedPlugin.getName().toLowerCase(Locale.ROOT), installedPlugin);
        }

        final StringBuilder json = new StringBuilder(2048);
        json.append('[');
        boolean first = true;
        for (final Plugin installedPlugin : Bukkit.getPluginManager().getPlugins()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            final PluginJarScan scan = scannedJars.get(installedPlugin.getName().toLowerCase(Locale.ROOT));
            this.appendPluginJson(json, installedPlugin, scan);
        }

        final List<PluginJarScan> unloaded = new ArrayList<>();
        for (final PluginJarScan scan : scannedJars.values()) {
            if (!loadedPlugins.containsKey(scan.metadata().name().toLowerCase(Locale.ROOT))) {
                unloaded.add(scan);
            }
        }
        unloaded.sort((left, right) -> left.metadata().name().compareToIgnoreCase(right.metadata().name()));
        for (final PluginJarScan scan : unloaded) {
            if (!first) {
                json.append(',');
            }
            first = false;
            this.appendPluginJson(json, null, scan);
        }
        json.append(']');
        return json.toString();
    }

    private void appendPluginJson(final StringBuilder json, final Plugin installedPlugin, final PluginJarScan scan) {
        final String name = installedPlugin == null ? scan.metadata().name() : installedPlugin.getName();
        final String version = installedPlugin == null ? scan.metadata().version() : installedPlugin.getPluginMeta().getVersion();
        final boolean loaded = installedPlugin != null;
        final boolean enabled = installedPlugin != null && installedPlugin.isEnabled();
        final boolean protectedPlugin = this.protectedPlugin(name);
        final PluginDescriptionFile description = installedPlugin == null ? null : installedPlugin.getDescription();
        final long fileSize = scan == null ? -1L : safeFileSize(scan.path());
        final String risk = protectedPlugin ? "protected" : !loaded ? "restart" : "runtime";
        json.append('{');
        field(json, "name", name).append(',');
        field(json, "version", version).append(',');
        field(json, "sourceJar", scan == null ? "" : scan.path().getFileName().toString()).append(',');
        field(json, "descriptor", scan == null ? "" : scan.metadata().descriptor()).append(',');
        field(json, "main", scan == null ? (description == null ? "" : description.getMain()) : scan.metadata().main()).append(',');
        field(json, "description", description == null ? "" : description.getDescription()).append(',');
        field(json, "website", description == null ? "" : description.getWebsite()).append(',');
        field(json, "apiVersion", description == null ? "" : description.getAPIVersion()).append(',');
        field(json, "risk", risk).append(',');
        field(json, "status", loaded ? (enabled ? "enabled" : "disabled") : "installed").append(',');
        booleanField(json, "loaded", loaded).append(',');
        booleanField(json, "enabled", enabled).append(',');
        booleanField(json, "controllable", !protectedPlugin).append(',');
        booleanField(json, "updateable", !protectedPlugin).append(',');
        numberField(json, "fileSizeBytes", fileSize).append(',');
        json.append("\"authors\":").append(stringArrayJson(description == null ? List.of() : description.getAuthors())).append(',');
        json.append("\"dependencies\":").append(stringArrayJson(description == null ? List.of() : description.getDepend())).append(',');
        json.append("\"softDependencies\":").append(stringArrayJson(description == null ? List.of() : description.getSoftDepend()));
        json.append('}');
    }

    private static long safeFileSize(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException ignored) {
            return -1L;
        }
    }

    private String actorDetailsJson() {
        final StringBuilder json = new StringBuilder(1024);
        json.append('[');
        boolean first = true;
        for (final String module : List.of("fake-players", "npcs")) {
            for (final HunterActorManager.ActorView actor : this.plugin.actorViews(module)) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append('{');
                field(json, "module", actor.module()).append(',');
                field(json, "id", actor.id()).append(',');
                field(json, "displayName", actor.displayName()).append(',');
                field(json, "kind", actor.kind()).append(',');
                field(json, "world", actor.world()).append(',');
                numberField(json, "x", actor.x()).append(',');
                numberField(json, "y", actor.y()).append(',');
                numberField(json, "z", actor.z()).append(',');
                numberField(json, "yaw", actor.yaw()).append(',');
                numberField(json, "pitch", actor.pitch()).append(',');
                field(json, "pose", actor.pose()).append(',');
                field(json, "clickCommand", actor.clickCommand()).append(',');
                booleanField(json, "aiEnabled", actor.aiEnabled()).append(',');
                field(json, "aiPersona", actor.aiPersona()).append(',');
                booleanField(json, "live", actor.live()).append(',');
                field(json, "entityUuid", actor.entityUuid());
                json.append('}');
            }
        }
        for (final HunterRealFakePlayerManager.RealFakePlayerView actor : this.plugin.realFakePlayerViews()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "module", actor.module()).append(',');
            field(json, "id", actor.id()).append(',');
            field(json, "displayName", actor.displayName()).append(',');
            field(json, "kind", "server-player").append(',');
            field(json, "world", actor.world()).append(',');
            numberField(json, "x", actor.x()).append(',');
            numberField(json, "y", actor.y()).append(',');
            numberField(json, "z", actor.z()).append(',');
            numberField(json, "yaw", actor.yaw()).append(',');
            numberField(json, "pitch", actor.pitch()).append(',');
            field(json, "pose", actor.gameMode()).append(',');
            field(json, "loops", actor.loops()).append(',');
            field(json, "clickCommand", actor.clickCommand()).append(',');
            booleanField(json, "aiEnabled", actor.aiEnabled()).append(',');
            field(json, "aiPersona", actor.aiPersona()).append(',');
            field(json, "aiStatus", actor.aiStatus()).append(',');
            booleanField(json, "aiFree", actor.aiFree()).append(',');
            booleanField(json, "live", true).append(',');
            field(json, "entityUuid", actor.entityUuid());
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String webSettingsJson() {
        final StringBuilder json = new StringBuilder(256);
        final String cpuMode = normalizeCpuMode(this.preferences.stringValue("optimizations.cpu.mode", "single-thread"));
        json.append('{');
        field(json, "bindAddress", this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1")).append(',');
        numberField(json, "port", Math.max(1, Math.min(65535, this.preferences.intValue("modules.web-panel.port", 8088)))).append(',');
        field(json, "externalUrl", this.preferences.stringValue("modules.web-panel.external-url", "")).append(',');
        booleanField(json, "publicMap", this.preferences.booleanValue("modules.web-panel.public-map", true)).append(',');
        field(json, "mapUrl", this.preferences.stringValue("modules.web-panel.map-url", "http://%host%:8100/")).append(',');
        field(json, "serverName", this.webServerName()).append(',');
        field(json, "f3ServerName", this.preferences.stringValue("modules.management.f3-server-name", "\"HunterCraft\" Server")).append(',');
        field(json, "cpuMode", cpuMode).append(',');
        booleanField(json, "tpsDisplayEnabled", this.preferences.moduleEnabled("tps-display")).append(',');
        booleanField(json, "tpsActionbar", this.preferences.booleanValue("modules.tps-display.actionbar", true)).append(',');
        numberField(json, "tpsIntervalTicks", this.preferences.intValue("modules.tps-display.interval-ticks", 40)).append(',');
        field(json, "tpsActionbarFormat", this.preferences.stringValue("modules.tps-display.actionbar-format", "&7TPS %tps_color%%tps% &8| &7MSPT &f%mspt% &8| &7Players &f%online%/%max%")).append(',');
        booleanField(json, "sidebarEnabled", this.preferences.moduleEnabled("sidebar")).append(',');
        field(json, "sidebarTitle", this.preferences.stringValue("modules.sidebar.title", "&6HunterCore")).append(',');
        json.append("\"sidebarLines\":").append(stringArrayJson(this.preferences.stringList("modules.sidebar.lines", HunterToolsPreferences.defaultSidebarLines()))).append(',');
        numberField(json, "sidebarIntervalTicks", this.preferences.intValue("modules.sidebar.interval-ticks", 40)).append(',');
        booleanField(json, "sidebarDirtyUpdatesOnly", this.preferences.booleanValue("modules.sidebar.dirty-updates-only", true)).append(',');
        booleanField(json, "motdEnabled", this.preferences.moduleEnabled("motd")).append(',');
        field(json, "motdLine1", this.preferences.stringValue("modules.motd.line-1", "&b\"HunterCraft\" Server &8| &fHunterCore")).append(',');
        field(json, "motdLine2", this.preferences.stringValue("modules.motd.line-2", "&7%online%/%max% players &8- &aTPS %tps% &8- &e%version%")).append(',');
        numberField(json, "motdMaxPlayers", this.preferences.intValue("modules.motd.max-players", -1)).append(',');
        booleanField(json, "authEnabled", this.preferences.booleanValue("modules.auth.enabled", true)).append(',');
        booleanField(json, "authRegistrationRequired", this.preferences.booleanValue("modules.auth.registration-required", true)).append(',');
        booleanField(json, "authWebRegistrationRequired", this.preferences.booleanValue("modules.auth.web-registration-required", false)).append(',');
        booleanField(json, "authWebRegistrationEnabled", this.preferences.booleanValue("modules.auth.web-registration-enabled", false)).append(',');
        booleanField(json, "authWebLoginEnabled", this.preferences.booleanValue("modules.auth.web-login-enabled", false)).append(',');
        booleanField(json, "authGuiEnabled", this.preferences.booleanValue("modules.auth.gui-enabled", true)).append(',');
        booleanField(json, "authOpenGuiOnJoin", this.preferences.booleanValue("modules.auth.open-gui-on-join", true)).append(',');
        numberField(json, "authMinimumPasswordLength", this.preferences.intValue("modules.auth.minimum-password-length", 4)).append(',');
        numberField(json, "authLoginTimeoutSeconds", this.preferences.intValue("modules.auth.login-timeout-seconds", 90)).append(',');
        numberField(json, "authMaxLoginAttempts", this.preferences.intValue("modules.auth.max-login-attempts", 5)).append(',');
        numberField(json, "authLockoutSeconds", this.preferences.intValue("modules.auth.lockout-seconds", 60)).append(',');
        field(json, "authRegistrationUrl", this.preferences.stringValue("modules.auth.registration-url", "")).append(',');
        booleanField(json, "corsEnabled", this.preferences.booleanValue("modules.web-panel.cors-enabled", true)).append(',');
        field(json, "corsAllowOrigin", this.preferences.stringValue("modules.web-panel.cors-allow-origin", "*")).append(',');
        booleanField(json, "apiKeyEnabled", this.preferences.booleanValue("modules.web-panel.api-key-enabled", false)).append(',');
        booleanField(json, "apiKeyConfigured", !this.preferences.stringValue("modules.web-panel.api-key", "").isBlank()).append(',');
        booleanField(json, "asyncEnabled", !cpuMode.equals("single-thread")).append(',');
        numberField(json, "recommendedWorkers", this.preferences.defaultWorkerCount()).append(',');
        field(json, "address", this.addressLine());
        json.append('}');
        return json.toString();
    }

    private String aiApprovalsJson() {
        final StringBuilder json = new StringBuilder(256);
        json.append('[');
        boolean first = true;
        for (final HunterRealFakePlayerManager.PendingRiskApprovalView approval : this.plugin.pendingRiskApprovalViews()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", approval.id()).append(',');
            field(json, "fakePlayerName", approval.fakePlayerName()).append(',');
            field(json, "label", approval.label()).append(',');
            field(json, "detail", approval.detail()).append(',');
            field(json, "requestedBy", approval.requestedBy()).append(',');
            numberField(json, "expiresInSeconds", approval.expiresInSeconds());
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String commandMessagesJson() {
        final StringBuilder json = new StringBuilder(512);
        json.append('{');
        json.append("\"about\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.about",
            HunterToolsPreferences.defaultCommandOverrideLines("about")
        ))).append(',');
        json.append("\"plugins\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.plugins",
            HunterToolsPreferences.defaultCommandOverrideLines("plugins")
        ))).append(',');
        json.append("\"version\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.version",
            HunterToolsPreferences.defaultCommandOverrideLines("version")
        ))).append(',');
        json.append("\"rules\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.rules",
            HunterToolsPreferences.defaultCommandOverrideLines("rules")
        ))).append(',');
        json.append("\"discord\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.discord",
            HunterToolsPreferences.defaultCommandOverrideLines("discord")
        ))).append(',');
        json.append("\"website\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.website",
            HunterToolsPreferences.defaultCommandOverrideLines("website")
        ))).append(',');
        json.append("\"motd\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.motd",
            HunterToolsPreferences.defaultCommandOverrideLines("motd")
        ))).append(',');
        json.append("\"info\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.info",
            HunterToolsPreferences.defaultCommandOverrideLines("info")
        ))).append(',');
        json.append("\"server\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.server",
            HunterToolsPreferences.defaultCommandOverrideLines("server")
        ))).append(',');
        json.append("\"links\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.links",
            HunterToolsPreferences.defaultCommandOverrideLines("links")
        ))).append(',');
        json.append("\"qq\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.qq",
            HunterToolsPreferences.defaultCommandOverrideLines("qq")
        ))).append(',');
        json.append("\"group\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.group",
            HunterToolsPreferences.defaultCommandOverrideLines("group")
        ))).append(',');
        json.append("\"opDenied\":").append(stringArrayJson(this.preferences.stringList(
            "modules.command-overrides.messages.op-denied",
            HunterToolsPreferences.defaultCommandOverrideLines("op-denied")
        )));
        json.append('}');
        return json.toString();
    }

    private String aiSettingsJson() {
        final StringBuilder json = new StringBuilder(1536);
        json.append('{');
        booleanField(json, "enabled", this.preferences.moduleEnabled("ai")).append(',');
        field(json, "provider", this.preferences.stringValue("modules.ai.provider", "openai-compatible")).append(',');
        field(json, "baseUrl", this.preferences.stringValue("modules.ai.base-url", "https://api.openai.com/v1")).append(',');
        field(json, "model", this.preferences.stringValue("modules.ai.model", "gpt-4o-mini")).append(',');
        booleanField(json, "apiKeyConfigured", this.plugin.aiApiKeyConfigured()).append(',');
        field(json, "apiKeyEnv", this.preferences.stringValue("modules.ai.api-key-env", "OPENAI_API_KEY")).append(',');
        numberField(json, "temperature", this.preferences.doubleValue("modules.ai.temperature", 0.7D)).append(',');
        numberField(json, "maxTokens", this.preferences.intValue("modules.ai.max-tokens", 300)).append(',');
        numberField(json, "timeoutSeconds", this.preferences.intValue("modules.ai.timeout-seconds", 30)).append(',');
        booleanField(json, "chatEnabled", this.preferences.booleanValue("modules.ai.chat.enabled", true)).append(',');
        field(json, "chatTriggerPrefix", this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai")).append(',');
        numberField(json, "chatCooldownSeconds", this.preferences.intValue("modules.ai.chat.cooldown-seconds", 5)).append(',');
        booleanField(json, "chatBroadcast", this.preferences.booleanValue("modules.ai.chat.broadcast", true)).append(',');
        field(json, "chatSystemPrompt", this.preferences.stringValue("modules.ai.chat.system-prompt", "")).append(',');
        json.append("\"chatProfiles\":").append(aiChatProfilesJson()).append(',');
        booleanField(json, "npcEnabled", this.preferences.booleanValue("modules.ai.npc.enabled", true)).append(',');
        numberField(json, "npcCooldownSeconds", this.preferences.intValue("modules.ai.npc.cooldown-seconds", 5)).append(',');
        numberField(json, "npcResponseRadiusBlocks", this.preferences.intValue("modules.ai.npc.response-radius-blocks", 16)).append(',');
        booleanField(json, "npcAllowActions", this.preferences.booleanValue("modules.ai.npc.allow-actions", true)).append(',');
        field(json, "npcSystemPrompt", this.preferences.stringValue("modules.ai.npc.system-prompt", "")).append(',');
        json.append("\"npcCommandWhitelist\":").append(stringArrayJson(this.preferences.stringList(
            "modules.ai.npc.command-whitelist",
            List.of("say", "tell", "msg", "title", "effect", "playsound")
        ))).append(',');
        booleanField(json, "fakePlayersEnabled", this.preferences.booleanValue("modules.ai.fake-players.enabled", true)).append(',');
        numberField(json, "fakePlayersIntervalSeconds", this.preferences.intValue("modules.ai.fake-players.interval-seconds", 6)).append(',');
        numberField(json, "fakePlayersMaxActions", this.preferences.intValue("modules.ai.fake-players.max-actions", 5)).append(',');
        numberField(json, "fakePlayersMaxMoveTicks", this.preferences.intValue("modules.ai.fake-players.max-move-ticks", 40)).append(',');
        numberField(json, "fakePlayersMaxActionTicks", this.preferences.intValue("modules.ai.fake-players.max-action-ticks", 80)).append(',');
        numberField(json, "fakePlayersNearbyRadiusBlocks", this.preferences.intValue("modules.ai.fake-players.nearby-radius-blocks", 6)).append(',');
        booleanField(json, "fakePlayersAllowMovement", this.preferences.booleanValue("modules.ai.fake-players.allow-movement", true)).append(',');
        booleanField(json, "fakePlayersAllowBreaking", this.preferences.booleanValue("modules.ai.fake-players.allow-breaking", true)).append(',');
        booleanField(json, "fakePlayersAllowPlacing", this.preferences.booleanValue("modules.ai.fake-players.allow-placing", true)).append(',');
        booleanField(json, "fakePlayersAllowInteraction", this.preferences.booleanValue("modules.ai.fake-players.allow-interaction", true)).append(',');
        numberField(json, "fakePlayersMaxPlaceDistanceBlocks", this.preferences.intValue("modules.ai.fake-players.max-place-distance-blocks", 6)).append(',');
        field(json, "fakePlayersQuickResponseMode", this.preferences.stringValue("modules.ai.fake-players.quick-response.mode", "off")).append(',');
        booleanField(json, "fakePlayersChatControlEnabled", this.preferences.booleanValue("modules.ai.fake-players.chat-control.enabled", true)).append(',');
        booleanField(json, "fakePlayersChatControlAmbientEnabled", this.preferences.booleanValue("modules.ai.fake-players.chat-control.ambient-enabled", true)).append(',');
        field(json, "fakePlayersChatControlPrefix", this.preferences.stringValue("modules.ai.fake-players.chat-control.trigger-prefix", "@bot")).append(',');
        numberField(json, "fakePlayersChatControlCooldownSeconds", this.preferences.intValue("modules.ai.fake-players.chat-control.cooldown-seconds", 3)).append(',');
        booleanField(json, "fakePlayersChatControlRequirePermission", this.preferences.booleanValue("modules.ai.fake-players.chat-control.require-permission", false)).append(',');
        field(json, "fakePlayersChatControlPermission", this.preferences.stringValue("modules.ai.fake-players.chat-control.permission", "huntertools.ai.fakeplayer")).append(',');
        field(json, "fakePlayersSystemPrompt", this.preferences.stringValue("modules.ai.fake-players.system-prompt", "")).append(',');
        json.append("\"fakeBotAliases\":").append(fakeBotAliasesJson()).append(',');
        json.append("\"fakePlayerPersonas\":").append(fakeAiPersonaProfilesJson()).append(',');
        final HunterToolsPreferences.StoryModeConfig story = this.preferences.storyModeConfig();
        booleanField(json, "storyModeEnabled", story.enabled()).append(',');
        field(json, "storyMainPlayerName", story.mainPlayerName()).append(',');
        field(json, "storyAiName", story.aiName()).append(',');
        field(json, "storyAiSkin", story.aiSkin()).append(',');
        field(json, "storyDialogueMode", story.dialogueMode()).append(',');
        field(json, "storyMeltdownKickCommand", story.meltdownKickCommand()).append(',');
        field(json, "storyMeltdownDestroyGoal", story.meltdownDestroyGoal()).append(',');
        json.append("\"storyPhases\":").append(storyPhasesJson(story));
        json.append('}');
        return json.toString();
    }

    private String storyPhasesJson(final HunterToolsPreferences.StoryModeConfig story) {
        final StringBuilder json = new StringBuilder(1024);
        json.append('[');
        boolean first = true;
        for (final HunterToolsPreferences.StoryPhaseConfig phase : story.phases()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", phase.id()).append(',');
            numberField(json, "durationSeconds", phase.durationSeconds()).append(',');
            field(json, "systemPrompt", phase.systemPrompt()).append(',');
            json.append("\"playerLines\":").append(stringArrayJson(phase.playerLines())).append(',');
            json.append("\"allowedActions\":").append(stringArrayJson(phase.allowedActions())).append(',');
            booleanField(json, "allowAiFree", phase.allowAiFree());
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String aiChatProfilesJson() {
        final StringBuilder json = new StringBuilder(512);
        json.append('[');
        boolean first = true;
        for (final HunterToolsPreferences.AiChatProfile profile : this.preferences.aiChatProfiles()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", profile.id()).append(',');
            booleanField(json, "enabled", profile.enabled()).append(',');
            field(json, "displayName", profile.displayName()).append(',');
            json.append("\"aliases\":").append(stringArrayJson(profile.aliases())).append(',');
            field(json, "systemPrompt", profile.systemPrompt()).append(',');
            field(json, "responseFormat", profile.responseFormat());
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String fakeBotAliasesJson() {
        final StringBuilder json = new StringBuilder(512);
        json.append('[');
        boolean first = true;
        for (final HunterToolsPreferences.FakeBotAlias alias : this.preferences.fakeBotAliases()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", alias.id()).append(',');
            booleanField(json, "enabled", alias.enabled()).append(',');
            field(json, "target", alias.target()).append(',');
            json.append("\"aliases\":").append(stringArrayJson(alias.aliases()));
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String fakeAiPersonaProfilesJson() {
        final StringBuilder json = new StringBuilder(768);
        json.append('[');
        boolean first = true;
        for (final HunterToolsPreferences.FakeAiPersonaProfile profile : this.preferences.fakeAiPersonaProfiles()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", profile.id()).append(',');
            booleanField(json, "enabled", profile.enabled()).append(',');
            field(json, "displayName", profile.displayName()).append(',');
            json.append("\"aliases\":").append(stringArrayJson(profile.aliases())).append(',');
            field(json, "systemPrompt", profile.systemPrompt()).append(',');
            field(json, "defaultGoal", profile.defaultGoal());
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }

    private String mapJson(final HttpExchange exchange) {
        final boolean publicMap = this.preferences.booleanValue("modules.web-panel.public-map", true);
        if (!publicMap && this.session(exchange) == null) {
            return "{\"ok\":false,\"error\":\"login_required\"}";
        }
        final String rawUrl = this.preferences.stringValue("modules.web-panel.map-url", "http://%host%:8100/");
        final String host = requestHost(exchange);
        final String mapUrl = rawUrl.replace("%host%", host);
        return "{\"ok\":true,\"public\":" + publicMap + ",\"url\":\"" + escapeJson(mapUrl) + "\"}";
    }

    private String webServerName() {
        final String configured = this.preferences.stringValue("modules.web-panel.server-name", "HunterCore").trim();
        return configured.isBlank() ? "HunterCore" : configured;
    }

    private String registrationUrl() {
        final String configured = this.preferences.stringValue("modules.auth.registration-url", "").trim();
        if (!configured.isBlank()) {
            return configured;
        }
        final String external = this.preferences.stringValue("modules.web-panel.external-url", "").trim();
        final String base = external.isBlank() ? this.addressLine() : external;
        return base.endsWith("/") ? base + "#register" : base + "/#register";
    }

    private String sessionJson(final HttpExchange exchange) {
        final WebSession session = this.session(exchange);
        return "{\"ok\":true,\"session\":" + (session == null ? "null" : sessionJson(session)) + "}";
    }

    private String chatJson() {
        final List<WebChatLine> copy;
        synchronized (this.chatLines) {
            copy = new ArrayList<>(this.chatLines);
        }
        final StringBuilder json = new StringBuilder(2048);
        json.append("{\"ok\":true,\"lines\":[");
        boolean first = true;
        for (final WebChatLine line : copy) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            numberField(json, "time", line.time()).append(',');
            field(json, "sender", line.sender()).append(',');
            field(json, "source", line.source()).append(',');
            field(json, "message", line.message());
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    private void login(final HttpExchange exchange) throws IOException {
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String username = body.getOrDefault("username", "");
        final String password = body.getOrDefault("password", "");
        final HunterToolsPreferences.WebUser user = this.preferences.webUser(username);
        final boolean webPassword = user != null && user.passwordConfigured() && verifyPassword(password, user.passwordHash());
        final boolean hunterAuthPassword = this.preferences.booleanValue("modules.auth.web-login-enabled", false) && verifyHunterAuthPassword(username, password);
        if (!webPassword && !hunterAuthPassword) {
            this.send(exchange, 401, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_login\"}");
            return;
        }
        final boolean operator = this.operatorUsername(username);
        final String role = operator ? "admin" : webPassword && user != null ? normalizeRole(user.role()) : "player";
        final String id = webPassword && user != null ? user.id() : HunterToolsPreferences.webUserId(username);
        final String displayName = webPassword && user != null ? user.displayName() : username;
        final boolean commandExecution = webPassword && user != null ? user.commandExecution() : true;
        final boolean allowedCommandsConfigured = webPassword && user != null && user.allowedCommandsConfigured();
        final List<String> allowedCommands = webPassword && user != null ? List.copyOf(user.allowedCommands()) : List.of();
        final long minutes = Math.max(5L, this.preferences.intValue("modules.web-panel.session-minutes", 360));
        final WebSession session = new WebSession(
            this.newToken(),
            this.newToken(),
            id,
            displayName,
            role,
            operator ? (webPassword ? "web-op" : "hunterauth-op") : webPassword ? "web" : "hunterauth",
            operator || commandExecution,
            allowedCommandsConfigured,
            allowedCommands,
            Instant.now().plusSeconds(minutes * 60L).toEpochMilli()
        );
        this.sessions.put(session.token(), session);
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + session.token() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + (minutes * 60L));
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"session\":" + sessionJson(session) + "}");
    }

    private boolean operatorUsername(final String username) {
        final String id = HunterToolsPreferences.webUserId(username);
        if (id.isBlank()) {
            return false;
        }
        try {
            if (Bukkit.isPrimaryThread()) {
                return this.operatorUsernameMain(id);
            }
            return Bukkit.getScheduler()
                .callSyncMethod(this.plugin, () -> this.operatorUsernameMain(id))
                .get(3L, TimeUnit.SECONDS);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (final ExecutionException | TimeoutException ex) {
            this.plugin.getLogger().warning("HunterCore web panel could not check OP role for " + username + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean operatorUsernameMain(final String id) {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (HunterToolsPreferences.webUserId(player.getName()).equals(id) && player.isOp()) {
                return true;
            }
        }
        for (final OfflinePlayer operator : Bukkit.getOperators()) {
            final String name = operator.getName();
            if (name != null && HunterToolsPreferences.webUserId(name).equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void authRegister(final HttpExchange exchange) throws IOException {
        if (!this.preferences.booleanValue("modules.auth.enabled", true)
            || !this.preferences.booleanValue("modules.auth.registration-required", true)) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"auth_registration_disabled\"}");
            return;
        }
        if (!this.preferences.booleanValue("modules.auth.web-registration-enabled", false)) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"web_registration_closed\"}");
            return;
        }

        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String username = body.getOrDefault("username", "").trim();
        final String password = body.getOrDefault("password", "");
        final String confirmPassword = body.getOrDefault("confirmPassword", body.getOrDefault("passwordConfirm", ""));
        final int minimumLength = Math.max(1, this.preferences.intValue("modules.auth.minimum-password-length", 4));
        if (!validMinecraftUsername(username) || password.length() < minimumLength || password.length() > 256
            || (!confirmPassword.isBlank() && !password.equals(confirmPassword))) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_registration\"}");
            return;
        }

        final UUID uuid = offlineUuid(username);
        final Path usersPath = hunterAuthUsersPath();
        synchronized (this.hunterAuthUsersLock) {
            Files.createDirectories(usersPath.getParent());
            final YamlConfiguration users = Files.isRegularFile(usersPath)
                ? YamlConfiguration.loadConfiguration(usersPath.toFile())
                : new YamlConfiguration();
            if (users.contains("users." + uuid + ".hash") || hunterAuthNameExists(users, username)) {
                this.send(exchange, 409, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"already_registered\"}");
                return;
            }
            final byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            final String path = "users." + uuid;
            users.set(path + ".name", username);
            users.set(path + ".salt", Base64.getEncoder().encodeToString(salt));
            users.set(path + ".hash", hunterAuthHash(password, salt));
            users.set(path + ".registered-from", "web-panel");
            users.set(path + ".registered-at", Instant.now().toString());
            users.save(usersPath.toFile());
        }

        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"username\":\"" + escapeJson(username) + "\"}");
    }

    private void logout(final HttpExchange exchange) {
        final WebSession session = this.session(exchange);
        if (session != null) {
            if (!this.csrfAllowed(exchange, session)) {
                this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"csrf_required\"}");
                return;
            }
            this.sessions.remove(session.token());
        }
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}");
    }

    private void chatSend(final HttpExchange exchange) throws IOException {
        final WebSession session = this.session(exchange);
        if (session == null) {
            this.send(exchange, 401, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"login_required\"}");
            return;
        }
        if (!this.csrfAllowed(exchange, session)) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"csrf_required\"}");
            return;
        }
        if (!session.commandExecution()) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"chat_denied\"}");
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 8 * 1024));
        final String message = plainChatMessage(body.getOrDefault("message", ""));
        if (message.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"empty_message\"}");
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> Bukkit.broadcastMessage(ChatColor.AQUA + "[Web] " + ChatColor.WHITE + session.displayName() + ChatColor.GRAY + ": " + ChatColor.WHITE + message));
        this.plugin.observeWebChat(session.displayName(), message);
        this.addChatLine(session.displayName(), "web", message);
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"chat\":" + this.chatJson() + "}");
    }

    private void command(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.session(exchange);
        if (session == null) {
            this.send(exchange, 401, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"login_required\"}");
            return;
        }
        if (!this.csrfAllowed(exchange, session)) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"csrf_required\"}");
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String command = body.getOrDefault("command", "").replaceFirst("^/+", "").trim();
        if (command.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"empty_command\"}");
            return;
        }
        if (!this.commandAllowed(session, command)) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"command_denied\"}");
            return;
        }

        this.sendCommandResult(exchange, 200, this.dispatchConfiguredCommand(command));
    }

    private void adminModule(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String module = HunterToolsPreferences.normalize(body.getOrDefault("module", ""));
        final Boolean enabled = parseBoolean(body.get("enabled"));
        if (!MODULES.contains(module)) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_module\"}");
            return;
        }
        if (module.equals("web-panel")) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"self_protection\"}");
            return;
        }
        if (enabled == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_enabled\"}");
            return;
        }
        final CommandResult result = this.dispatchConfiguredCommand("hc admin module " + module + " " + onOff(enabled));
        this.invalidateStatusCaches();
        this.sendCommandResult(exchange, 200, result);
    }

    private void adminCommand(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String module = HunterToolsPreferences.normalize(body.getOrDefault("module", ""));
        final String command = HunterToolsPreferences.normalize(body.getOrDefault("command", ""));
        final Boolean enabled = parseBoolean(body.get("enabled"));
        final List<String> commands = MODULE_COMMANDS.get(module);
        if (commands == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_command_module\"}");
            return;
        }
        if (!commands.contains(command)) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_command\"}");
            return;
        }
        if (enabled == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_enabled\"}");
            return;
        }
        final CommandResult result = this.dispatchConfiguredCommand("hc admin command " + module + " " + command + " " + onOff(enabled));
        this.invalidateStatusCaches();
        this.sendCommandResult(exchange, 200, result);
    }

    private void adminActorSpawn(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String module = HunterToolsPreferences.normalize(body.getOrDefault("module", "npcs"));
        final String label = actorCommandLabel(module);
        if (label == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_actor_module\"}");
            return;
        }
        final WebSession session = this.adminOperator(exchange, label);
        if (session == null) {
            return;
        }
        final String name = commandToken(body.getOrDefault("name", ""), 32);
        if (name.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_actor_name\"}");
            return;
        }
        String kind = HunterToolsPreferences.normalize(body.getOrDefault("kind", "villager"));
        if (module.equals("fake-players") || module.equals("real-fake-players")) {
            kind = "mannequin";
        } else if (!kind.equals("villager") && !kind.equals("mannequin")) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_actor_kind\"}");
            return;
        }

        final StringBuilder command = new StringBuilder(label).append(" spawn ").append(name);
        if (module.equals("npcs")) {
            command.append(' ').append(kind);
        }
        final String location = actorLocationArguments(body);
        if (location == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_actor_location\"}");
            return;
        }
        if (!location.isBlank()) {
            command.append(' ').append(location);
        }
        final CommandResult result = this.dispatchConfiguredCommand(command.toString());
        this.invalidateStatusCaches();
        this.sendCommandResult(exchange, 200, result);
    }

    private void adminActorRemove(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String module = HunterToolsPreferences.normalize(body.getOrDefault("module", ""));
        final String label = actorCommandLabel(module);
        if (label == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_actor_module\"}");
            return;
        }
        final WebSession session = this.adminOperator(exchange, label);
        if (session == null) {
            return;
        }
        final String id = commandToken(body.getOrDefault("id", ""), 32);
        if (id.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_actor_id\"}");
            return;
        }
        final CommandResult result = this.dispatchConfiguredCommand(label + " remove " + id);
        this.invalidateStatusCaches();
        this.sendCommandResult(exchange, 200, result);
    }

    private void adminActorClickCommand(final HttpExchange exchange) throws IOException {
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String module = HunterToolsPreferences.normalize(body.getOrDefault("module", ""));
        final String label = actorCommandLabel(module);
        if (label == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_actor_module\"}");
            return;
        }
        final WebSession session = this.adminOperator(exchange, label);
        if (session == null) {
            return;
        }
        final String id = commandToken(body.getOrDefault("id", ""), 32);
        if (id.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_actor_id\"}");
            return;
        }
        final String command = actorClickCommand(body.getOrDefault("command", ""));
        if (command == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_click_command\"}");
            return;
        }
        if (!this.plugin.setActorClickCommand(module, id, command)) {
            this.send(exchange, 404, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"actor_not_found\"}");
            return;
        }
        this.invalidateStatusCaches();
        final StringBuilder json = new StringBuilder(96);
        json.append("{\"ok\":true,");
        field(json, "message", command.isBlank() ? "Actor click command cleared." : "Actor click command saved.");
        json.append('}');
        this.send(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void adminActorAi(final HttpExchange exchange) throws IOException {
        final Map<String, String> body = parseJsonObject(this.body(exchange, 24 * 1024));
        final String module = HunterToolsPreferences.normalize(body.getOrDefault("module", ""));
        if (!module.equals("npcs") && !module.equals("real-fake-players")) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"ai_only_supports_npcs_and_real_fake_players\"}");
            return;
        }
        final WebSession session = this.adminOperator(exchange, module.equals("npcs") ? "npc" : "player");
        if (session == null) {
            return;
        }
        final String id = commandToken(body.getOrDefault("id", ""), 32);
        final Boolean enabled = parseBoolean(body.getOrDefault("enabled", ""));
        final String persona = body.getOrDefault("persona", "").replace("\r\n", "\n").replace('\r', '\n').trim();
        if (id.isBlank() || enabled == null || persona.length() > 2048) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_actor_ai\"}");
            return;
        }
        if (!this.plugin.setActorAi(module, id, enabled, persona)) {
            this.send(exchange, 404, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"actor_not_found\"}");
            return;
        }
        this.invalidateStatusCaches();
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"message\":\"Actor AI settings saved.\"}");
    }

    private void adminWebUserSave(final HttpExchange exchange) throws IOException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String username = commandToken(body.getOrDefault("username", ""), 32);
        if (username.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_web_user\"}");
            return;
        }
        final String role = body.getOrDefault("role", "player").toLowerCase(Locale.ROOT);
        if (!role.equals("admin") && !role.equals("player")) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_role\"}");
            return;
        }
        final HunterToolsPreferences.WebUser existing = this.preferences.webUser(username);
        if (existing != null && existing.role().equals("admin") && role.equals("player") && this.lastAdmin(existing.id())) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"last_admin\"}");
            return;
        }
        final String password = body.getOrDefault("password", "").trim();
        if (existing == null && password.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"password_required\"}");
            return;
        }
        final String passwordHash = password.isBlank() ? existing.passwordHash() : hashPassword(password);
        final Boolean commandExecution = parseBoolean(body.getOrDefault("commandExecution", "true"));
        if (commandExecution == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_command_execution\"}");
            return;
        }
        final ParsedAllowedCommands allowedCommands = parseAllowedCommands(
            body.getOrDefault("allowedCommandsMode", "inherit"),
            body.getOrDefault("allowedCommands", "")
        );
        if (!allowedCommands.valid()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_allowed_commands\"}");
            return;
        }

        this.preferences.setWebUser(username, role, passwordHash);
        this.preferences.setWebUserCommandExecution(username, commandExecution);
        this.preferences.setWebUserAllowedCommands(username, allowedCommands.commands());
        this.savePreferences();
        this.expireWebUserSessions(username);
        this.invalidateStatusCaches();
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}");
    }

    private void adminWebUserRemove(final HttpExchange exchange) throws IOException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String username = commandToken(body.getOrDefault("username", ""), 32);
        if (username.isBlank() || this.preferences.webUser(username) == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_web_user\"}");
            return;
        }
        if (this.lastAdmin(HunterToolsPreferences.webUserId(username))) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"last_admin\"}");
            return;
        }
        this.preferences.removeWebUser(username);
        this.savePreferences();
        this.expireWebUserSessions(username);
        this.invalidateStatusCaches();
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true}");
    }

    private void adminLuckPerms(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange, "lp");
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String action = HunterToolsPreferences.normalize(body.getOrDefault("action", ""));
        final String target = commandToken(body.getOrDefault("target", ""), 64);
        final String group = commandToken(body.getOrDefault("group", ""), 64);
        final String permission = permissionToken(body.getOrDefault("permission", ""));
        final String value = body.getOrDefault("value", "true").equalsIgnoreCase("false") ? "false" : "true";
        final String command;
        switch (action) {
            case "user-permission-set" -> {
                if (target.isBlank() || permission.isBlank()) {
                    this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_luckperms_user_permission\"}");
                    return;
                }
                command = "lp user " + target + " permission set " + permission + " " + value;
            }
            case "user-permission-unset" -> {
                if (target.isBlank() || permission.isBlank()) {
                    this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_luckperms_user_permission\"}");
                    return;
                }
                command = "lp user " + target + " permission unset " + permission;
            }
            case "user-parent-set", "user-parent-add", "user-parent-remove" -> {
                if (target.isBlank() || group.isBlank()) {
                    this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_luckperms_user_parent\"}");
                    return;
                }
                final String operation = action.substring("user-parent-".length());
                command = "lp user " + target + " parent " + operation + " " + group;
            }
            case "group-permission-set" -> {
                if (group.isBlank() || permission.isBlank()) {
                    this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_luckperms_group_permission\"}");
                    return;
                }
                command = "lp group " + group + " permission set " + permission + " " + value;
            }
            case "group-permission-unset" -> {
                if (group.isBlank() || permission.isBlank()) {
                    this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_luckperms_group_permission\"}");
                    return;
                }
                command = "lp group " + group + " permission unset " + permission;
            }
            case "editor" -> command = "lp editor";
            case "sync" -> command = "lp sync";
            default -> {
                this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"unknown_luckperms_action\"}");
                return;
            }
        }
        this.sendCommandResult(exchange, 200, this.dispatchConfiguredCommand(command));
    }

    private void adminWebSettings(final HttpExchange exchange) throws IOException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String bindAddress = body.getOrDefault("bindAddress", this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1")).trim();
        final String rawPort = body.getOrDefault("port", String.valueOf(this.preferences.intValue("modules.web-panel.port", 8088))).trim();
        final String externalUrl = body.getOrDefault("externalUrl", this.preferences.stringValue("modules.web-panel.external-url", "")).trim();
        final String mapUrl = body.getOrDefault("mapUrl", this.preferences.stringValue("modules.web-panel.map-url", "http://%host%:8100/")).trim();
        final String serverName = body.getOrDefault("serverName", this.webServerName()).trim();
        final String f3ServerName = body.getOrDefault("f3ServerName", this.preferences.stringValue("modules.management.f3-server-name", "\"HunterCraft\" Server")).trim();
        final String cpuMode = normalizeCpuMode(body.getOrDefault("cpuMode", this.preferences.stringValue("optimizations.cpu.mode", "single-thread")));
        final Boolean publicMap = parseBoolean(body.getOrDefault("publicMap", String.valueOf(this.preferences.booleanValue("modules.web-panel.public-map", true))));
        final Boolean tpsDisplayEnabled = parseBoolean(body.getOrDefault("tpsDisplayEnabled", String.valueOf(this.preferences.moduleEnabled("tps-display"))));
        final Boolean tpsActionbar = parseBoolean(body.getOrDefault("tpsActionbar", String.valueOf(this.preferences.booleanValue("modules.tps-display.actionbar", true))));
        final Integer tpsIntervalTicks = parseInteger(body.getOrDefault("tpsIntervalTicks", String.valueOf(this.preferences.intValue("modules.tps-display.interval-ticks", 40))), 20, 1200);
        final String tpsActionbarFormat = body.getOrDefault("tpsActionbarFormat", this.preferences.stringValue("modules.tps-display.actionbar-format", "&7TPS %tps_color%%tps% &8| &7MSPT &f%mspt% &8| &7Players &f%online%/%max%")).trim();
        final Boolean sidebarEnabled = parseBoolean(body.getOrDefault("sidebarEnabled", String.valueOf(this.preferences.moduleEnabled("sidebar"))));
        final String sidebarTitle = body.getOrDefault("sidebarTitle", this.preferences.stringValue("modules.sidebar.title", "&6HunterCore")).trim();
        final List<String> sidebarLines = commandMessageLines(body.getOrDefault("sidebarLines", String.join("\n", this.preferences.stringList("modules.sidebar.lines", HunterToolsPreferences.defaultSidebarLines()))));
        final Integer sidebarIntervalTicks = parseInteger(body.getOrDefault("sidebarIntervalTicks", String.valueOf(this.preferences.intValue("modules.sidebar.interval-ticks", 40))), 20, 1200);
        final Boolean sidebarDirtyUpdatesOnly = parseBoolean(body.getOrDefault("sidebarDirtyUpdatesOnly", String.valueOf(this.preferences.booleanValue("modules.sidebar.dirty-updates-only", true))));
        final Boolean motdEnabled = parseBoolean(body.getOrDefault("motdEnabled", String.valueOf(this.preferences.moduleEnabled("motd"))));
        final String motdLine1 = body.getOrDefault("motdLine1", this.preferences.stringValue("modules.motd.line-1", "&b\"HunterCraft\" Server &8| &fHunterCore")).trim();
        final String motdLine2 = body.getOrDefault("motdLine2", this.preferences.stringValue("modules.motd.line-2", "&7%online%/%max% players &8- &aTPS %tps% &8- &e%version%")).trim();
        final Integer motdMaxPlayers = parseInteger(body.getOrDefault("motdMaxPlayers", String.valueOf(this.preferences.intValue("modules.motd.max-players", -1))), -1, 1_000_000);
        final Boolean authEnabled = parseBoolean(body.getOrDefault("authEnabled", String.valueOf(this.preferences.booleanValue("modules.auth.enabled", true))));
        final Boolean authRegistrationRequired = parseBoolean(body.getOrDefault("authRegistrationRequired", String.valueOf(this.preferences.booleanValue("modules.auth.registration-required", true))));
        final Boolean authWebRegistrationRequired = parseBoolean(body.getOrDefault("authWebRegistrationRequired", String.valueOf(this.preferences.booleanValue("modules.auth.web-registration-required", false))));
        final Boolean authWebRegistrationEnabled = parseBoolean(body.getOrDefault("authWebRegistrationEnabled", String.valueOf(this.preferences.booleanValue("modules.auth.web-registration-enabled", false))));
        final Boolean authWebLoginEnabled = parseBoolean(body.getOrDefault("authWebLoginEnabled", String.valueOf(this.preferences.booleanValue("modules.auth.web-login-enabled", false))));
        final Boolean authGuiEnabled = parseBoolean(body.getOrDefault("authGuiEnabled", String.valueOf(this.preferences.booleanValue("modules.auth.gui-enabled", true))));
        final Boolean authOpenGuiOnJoin = parseBoolean(body.getOrDefault("authOpenGuiOnJoin", String.valueOf(this.preferences.booleanValue("modules.auth.open-gui-on-join", true))));
        final Integer authMinimumPasswordLength = parseInteger(body.getOrDefault("authMinimumPasswordLength", String.valueOf(this.preferences.intValue("modules.auth.minimum-password-length", 4))), 1, 128);
        final Integer authLoginTimeoutSeconds = parseInteger(body.getOrDefault("authLoginTimeoutSeconds", String.valueOf(this.preferences.intValue("modules.auth.login-timeout-seconds", 90))), 0, 3600);
        final Integer authMaxLoginAttempts = parseInteger(body.getOrDefault("authMaxLoginAttempts", String.valueOf(this.preferences.intValue("modules.auth.max-login-attempts", 5))), 1, 100);
        final Integer authLockoutSeconds = parseInteger(body.getOrDefault("authLockoutSeconds", String.valueOf(this.preferences.intValue("modules.auth.lockout-seconds", 60))), 1, 86400);
        final String authRegistrationUrl = body.getOrDefault("authRegistrationUrl", this.preferences.stringValue("modules.auth.registration-url", "")).trim();
        final Boolean corsEnabled = parseBoolean(body.getOrDefault("corsEnabled", String.valueOf(this.preferences.booleanValue("modules.web-panel.cors-enabled", true))));
        final String corsAllowOrigin = body.getOrDefault("corsAllowOrigin", this.preferences.stringValue("modules.web-panel.cors-allow-origin", "*")).trim();
        final Boolean apiKeyEnabled = parseBoolean(body.getOrDefault("apiKeyEnabled", String.valueOf(this.preferences.booleanValue("modules.web-panel.api-key-enabled", false))));
        final Boolean clearApiKey = parseBoolean(body.getOrDefault("clearApiKey", "false"));
        final String apiKey = body.getOrDefault("apiKey", "").trim();
        final int port;
        try {
            port = Integer.parseInt(rawPort);
        } catch (final NumberFormatException ex) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_port\"}");
            return;
        }
        if (bindAddress.isBlank() || bindAddress.length() > 128 || bindAddress.contains(" ") || port < 1 || port > 65535
            || mapUrl.isBlank() || serverName.isBlank() || serverName.length() > 64 || publicMap == null
            || tpsDisplayEnabled == null || tpsActionbar == null || tpsIntervalTicks == null || tpsActionbarFormat.length() > 256
            || sidebarEnabled == null || sidebarTitle.isBlank() || sidebarTitle.length() > 64 || sidebarLines == null
            || sidebarIntervalTicks == null || sidebarDirtyUpdatesOnly == null || motdEnabled == null
            || motdLine1.length() > 256 || motdLine2.length() > 256 || motdMaxPlayers == null
            || authEnabled == null || authRegistrationRequired == null || authWebRegistrationRequired == null || authWebRegistrationEnabled == null || authWebLoginEnabled == null
            || authGuiEnabled == null || authOpenGuiOnJoin == null || authMinimumPasswordLength == null
            || corsEnabled == null || apiKeyEnabled == null || clearApiKey == null
            || f3ServerName.isBlank() || f3ServerName.length() > 96 || apiKey.length() > 256
            || invalidOptionalUrl(externalUrl) || invalidOptionalUrl(authRegistrationUrl)
            || corsAllowOrigin.isBlank() || corsAllowOrigin.length() > 256 || containsHeaderBreak(corsAllowOrigin)) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_web_settings\"}");
            return;
        }
        if (!validCpuMode(cpuMode)) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_cpu_mode\"}");
            return;
        }

        final boolean restart = !bindAddress.equals(this.preferences.stringValue("modules.web-panel.bind-address", "127.0.0.1"))
            || port != this.preferences.intValue("modules.web-panel.port", 8088);
        final boolean threadingChanged = !cpuMode.equalsIgnoreCase(this.preferences.stringValue("optimizations.cpu.mode", "single-thread"));
        this.preferences.setValue("modules.web-panel.bind-address", bindAddress);
        this.preferences.setValue("modules.web-panel.port", port);
        this.preferences.setValue("modules.web-panel.external-url", externalUrl);
        this.preferences.setValue("modules.web-panel.public-map", publicMap);
        this.preferences.setValue("modules.web-panel.map-url", mapUrl);
        this.preferences.setValue("modules.web-panel.server-name", serverName);
        this.preferences.setValue("modules.management.f3-server-name", f3ServerName);
        this.preferences.setModuleEnabled("tps-display", tpsDisplayEnabled);
        this.preferences.setValue("modules.tps-display.actionbar", tpsActionbar);
        this.preferences.setValue("modules.tps-display.interval-ticks", tpsIntervalTicks);
        this.preferences.setValue("modules.tps-display.actionbar-format", tpsActionbarFormat);
        this.preferences.setModuleEnabled("sidebar", sidebarEnabled);
        this.preferences.setValue("modules.sidebar.title", sidebarTitle);
        this.preferences.setValue("modules.sidebar.lines", sidebarLines);
        this.preferences.setValue("modules.sidebar.interval-ticks", sidebarIntervalTicks);
        this.preferences.setValue("modules.sidebar.dirty-updates-only", sidebarDirtyUpdatesOnly);
        this.preferences.setModuleEnabled("motd", motdEnabled);
        this.preferences.setValue("modules.motd.line-1", motdLine1);
        this.preferences.setValue("modules.motd.line-2", motdLine2);
        this.preferences.setValue("modules.motd.max-players", motdMaxPlayers);
        this.preferences.setValue("modules.auth.enabled", authEnabled);
        this.preferences.setValue("modules.auth.registration-required", authRegistrationRequired);
        this.preferences.setValue("modules.auth.web-registration-required", authWebRegistrationRequired);
        this.preferences.setValue("modules.auth.web-registration-enabled", authWebRegistrationEnabled);
        this.preferences.setValue("modules.auth.web-login-enabled", authWebLoginEnabled);
        this.preferences.setValue("modules.auth.gui-enabled", authGuiEnabled);
        this.preferences.setValue("modules.auth.open-gui-on-join", authOpenGuiOnJoin);
        this.preferences.setValue("modules.auth.minimum-password-length", authMinimumPasswordLength);
        this.preferences.setValue("modules.auth.login-timeout-seconds", authLoginTimeoutSeconds);
        this.preferences.setValue("modules.auth.max-login-attempts", authMaxLoginAttempts);
        this.preferences.setValue("modules.auth.lockout-seconds", authLockoutSeconds);
        this.preferences.setValue("modules.auth.registration-url", authRegistrationUrl);
        this.preferences.setValue("modules.web-panel.cors-enabled", corsEnabled);
        this.preferences.setValue("modules.web-panel.cors-allow-origin", corsAllowOrigin);
        this.preferences.setValue("modules.web-panel.api-key-enabled", apiKeyEnabled);
        if (clearApiKey) {
            this.preferences.setValue("modules.web-panel.api-key", "");
        } else if (!apiKey.isBlank()) {
            this.preferences.setValue("modules.web-panel.api-key", apiKey);
        }
        this.preferences.setValue("optimizations.cpu.mode", cpuMode);
        final boolean asyncEnabled = !cpuMode.equals("single-thread");
        this.preferences.setValue("optimizations.hunter-tools.async-rendering", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.async-save", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.actor-async-load", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.actor-batch-save", asyncEnabled);
        this.preferences.setValue("optimizations.hunter-tools.render-workers", this.preferences.defaultWorkerCount());
        this.preferences.setValue("optimizations.hunter-tools.web-panel-workers", this.preferences.defaultWorkerCount());
        this.savePreferences();
        this.plugin.applyServerBrand();
        this.plugin.restartDisplayTasks();
        this.invalidateStatusCaches();
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"restart\":" + restart + ",\"threadingChanged\":" + threadingChanged + ",\"settings\":" + this.webSettingsJson() + "}");
        if (restart) {
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(250L);
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                this.restart();
            });
        }
    }

    private static boolean validCpuMode(final String input) {
        return input.equals("single-thread")
            || input.equals("high-clock")
            || input.equals("high-core")
            || input.equals("multi-thread");
    }

    private static String normalizeCpuMode(final String input) {
        final String normalized = HunterToolsPreferences.normalize(input);
        return switch (normalized) {
            case "high-clock", "clock" -> "high-clock";
            case "high-core", "core" -> "high-core";
            case "multi-thread", "multi", "performance", "multithread" -> "multi-thread";
            default -> "single-thread";
        };
    }

    private void adminCommandMessages(final HttpExchange exchange) throws IOException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 64 * 1024));
        final List<String> about = commandMessageLines(body.getOrDefault("about", ""));
        final List<String> plugins = commandMessageLines(body.getOrDefault("plugins", ""));
        final List<String> version = commandMessageLines(body.getOrDefault("version", ""));
        final List<String> rules = commandMessageLines(body.getOrDefault("rules", ""));
        final List<String> discord = commandMessageLines(body.getOrDefault("discord", ""));
        final List<String> website = commandMessageLines(body.getOrDefault("website", ""));
        final List<String> motd = commandMessageLines(body.getOrDefault("motd", ""));
        final List<String> info = commandMessageLines(body.getOrDefault("info", ""));
        final List<String> server = commandMessageLines(body.getOrDefault("server", ""));
        final List<String> links = commandMessageLines(body.getOrDefault("links", ""));
        final List<String> qq = commandMessageLines(body.getOrDefault("qq", ""));
        final List<String> group = commandMessageLines(body.getOrDefault("group", ""));
        final List<String> opDenied = commandMessageLines(body.getOrDefault("opDenied", ""));
        if (about == null || plugins == null || version == null || rules == null || discord == null || website == null
            || motd == null || info == null || server == null || links == null || qq == null || group == null || opDenied == null) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_command_messages\"}");
            return;
        }
        this.preferences.setValue("modules.command-overrides.messages.about", about);
        this.preferences.setValue("modules.command-overrides.messages.plugins", plugins);
        this.preferences.setValue("modules.command-overrides.messages.version", version);
        this.preferences.setValue("modules.command-overrides.messages.rules", rules);
        this.preferences.setValue("modules.command-overrides.messages.discord", discord);
        this.preferences.setValue("modules.command-overrides.messages.website", website);
        this.preferences.setValue("modules.command-overrides.messages.motd", motd);
        this.preferences.setValue("modules.command-overrides.messages.info", info);
        this.preferences.setValue("modules.command-overrides.messages.server", server);
        this.preferences.setValue("modules.command-overrides.messages.links", links);
        this.preferences.setValue("modules.command-overrides.messages.qq", qq);
        this.preferences.setValue("modules.command-overrides.messages.group", group);
        this.preferences.setValue("modules.command-overrides.messages.op-denied", opDenied);
        this.savePreferences();
        this.invalidateStatusCaches();
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"messages\":" + this.commandMessagesJson() + "}");
    }

    private void adminAiSettings(final HttpExchange exchange) throws IOException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 48 * 1024));
        final Boolean enabled = parseBoolean(body.getOrDefault("enabled", String.valueOf(this.preferences.moduleEnabled("ai"))));
        final Boolean chatEnabled = parseBoolean(body.getOrDefault("chatEnabled", String.valueOf(this.preferences.booleanValue("modules.ai.chat.enabled", true))));
        final Boolean chatBroadcast = parseBoolean(body.getOrDefault("chatBroadcast", String.valueOf(this.preferences.booleanValue("modules.ai.chat.broadcast", true))));
        final Boolean npcEnabled = parseBoolean(body.getOrDefault("npcEnabled", String.valueOf(this.preferences.booleanValue("modules.ai.npc.enabled", true))));
        final Boolean npcAllowActions = parseBoolean(body.getOrDefault("npcAllowActions", String.valueOf(this.preferences.booleanValue("modules.ai.npc.allow-actions", true))));
        final Boolean fakePlayersEnabled = parseBoolean(body.getOrDefault("fakePlayersEnabled", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.enabled", true))));
        final Boolean fakePlayersAllowMovement = parseBoolean(body.getOrDefault("fakePlayersAllowMovement", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.allow-movement", true))));
        final Boolean fakePlayersAllowBreaking = parseBoolean(body.getOrDefault("fakePlayersAllowBreaking", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.allow-breaking", true))));
        final Boolean fakePlayersAllowPlacing = parseBoolean(body.getOrDefault("fakePlayersAllowPlacing", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.allow-placing", true))));
        final Boolean fakePlayersAllowInteraction = parseBoolean(body.getOrDefault("fakePlayersAllowInteraction", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.allow-interaction", true))));
        final Boolean fakePlayersChatControlEnabled = parseBoolean(body.getOrDefault("fakePlayersChatControlEnabled", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.chat-control.enabled", true))));
        final Boolean fakePlayersChatControlAmbientEnabled = parseBoolean(body.getOrDefault("fakePlayersChatControlAmbientEnabled", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.chat-control.ambient-enabled", true))));
        final Boolean fakePlayersChatControlRequirePermission = parseBoolean(body.getOrDefault("fakePlayersChatControlRequirePermission", String.valueOf(this.preferences.booleanValue("modules.ai.fake-players.chat-control.require-permission", false))));
        final String provider = body.getOrDefault("provider", "openai-compatible").trim();
        final String baseUrl = body.getOrDefault("baseUrl", this.preferences.stringValue("modules.ai.base-url", "https://api.openai.com/v1")).trim();
        final String model = body.getOrDefault("model", this.preferences.stringValue("modules.ai.model", "gpt-4o-mini")).trim();
        final String apiKey = body.getOrDefault("apiKey", "").trim();
        final Boolean clearApiKey = parseBoolean(body.getOrDefault("clearApiKey", "false"));
        final String apiKeyEnv = body.getOrDefault("apiKeyEnv", this.preferences.stringValue("modules.ai.api-key-env", "OPENAI_API_KEY")).trim();
        final String chatPrefix = body.getOrDefault("chatTriggerPrefix", this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai")).trim();
        final String chatPrompt = body.getOrDefault("chatSystemPrompt", this.preferences.stringValue("modules.ai.chat.system-prompt", "")).trim();
        final String npcPrompt = body.getOrDefault("npcSystemPrompt", this.preferences.stringValue("modules.ai.npc.system-prompt", "")).trim();
        final String fakePlayersPrompt = body.getOrDefault("fakePlayersSystemPrompt", this.preferences.stringValue("modules.ai.fake-players.system-prompt", "")).trim();
        final String fakePlayersQuickResponseMode = HunterToolsPreferences.normalize(body.getOrDefault(
            "fakePlayersQuickResponseMode",
            this.preferences.stringValue("modules.ai.fake-players.quick-response.mode", "off")
        ));
        final String fakePlayersChatControlPrefix = body.getOrDefault("fakePlayersChatControlPrefix", this.preferences.stringValue("modules.ai.fake-players.chat-control.trigger-prefix", "@bot")).trim();
        final String fakePlayersChatControlPermission = body.getOrDefault("fakePlayersChatControlPermission", this.preferences.stringValue("modules.ai.fake-players.chat-control.permission", "huntertools.ai.fakeplayer")).trim();
        final Double temperature = parseDouble(body.getOrDefault("temperature", String.valueOf(this.preferences.doubleValue("modules.ai.temperature", 0.7D))), 0.0D, 2.0D);
        final Integer maxTokens = parseInteger(body.getOrDefault("maxTokens", String.valueOf(this.preferences.intValue("modules.ai.max-tokens", 300))), 16, 4096);
        final Integer timeoutSeconds = parseInteger(body.getOrDefault("timeoutSeconds", String.valueOf(this.preferences.intValue("modules.ai.timeout-seconds", 30))), 1, 120);
        final Integer chatCooldown = parseInteger(body.getOrDefault("chatCooldownSeconds", String.valueOf(this.preferences.intValue("modules.ai.chat.cooldown-seconds", 5))), 0, 3600);
        final Integer npcCooldown = parseInteger(body.getOrDefault("npcCooldownSeconds", String.valueOf(this.preferences.intValue("modules.ai.npc.cooldown-seconds", 5))), 0, 3600);
        final Integer npcRadius = parseInteger(body.getOrDefault("npcResponseRadiusBlocks", String.valueOf(this.preferences.intValue("modules.ai.npc.response-radius-blocks", 16))), 0, 128);
        final Integer fakePlayersInterval = parseInteger(body.getOrDefault("fakePlayersIntervalSeconds", String.valueOf(this.preferences.intValue("modules.ai.fake-players.interval-seconds", 6))), 1, 3600);
        final Integer fakePlayersMaxActions = parseInteger(body.getOrDefault("fakePlayersMaxActions", String.valueOf(this.preferences.intValue("modules.ai.fake-players.max-actions", 5))), 1, 12);
        final Integer fakePlayersMaxMoveTicks = parseInteger(body.getOrDefault("fakePlayersMaxMoveTicks", String.valueOf(this.preferences.intValue("modules.ai.fake-players.max-move-ticks", 40))), 1, 200);
        final Integer fakePlayersMaxActionTicks = parseInteger(body.getOrDefault("fakePlayersMaxActionTicks", String.valueOf(this.preferences.intValue("modules.ai.fake-players.max-action-ticks", 80))), 1, 400);
        final Integer fakePlayersNearbyRadius = parseInteger(body.getOrDefault("fakePlayersNearbyRadiusBlocks", String.valueOf(this.preferences.intValue("modules.ai.fake-players.nearby-radius-blocks", 6))), 2, 12);
        final Integer fakePlayersMaxPlaceDistance = parseInteger(body.getOrDefault("fakePlayersMaxPlaceDistanceBlocks", String.valueOf(this.preferences.intValue("modules.ai.fake-players.max-place-distance-blocks", 6))), 1, 16);
        final Integer fakePlayersChatControlCooldown = parseInteger(body.getOrDefault("fakePlayersChatControlCooldownSeconds", String.valueOf(this.preferences.intValue("modules.ai.fake-players.chat-control.cooldown-seconds", 3))), 0, 3600);
        final List<String> whitelist = parseCommandList(body.getOrDefault("npcCommandWhitelist", String.join(",", this.preferences.stringList(
            "modules.ai.npc.command-whitelist",
            List.of("say", "tell", "msg", "title", "effect", "playsound")
        ))));
        final List<HunterToolsPreferences.AiChatProfile> chatProfiles = parseAiChatProfiles(
            body.getOrDefault("chatProfiles", "[]"),
            this.preferences.aiChatProfiles()
        );
        final List<HunterToolsPreferences.FakeBotAlias> fakeBotAliases = parseFakeBotAliases(
            body.getOrDefault("fakeBotAliases", "[]")
        );
        final List<HunterToolsPreferences.FakeAiPersonaProfile> fakePlayerPersonas = parseFakeAiPersonaProfiles(
            body.getOrDefault("fakePlayerPersonas", "[]")
        );
        final Boolean storyModeEnabled = parseBoolean(body.getOrDefault("storyModeEnabled", String.valueOf(this.preferences.booleanValue("modules.story-mode.enabled", false))));
        final String storyMainPlayerName = body.getOrDefault("storyMainPlayerName", this.preferences.stringValue("modules.story-mode.main-player-name", "")).trim();
        final String storyAiName = body.getOrDefault("storyAiName", this.preferences.stringValue("modules.story-mode.ai-name", "Ava")).trim();
        final String storyAiSkin = body.getOrDefault("storyAiSkin", this.preferences.stringValue("modules.story-mode.ai-skin", "")).trim();
        final String storyDialogueMode = HunterToolsPreferences.normalize(body.getOrDefault("storyDialogueMode", this.preferences.stringValue("modules.story-mode.dialogue-mode", "auto")));
        final String storyMeltdownKickCommand = body.getOrDefault("storyMeltdownKickCommand", this.preferences.stringValue("modules.story-mode.meltdown.kick-command", "你被实验 AI 拒绝访问该服务器。")).trim();
        final String storyMeltdownDestroyGoal = body.getOrDefault("storyMeltdownDestroyGoal", this.preferences.stringValue("modules.story-mode.meltdown.destroy-goal", "")).trim();
        final List<HunterToolsPreferences.StoryPhaseConfig> storyPhases = parseStoryPhaseConfigs(
            body.getOrDefault("storyPhases", "[]"),
            this.preferences.storyModeConfig().phases()
        );

        if (enabled == null || chatEnabled == null || chatBroadcast == null || npcEnabled == null || npcAllowActions == null
            || fakePlayersEnabled == null || fakePlayersAllowMovement == null || fakePlayersAllowBreaking == null || fakePlayersAllowPlacing == null || fakePlayersAllowInteraction == null
            || fakePlayersChatControlEnabled == null || fakePlayersChatControlAmbientEnabled == null || fakePlayersChatControlRequirePermission == null
            || clearApiKey == null || temperature == null || maxTokens == null || timeoutSeconds == null || chatCooldown == null
            || npcCooldown == null || npcRadius == null || fakePlayersInterval == null || fakePlayersMaxActions == null
            || fakePlayersMaxMoveTicks == null || fakePlayersMaxActionTicks == null || fakePlayersNearbyRadius == null || fakePlayersMaxPlaceDistance == null
            || fakePlayersChatControlCooldown == null || whitelist == null || provider.isBlank() || provider.length() > 64
            || chatProfiles == null || fakeBotAliases == null || fakePlayerPersonas == null || storyModeEnabled == null || storyPhases == null
            || !validHttpUrl(baseUrl) || model.isBlank() || model.length() > 128 || apiKey.length() > 512
            || apiKeyEnv.length() > 128 || chatPrefix.isBlank() || chatPrefix.length() > 32
            || !Set.of("off", "locked").contains(fakePlayersQuickResponseMode)
            || !Set.of("auto", "manual").contains(storyDialogueMode)
            || fakePlayersChatControlPrefix.isBlank() || fakePlayersChatControlPrefix.length() > 32
            || fakePlayersChatControlPermission.length() > 96
            || chatPrompt.length() > 4096 || npcPrompt.length() > 4096 || fakePlayersPrompt.length() > 4096
            || storyMainPlayerName.length() > 32 || storyAiName.isBlank() || storyAiName.length() > 32 || storyAiSkin.length() > 128
            || storyMeltdownKickCommand.isBlank() || storyMeltdownKickCommand.length() > 256
            || storyMeltdownDestroyGoal.isBlank() || storyMeltdownDestroyGoal.length() > 1024) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_ai_settings\"}");
            return;
        }

        this.preferences.setModuleEnabled("ai", enabled);
        this.preferences.setValue("modules.ai.provider", provider);
        this.preferences.setValue("modules.ai.base-url", baseUrl);
        this.preferences.setValue("modules.ai.model", model);
        if (clearApiKey) {
            this.preferences.setValue("modules.ai.api-key", "");
        } else if (!apiKey.isBlank()) {
            this.preferences.setValue("modules.ai.api-key", apiKey);
        }
        this.preferences.setValue("modules.ai.api-key-env", apiKeyEnv);
        this.preferences.setValue("modules.ai.temperature", temperature);
        this.preferences.setValue("modules.ai.max-tokens", maxTokens);
        this.preferences.setValue("modules.ai.timeout-seconds", timeoutSeconds);
        this.preferences.setValue("modules.ai.chat.enabled", chatEnabled);
        this.preferences.setValue("modules.ai.chat.trigger-prefix", chatPrefix);
        this.preferences.setValue("modules.ai.chat.cooldown-seconds", chatCooldown);
        this.preferences.setValue("modules.ai.chat.broadcast", chatBroadcast);
        this.preferences.setValue("modules.ai.chat.system-prompt", chatPrompt);
        this.preferences.setAiChatProfiles(chatProfiles);
        this.preferences.setValue("modules.ai.npc.enabled", npcEnabled);
        this.preferences.setValue("modules.ai.npc.cooldown-seconds", npcCooldown);
        this.preferences.setValue("modules.ai.npc.response-radius-blocks", npcRadius);
        this.preferences.setValue("modules.ai.npc.allow-actions", npcAllowActions);
        this.preferences.setValue("modules.ai.npc.system-prompt", npcPrompt);
        this.preferences.setValue("modules.ai.npc.command-whitelist", whitelist);
        this.preferences.setValue("modules.ai.fake-players.enabled", fakePlayersEnabled);
        this.preferences.setValue("modules.ai.fake-players.interval-seconds", fakePlayersInterval);
        this.preferences.setValue("modules.ai.fake-players.max-actions", fakePlayersMaxActions);
        this.preferences.setValue("modules.ai.fake-players.max-move-ticks", fakePlayersMaxMoveTicks);
        this.preferences.setValue("modules.ai.fake-players.max-action-ticks", fakePlayersMaxActionTicks);
        this.preferences.setValue("modules.ai.fake-players.nearby-radius-blocks", fakePlayersNearbyRadius);
        this.preferences.setValue("modules.ai.fake-players.allow-movement", fakePlayersAllowMovement);
        this.preferences.setValue("modules.ai.fake-players.allow-breaking", fakePlayersAllowBreaking);
        this.preferences.setValue("modules.ai.fake-players.allow-placing", fakePlayersAllowPlacing);
        this.preferences.setValue("modules.ai.fake-players.allow-interaction", fakePlayersAllowInteraction);
        this.preferences.setValue("modules.ai.fake-players.max-place-distance-blocks", fakePlayersMaxPlaceDistance);
        this.preferences.setValue("modules.ai.fake-players.quick-response.mode", fakePlayersQuickResponseMode);
        this.preferences.setValue("modules.ai.fake-players.chat-control.enabled", fakePlayersChatControlEnabled);
        this.preferences.setValue("modules.ai.fake-players.chat-control.ambient-enabled", fakePlayersChatControlAmbientEnabled);
        this.preferences.setValue("modules.ai.fake-players.chat-control.trigger-prefix", fakePlayersChatControlPrefix);
        this.preferences.setValue("modules.ai.fake-players.chat-control.cooldown-seconds", fakePlayersChatControlCooldown);
        this.preferences.setValue("modules.ai.fake-players.chat-control.require-permission", fakePlayersChatControlRequirePermission);
        this.preferences.setValue("modules.ai.fake-players.chat-control.permission", fakePlayersChatControlPermission);
        this.preferences.setFakeBotAliases(fakeBotAliases);
        this.preferences.setFakeAiPersonaProfiles(fakePlayerPersonas);
        this.preferences.setValue("modules.ai.fake-players.system-prompt", fakePlayersPrompt);
        this.preferences.setValue("modules.story-mode.enabled", storyModeEnabled);
        this.preferences.setValue("modules.story-mode.main-player-name", storyMainPlayerName);
        this.preferences.setValue("modules.story-mode.ai-name", storyAiName);
        this.preferences.setValue("modules.story-mode.ai-skin", storyAiSkin);
        this.preferences.setValue("modules.story-mode.dialogue-mode", storyDialogueMode);
        this.preferences.setValue("modules.story-mode.meltdown.kick-command", storyMeltdownKickCommand);
        this.preferences.setValue("modules.story-mode.meltdown.destroy-goal", storyMeltdownDestroyGoal);
        for (final HunterToolsPreferences.StoryPhaseConfig phase : storyPhases) {
            final String path = "modules.story-mode.phases." + phase.id();
            this.preferences.setValue(path + ".duration-seconds", phase.durationSeconds());
            this.preferences.setValue(path + ".system-prompt", phase.systemPrompt());
            this.preferences.setValue(path + ".player-lines", phase.playerLines());
            this.preferences.setValue(path + ".allowed-actions", phase.allowedActions());
            this.preferences.setValue(path + ".allow-ai-free", phase.allowAiFree());
        }
        this.savePreferences();
        this.invalidateStatusCaches();
        this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"settings\":" + this.aiSettingsJson() + "}");
    }

    private void adminAiTest(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 8 * 1024));
        final String prompt = body.getOrDefault("prompt", "").trim();
        if (prompt.isBlank() || prompt.length() > 1024) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_ai_prompt\"}");
            return;
        }
        final int timeout = Math.max(1, Math.min(120, this.preferences.intValue("modules.ai.timeout-seconds", 30))) + 5;
        try {
            final String response = this.plugin.testAiPrompt(prompt).get(timeout, TimeUnit.SECONDS);
            this.send(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"response\":\"" + escapeJson(response) + "\"}");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"ai_test_failed\",\"message\":\"" + escapeJson(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()) + "\"}");
        }
    }

    private void adminAiApproval(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange, "player");
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 8 * 1024));
        final String name = commandToken(body.getOrDefault("name", ""), 32);
        final String action = HunterToolsPreferences.normalize(body.getOrDefault("action", ""));
        if (name.isBlank() || (!action.equals("approve") && !action.equals("deny"))) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_ai_approval\"}");
            return;
        }
        this.invalidateStatusCaches();
        this.sendCommandResult(exchange, 200, this.dispatchConfiguredCommand("player ai " + name + " " + action));
    }

    private void adminPlugin(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String pluginName = commandToken(body.getOrDefault("plugin", body.getOrDefault("name", "")), 64);
        final String action = HunterToolsPreferences.normalize(body.getOrDefault("action", ""));
        if (pluginName.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_plugin\"}");
            return;
        }
        if (!action.equals("enable") && !action.equals("disable") && !action.equals("reload")) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_plugin_action\"}");
            return;
        }
        final String rateLimited = this.pluginOperationRateLimitError();
        if (rateLimited != null) {
            this.send(exchange, 429, "application/json; charset=utf-8",
                "{\"ok\":false,\"error\":\"plugin_rate_limited\",\"message\":\"" + escapeJson(rateLimited) + "\"}");
            return;
        }

        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final PluginOperationResult result = Bukkit.getScheduler()
            .callSyncMethod(this.plugin, () -> this.applyPluginAction(pluginName, action))
            .get(timeout, TimeUnit.SECONDS);
        this.invalidateStatusCaches();
        this.sendPluginOperationResult(exchange, result);
    }

    private void adminPluginUpdate(final HttpExchange exchange) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final WebSession session = this.adminOperator(exchange);
        if (session == null) {
            return;
        }
        final Map<String, String> body = parseJsonObject(this.body(exchange, 16 * 1024));
        final String pluginName = commandToken(body.getOrDefault("plugin", body.getOrDefault("name", "")), 64);
        final String rawUrl = body.getOrDefault("url", "").trim();
        if (pluginName.isBlank()) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_plugin\"}");
            return;
        }
        if (this.protectedPlugin(pluginName)) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"self_protection\"}");
            return;
        }
        final String rateLimited = this.pluginOperationRateLimitError();
        if (rateLimited != null) {
            this.send(exchange, 429, "application/json; charset=utf-8",
                "{\"ok\":false,\"error\":\"plugin_rate_limited\",\"message\":\"" + escapeJson(rateLimited) + "\"}");
            return;
        }

        final URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (final IllegalArgumentException ex) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_update_url\"}");
            return;
        }
        final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null || rawUrl.length() > 2048) {
            this.send(exchange, 400, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"invalid_update_url\"}");
            return;
        }
        try {
            this.validatePluginUpdateUri(uri);
        } catch (final IOException ex) {
            this.send(exchange, 400, "application/json; charset=utf-8",
                "{\"ok\":false,\"error\":\"blocked_update_url\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            return;
        }

        final Path pluginsDir = Bukkit.getPluginsFolder().toPath();
        Files.createDirectories(pluginsDir);
        final Path download = Files.createTempFile(pluginsDir, "huntercore-plugin-update-", ".jar.part");
        try {
            final PluginJarMetadata metadata = this.downloadPluginJar(uri, download);
            if (!metadata.name().equalsIgnoreCase(pluginName)) {
                Files.deleteIfExists(download);
                this.send(exchange, 400, "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"plugin_name_mismatch\",\"message\":\"Downloaded jar is "
                        + escapeJson(metadata.name()) + ", not " + escapeJson(pluginName) + ".\"}");
                return;
            }
            final PluginOperationResult result = this.installDownloadedPlugin(metadata, download);
            this.invalidateStatusCaches();
            this.sendPluginOperationResult(exchange, result);
        } catch (final IOException ex) {
            Files.deleteIfExists(download);
            this.send(exchange, 400, "application/json; charset=utf-8",
                "{\"ok\":false,\"error\":\"plugin_update_failed\",\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
        } catch (final InterruptedException ex) {
            Files.deleteIfExists(download);
            Thread.currentThread().interrupt();
            throw ex;
        } catch (final ExecutionException | TimeoutException ex) {
            Files.deleteIfExists(download);
            throw ex;
        }
    }

    private PluginOperationResult applyPluginAction(final String pluginName, final String action) {
        final Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null) {
            if (action.equals("enable")) {
                return this.loadInstalledPlugin(pluginName);
            }
            return PluginOperationResult.fail("plugin_not_loaded", "Plugin " + pluginName + " is installed but not loaded, or it is unknown.");
        }
        if (this.protectedPlugin(target.getName())) {
            return PluginOperationResult.fail("self_protection", target.getName() + " hosts the web panel and cannot be controlled from the web panel.");
        }

        try {
            switch (action) {
                case "enable" -> {
                    if (!target.isEnabled()) {
                        Bukkit.getPluginManager().enablePlugin(target);
                    }
                    return PluginOperationResult.ok("enabled", target.getName() + " is enabled.", pluginLine(target));
                }
                case "disable" -> {
                    if (target.isEnabled()) {
                        Bukkit.getPluginManager().disablePlugin(target);
                    }
                    return PluginOperationResult.ok("disabled", target.getName() + " is disabled.", pluginLine(target));
                }
                case "reload" -> {
                    if (target.isEnabled()) {
                        Bukkit.getPluginManager().disablePlugin(target);
                    }
                    Bukkit.getPluginManager().enablePlugin(target);
                    return PluginOperationResult.ok("reloaded", target.getName() + " was reloaded with Bukkit enable/disable.", pluginLine(target));
                }
                default -> {
                    return PluginOperationResult.fail("invalid_plugin_action", "Unsupported plugin action.");
                }
            }
        } catch (final RuntimeException ex) {
            return PluginOperationResult.fail("plugin_action_failed", target.getName() + " action failed: " + ex.getMessage());
        }
    }

    private PluginOperationResult loadInstalledPlugin(final String pluginName) {
        final PluginJarScan scan = this.pluginJarScanLookup().get(pluginName.toLowerCase(Locale.ROOT));
        if (scan == null) {
            return PluginOperationResult.fail("unknown_plugin", "No installed jar was found for " + pluginName + ".");
        }
        if (this.protectedPlugin(scan.metadata().name())) {
            return PluginOperationResult.fail("self_protection", scan.metadata().name() + " hosts the web panel and cannot be controlled from the web panel.");
        }
        try {
            final Plugin loaded = Bukkit.getPluginManager().loadPlugin(scan.path().toFile());
            Bukkit.getPluginManager().enablePlugin(loaded);
            return PluginOperationResult.ok(
                loaded.isEnabled() ? "loaded" : "installed_disabled",
                loaded.getName() + " was loaded from " + scan.path().getFileName() + ".",
                pluginLine(loaded)
            );
        } catch (final Exception ex) {
            return PluginOperationResult.fail("plugin_load_failed", scan.metadata().name() + " could not be loaded: " + ex.getMessage());
        }
    }

    private void validatePluginUpdateUri(final URI uri) throws IOException {
        final String host = uri.getHost();
        if (host == null) {
            throw new IOException("Update URL must include a host.");
        }
        if (this.preferences.booleanValue("modules.web-panel.allow-private-plugin-update-hosts", false)) {
            return;
        }
        final InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new IOException("Update host did not resolve.");
        }
        for (final InetAddress address : addresses) {
            if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
                throw new IOException("Update host resolves to a private or local network address.");
            }
        }
    }

    private PluginJarMetadata downloadPluginJar(final URI uri, final Path target) throws IOException {
        this.validatePluginUpdateUri(uri);
        final int maxMegabytes = Math.max(1, Math.min(512, this.preferences.intValue("modules.web-panel.plugin-update-max-mb", 64)));
        final long maxBytes = maxMegabytes * 1024L * 1024L;
        final HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(45L))
            .header("User-Agent", "HunterCore-WebPanel/1.0")
            .GET()
            .build();
        final HttpResponse<InputStream> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted.", ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed with HTTP " + response.statusCode() + ".");
        }
        final long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
        if (contentLength > maxBytes) {
            throw new IOException("Download is larger than " + maxMegabytes + " MB.");
        }
        try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target)) {
            final byte[] buffer = new byte[8192];
            long copied = 0L;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                copied += read;
                if (copied > maxBytes) {
                    throw new IOException("Download is larger than " + maxMegabytes + " MB.");
                }
                output.write(buffer, 0, read);
            }
        }
        return this.readPluginJarMetadata(target);
    }

    private PluginOperationResult installDownloadedPlugin(final PluginJarMetadata metadata, final Path download)
        throws IOException, InterruptedException, ExecutionException, TimeoutException {
        final Path pluginsDir = Bukkit.getPluginsFolder().toPath();
        final Path existingJar = this.pluginJarLookup().get(metadata.name().toLowerCase(Locale.ROOT));
        final Path targetJar = existingJar == null ? pluginsDir.resolve(safePluginJarName(metadata.name())) : existingJar;

        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final PluginOperationResult disableResult = Bukkit.getScheduler()
            .callSyncMethod(this.plugin, () -> this.disablePluginForUpdate(metadata.name()))
            .get(timeout, TimeUnit.SECONDS);
        if (!disableResult.ok()) {
            Files.deleteIfExists(download);
            return disableResult;
        }

        Path backup = null;
        try {
            if (Files.exists(targetJar)) {
                backup = nextBackupPath(targetJar);
                Files.move(targetJar, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(download, targetJar, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException ex) {
            if (backup != null && Files.exists(backup) && !Files.exists(targetJar)) {
                Files.move(backup, targetJar, StandardCopyOption.REPLACE_EXISTING);
            }
            throw new IOException("Could not replace plugin jar: " + ex.getMessage(), ex);
        }

        final Path backupPath = backup;
        return Bukkit.getScheduler()
            .callSyncMethod(this.plugin, () -> this.loadUpdatedPlugin(metadata, targetJar, backupPath))
            .get(timeout, TimeUnit.SECONDS);
    }

    private PluginOperationResult disablePluginForUpdate(final String pluginName) {
        final Plugin existing = Bukkit.getPluginManager().getPlugin(pluginName);
        if (existing == null) {
            return PluginOperationResult.ok("new_plugin", pluginName + " is not currently loaded. The downloaded jar will be installed.", "");
        }
        if (this.protectedPlugin(existing.getName())) {
            return PluginOperationResult.fail("self_protection", existing.getName() + " hosts the web panel and cannot be updated from the web panel.");
        }
        try {
            if (existing.isEnabled()) {
                Bukkit.getPluginManager().disablePlugin(existing);
            }
            return PluginOperationResult.ok("disabled_for_update", existing.getName() + " was disabled before jar replacement.", pluginLine(existing));
        } catch (final RuntimeException ex) {
            return PluginOperationResult.fail("plugin_disable_failed", existing.getName() + " could not be disabled: " + ex.getMessage());
        }
    }

    private PluginOperationResult loadUpdatedPlugin(final PluginJarMetadata metadata, final Path targetJar, final Path backupPath) {
        try {
            final Plugin loaded = Bukkit.getPluginManager().loadPlugin(targetJar.toFile());
            Bukkit.getPluginManager().enablePlugin(loaded);
            return PluginOperationResult.ok(
                loaded.isEnabled() ? "hot_updated" : "installed_disabled",
                metadata.name() + " " + metadata.version() + " was installed" + (loaded.isEnabled() ? " and enabled without stopping the server." : "."),
                "jar=" + targetJar.getFileName() + (backupPath == null ? "" : "\nbackup=" + backupPath.getFileName()) + "\n" + pluginLine(loaded)
            );
        } catch (final Exception ex) {
            final Plugin existing = Bukkit.getPluginManager().getPlugin(metadata.name());
            String reenabled = "";
            if (existing != null && !existing.isEnabled()) {
                try {
                    Bukkit.getPluginManager().enablePlugin(existing);
                    reenabled = "\nExisting in-memory plugin was re-enabled.";
                } catch (final RuntimeException ignored) {
                    reenabled = "\nExisting in-memory plugin could not be re-enabled.";
                }
            }
            return PluginOperationResult.ok(
                "restart_required",
                metadata.name() + " jar was installed, but Paper/Bukkit could not hot-load it. Restart or manually reload this plugin to activate the new jar.",
                "jar=" + targetJar.getFileName()
                    + (backupPath == null ? "" : "\nbackup=" + backupPath.getFileName())
                    + "\nreason=" + ex.getMessage()
                    + reenabled
            );
        }
    }

    private Map<String, Path> pluginJarLookup() {
        final Map<String, Path> jars = new HashMap<>();
        for (final Map.Entry<String, PluginJarScan> entry : this.pluginJarScanLookup().entrySet()) {
            jars.put(entry.getKey(), entry.getValue().path());
        }
        return jars;
    }

    private Map<String, PluginJarScan> pluginJarScanLookup() {
        final Map<String, PluginJarScan> jars = new HashMap<>();
        final Path pluginsDir = Bukkit.getPluginsFolder().toPath();
        if (!Files.isDirectory(pluginsDir)) {
            return jars;
        }
        try (var stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (final Path path : stream) {
                try {
                    final PluginJarMetadata metadata = this.readPluginJarMetadata(path);
                    jars.putIfAbsent(metadata.name().toLowerCase(Locale.ROOT), new PluginJarScan(metadata, path));
                } catch (final IOException ignored) {
                }
            }
        } catch (final IOException ignored) {
        }
        return jars;
    }

    private PluginJarMetadata readPluginJarMetadata(final Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            for (final String descriptorName : List.of("paper-plugin.yml", "plugin.yml")) {
                final ZipEntry entry = jarFile.getEntry(descriptorName);
                if (entry == null) {
                    continue;
                }
                try (InputStream input = jarFile.getInputStream(entry);
                     InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    final YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(reader);
                    final String name = commandToken(descriptor.getString("name", ""), 64);
                    if (name.isBlank()) {
                        continue;
                    }
                    return new PluginJarMetadata(
                        name,
                        descriptor.getString("version", ""),
                        descriptor.getString("main", ""),
                        descriptorName
                    );
                }
            }
        }
        throw new IOException("Missing plugin.yml or paper-plugin.yml.");
    }

    private boolean protectedPlugin(final String pluginName) {
        return pluginName != null && pluginName.equalsIgnoreCase(this.plugin.getName());
    }

    private static String safePluginJarName(final String pluginName) {
        final String safe = pluginName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return (safe.isBlank() ? "plugin" : safe) + ".jar";
    }

    private static Path nextBackupPath(final Path source) {
        final String fileName = source.getFileName().toString();
        Path candidate = source.resolveSibling(fileName + ".bak-" + System.currentTimeMillis());
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = source.resolveSibling(fileName + ".bak-" + System.currentTimeMillis() + "-" + suffix++);
        }
        return candidate;
    }

    private static String pluginLine(final Plugin plugin) {
        return plugin.getName() + " " + plugin.getPluginMeta().getVersion() + " " + (plugin.isEnabled() ? "enabled" : "disabled");
    }

    private void sendPluginOperationResult(final HttpExchange exchange, final PluginOperationResult result) {
        this.send(exchange, result.ok() ? 200 : 400, "application/json; charset=utf-8",
            "{\"ok\":" + result.ok()
                + ",\"status\":\"" + escapeJson(result.status()) + '"'
                + ",\"message\":\"" + escapeJson(result.message()) + '"'
                + ",\"output\":\"" + escapeJson(result.output()) + '"'
                + (result.ok() ? "" : ",\"error\":\"" + escapeJson(result.status()) + '"')
                + "}");
    }

    private void savePreferences() {
        if (this.executor == null) {
            this.preferences.saveNow();
        } else {
            this.preferences.save(this.executor);
        }
    }

    private void expireWebUserSessions(final String username) {
        final String id = HunterToolsPreferences.webUserId(username);
        this.sessions.values().removeIf(session -> session.username().equals(id));
    }

    private boolean lastAdmin(final String id) {
        int admins = 0;
        boolean targetAdmin = false;
        for (final String userId : this.preferences.webUserIds()) {
            final HunterToolsPreferences.WebUser user = this.preferences.webUser(userId);
            if (user != null && user.passwordConfigured() && normalizeRole(user.role()).equals("admin")) {
                admins++;
                if (user.id().equals(id)) {
                    targetAdmin = true;
                }
            }
        }
        return targetAdmin && admins <= 1;
    }

    private CommandResult dispatchConfiguredCommand(final String command) throws InterruptedException, ExecutionException, TimeoutException {
        final int timeout = Math.max(1, this.preferences.intValue("modules.web-panel.command-timeout-seconds", 10));
        final int maxLines = Math.max(1, this.preferences.intValue("modules.web-panel.command-output-lines", 80));
        final int maxChars = Math.max(256, this.preferences.intValue("modules.web-panel.command-output-chars", 12_000));
        return Bukkit.getScheduler().callSyncMethod(this.plugin, () -> this.dispatchWebCommand(command, maxLines, maxChars)).get(timeout, TimeUnit.SECONDS);
    }

    private void sendCommandResult(final HttpExchange exchange, final int status, final CommandResult result) {
        final Matcher editor = LUCKPERMS_EDITOR_URL.matcher(result.output());
        this.send(exchange, status, "application/json; charset=utf-8",
            "{\"ok\":true,\"dispatched\":" + result.dispatched()
                + ",\"message\":\"" + escapeJson(result.message()) + '"'
                + ",\"output\":\"" + escapeJson(result.output()) + '"'
                + (editor.find() ? ",\"editorUrl\":\"" + escapeJson(editor.group()) + '"' : "")
                + "}");
    }

    private WebSession adminOperator(final HttpExchange exchange) {
        return this.adminOperator(exchange, "hc");
    }

    private WebSession adminOperator(final HttpExchange exchange, final String requiredCommand) {
        final WebSession session = this.session(exchange);
        if (session == null) {
            this.send(exchange, 401, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"login_required\"}");
            return null;
        }
        if (!this.csrfAllowed(exchange, session)) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"csrf_required\"}");
            return null;
        }
        if (!session.admin()) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"admin_required\"}");
            return null;
        }
        if (!this.commandAllowed(session, requiredCommand)) {
            this.send(exchange, 403, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"command_denied\"}");
            return null;
        }
        return session;
    }

    private static String actorCommandLabel(final String module) {
        return switch (module) {
            case "real-fake-players" -> "player";
            case "npcs" -> "npc";
            default -> null;
        };
    }

    private static String actorLocationArguments(final Map<String, String> body) {
        final String world = commandToken(body.getOrDefault("world", ""), 64);
        final String x = body.getOrDefault("x", "").trim();
        final String y = body.getOrDefault("y", "").trim();
        final String z = body.getOrDefault("z", "").trim();
        if (world.isBlank() && x.isBlank() && y.isBlank() && z.isBlank()) {
            return "";
        }
        if (world.isBlank() || x.isBlank() || y.isBlank() || z.isBlank()) {
            return null;
        }
        try {
            final double parsedX = Double.parseDouble(x);
            final double parsedY = Double.parseDouble(y);
            final double parsedZ = Double.parseDouble(z);
            final float yaw = parseOptionalFloat(body.getOrDefault("yaw", ""), 0.0F);
            final float pitch = parseOptionalFloat(body.getOrDefault("pitch", ""), 0.0F);
            return world + " "
                + String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f %.3f", parsedX, parsedY, parsedZ, yaw, pitch);
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static String actorClickCommand(final String value) {
        if (value == null) {
            return "";
        }
        String command = value.trim();
        if (command.isBlank()) {
            return "";
        }
        if (command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.length() > 512) {
            return null;
        }
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        return command;
    }

    private static float parseOptionalFloat(final String value, final float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Float.parseFloat(value.trim());
    }

    private static String commandToken(final String value, final int maxLength) {
        if (value == null) {
            return "";
        }
        final String trimmed = value.trim();
        if (trimmed.isBlank() || trimmed.length() > maxLength || !trimmed.matches("[A-Za-z0-9_.-]+")) {
            return "";
        }
        return trimmed;
    }

    private static String permissionToken(final String value) {
        if (value == null) {
            return "";
        }
        final String trimmed = value.trim();
        if (trimmed.isBlank() || trimmed.length() > 160 || !trimmed.matches("[A-Za-z0-9*._:-]+")) {
            return "";
        }
        return trimmed;
    }

    private CommandResult dispatchWebCommand(final String command, final int maxLines, final int maxChars) {
        final CapturingConsoleCommandSender sender = new CapturingConsoleCommandSender(Bukkit.getConsoleSender(), maxLines, maxChars);
        final boolean dispatched = Bukkit.getServer().getCommandMap().dispatch(sender, command);
        final String output = sender.output();
        final String message;
        if (!dispatched) {
            message = "Command returned false.";
        } else if (output.isBlank()) {
            message = "Command dispatched as console. No command output was captured.";
        } else {
            message = "Command dispatched as console.";
        }
        return new CommandResult(dispatched, message, output);
    }

    private boolean commandAllowed(final WebSession session, final String command) {
        final String root = commandRoot(command);
        if (session.admin()) {
            if (!this.preferences.booleanValue("modules.web-panel.admin-command-execution", true)) {
                return false;
            }
            if (this.preferences.booleanValue("modules.web-panel.admin-bypass.enabled", true)
                && this.preferences.booleanValue("modules.web-panel.admin-bypass.command-allowlist", true)) {
                return true;
            }
            if (!session.commandExecution()) {
                return false;
            }
            return !session.allowedCommandsConfigured() || allowedCommand(session.allowedCommands(), root);
        }

        if (!session.commandExecution()) {
            return false;
        }
        if (!this.preferences.booleanValue("modules.web-panel.player-command-execution", true)) {
            return false;
        }
        if (session.allowedCommandsConfigured()) {
            return allowedCommand(session.allowedCommands(), root);
        }
        return allowedCommand(this.preferences.stringList("modules.web-panel.player-allowed-commands", List.of()), root);
    }

    private boolean csrfAllowed(final HttpExchange exchange, final WebSession session) {
        if (session.authSource().equals("api-key")) {
            return true;
        }
        if (!this.preferences.booleanValue("modules.web-panel.require-csrf", true)) {
            return true;
        }
        final String provided = exchange.getRequestHeaders().getFirst(CSRF_HEADER);
        return provided != null && MessageDigest.isEqual(
            session.csrfToken().getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8)
        );
    }

    private WebSession session(final HttpExchange exchange) {
        final WebSession apiKeySession = this.apiKeySession(exchange);
        if (apiKeySession != null) {
            return apiKeySession;
        }
        String token = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        if (token == null || token.isBlank()) {
            token = cookie(exchange, SESSION_COOKIE);
        }
        if (token == null || token.isBlank()) {
            return null;
        }
        final WebSession session = this.sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAtMillis() < System.currentTimeMillis()) {
            this.sessions.remove(token);
            return null;
        }
        return session;
    }

    private WebSession apiKeySession(final HttpExchange exchange) {
        if (!this.preferences.booleanValue("modules.web-panel.api-key-enabled", false)) {
            return null;
        }
        final String configured = this.preferences.stringValue("modules.web-panel.api-key", "");
        if (configured.isBlank()) {
            return null;
        }
        String provided = exchange.getRequestHeaders().getFirst(API_KEY_HEADER);
        if (provided == null || provided.isBlank()) {
            final String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                provided = authorization.substring(7).trim();
            }
        }
        if (provided == null || provided.isBlank() || !MessageDigest.isEqual(
            configured.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8)
        )) {
            return null;
        }
        return new WebSession(
            "api-key",
            "",
            "api-key",
            "API Key",
            "admin",
            "api-key",
            true,
            false,
            List.of(),
            Instant.now().plusSeconds(60L).toEpochMilli()
        );
    }

    private void requireMethod(final HttpExchange exchange, final String method) {
        if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
            throw new MethodMismatchException();
        }
    }

    private String body(final HttpExchange exchange, final int maxBytes) throws IOException {
        final byte[] bytes = exchange.getRequestBody().readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) {
            throw new IOException("request body too large");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void send(final HttpExchange exchange, final int status, final String type, final String body) {
        try {
            final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            final Headers headers = exchange.getResponseHeaders();
            this.applyCorsHeaders(exchange);
            headers.set("Content-Type", type);
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (final IOException ignored) {
        }
    }

    private void sendNoContent(final HttpExchange exchange, final int status) {
        try {
            this.applyCorsHeaders(exchange);
            exchange.sendResponseHeaders(status, -1);
        } catch (final IOException ignored) {
        }
    }

    private void sendBytes(final HttpExchange exchange, final int status, final String type, final byte[] bytes) {
        try {
            final Headers headers = exchange.getResponseHeaders();
            this.applyCorsHeaders(exchange);
            headers.set("Content-Type", type);
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        } catch (final IOException ignored) {
        }
    }

    private void applyCorsHeaders(final HttpExchange exchange) {
        if (!this.preferences.booleanValue("modules.web-panel.cors-enabled", true)) {
            return;
        }
        final String allowOrigin = this.corsAllowOrigin(exchange);
        if (allowOrigin.isBlank()) {
            return;
        }
        final Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", allowOrigin);
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, " + CSRF_HEADER + ", " + SESSION_HEADER + ", " + API_KEY_HEADER);
        headers.set("Access-Control-Max-Age", "86400");
        if (!allowOrigin.equals("*")) {
            headers.set("Access-Control-Allow-Credentials", "true");
            headers.add("Vary", "Origin");
        }
    }

    private String corsAllowOrigin(final HttpExchange exchange) {
        final String configured = this.preferences.stringValue("modules.web-panel.cors-allow-origin", "*").trim();
        if (configured.equals("*")) {
            return "*";
        }
        final String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank()) {
            for (final String allowed : configured.split(",")) {
                if (origin.equals(allowed.trim())) {
                    return origin;
                }
            }
        }
        return configured.split(",", 2)[0].trim();
    }

    private void sendServerIcon(final HttpExchange exchange) {
        final Path icon = Bukkit.getWorldContainer().toPath().resolve("server-icon.png");
        if (!Files.isRegularFile(icon)) {
            this.send(exchange, 404, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"not_found\"}");
            return;
        }
        try {
            this.sendBytes(exchange, 200, "image/png", Files.readAllBytes(icon));
        } catch (final IOException ex) {
            this.send(exchange, 500, "application/json; charset=utf-8", "{\"ok\":false,\"error\":\"icon_unavailable\"}");
        }
    }

    private String newToken() {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] pbkdf2(final char[] password, final byte[] salt, final int iterations, final int bits) {
        try {
            final SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            final KeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
            return factory.generateSecret(spec).getEncoded();
        } catch (final Exception ex) {
            throw new IllegalStateException("PBKDF2 is unavailable", ex);
        }
    }

    private static String hunterAuthHash(final String password, final byte[] salt) {
        return Base64.getEncoder().encodeToString(pbkdf2(password.toCharArray(), salt, HASH_ITERATIONS, HASH_BITS));
    }

    private static UUID offlineUuid(final String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean validMinecraftUsername(final String username) {
        return username != null && username.matches("[A-Za-z0-9_]{3,16}");
    }

    private static boolean hunterAuthNameExists(final YamlConfiguration users, final String username) {
        final ConfigurationSection section = users.getConfigurationSection("users");
        if (section == null) {
            return false;
        }
        for (final String id : section.getKeys(false)) {
            if (users.getString("users." + id + ".name", "").equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> parseJsonObject(final String json) {
        final Map<String, String> values = new HashMap<>();
        int index = 0;
        while (index < json.length()) {
            final int keyStart = json.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            final int keyEnd = findStringEnd(json, keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            final String key = unescapeJson(json.substring(keyStart + 1, keyEnd));
            final int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) {
                break;
            }
            final int valueStart = json.indexOf('"', colon + 1);
            if (valueStart < 0) {
                index = colon + 1;
                continue;
            }
            final int valueEnd = findStringEnd(json, valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            values.put(key, unescapeJson(json.substring(valueStart + 1, valueEnd)));
            index = valueEnd + 1;
        }
        return values;
    }

    private static int findStringEnd(final String json, final int start) {
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeJson(final String value) {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
    }

    private static String normalizePath(final String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    private static String cookie(final HttpExchange exchange, final String name) {
        final List<String> cookies = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
        for (final String header : cookies) {
            for (final String item : header.split(";")) {
                final String[] parts = item.trim().split("=", 2);
                if (parts.length == 2 && parts[0].equals(name)) {
                    return parts[1];
                }
            }
        }
        return null;
    }

    private static String requestHost(final HttpExchange exchange) {
        final String header = exchange.getRequestHeaders().getFirst("Host");
        if (header == null || header.isBlank()) {
            return "127.0.0.1";
        }
        final int colon = header.indexOf(':');
        return colon > 0 ? header.substring(0, colon) : header;
    }

    private static String sessionJson(final WebSession session) {
        return "{\"username\":\"" + escapeJson(session.displayName())
            + "\",\"role\":\"" + escapeJson(session.role())
            + "\",\"admin\":" + session.admin()
            + ",\"authSource\":\"" + escapeJson(session.authSource()) + '"'
            + ",\"token\":\"" + escapeJson(session.token()) + '"'
            + ",\"csrf\":\"" + escapeJson(session.csrfToken()) + '"'
            + ",\"commandExecution\":" + session.commandExecution()
            + ",\"allowedCommandsConfigured\":" + session.allowedCommandsConfigured()
            + ",\"allowedCommands\":" + stringArrayJson(session.allowedCommands())
            + ",\"expiresAt\":" + session.expiresAtMillis() + "}";
    }

    private static boolean allowedCommand(final List<String> allowedCommands, final String root) {
        for (final String value : allowedCommands) {
            final String allowed = commandRoot(value);
            if (allowed.equals("*") || allowed.equals(root)) {
                return true;
            }
        }
        return false;
    }

    private static String commandRoot(final String command) {
        return command.replaceFirst("^/+", "").trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
    }

    private static Boolean parseBoolean(final String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("on") || normalized.equals("yes") || normalized.equals("1")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("off") || normalized.equals("no") || normalized.equals("0")) {
            return false;
        }
        return null;
    }

    private static Double parseDouble(final String value, final double min, final double max) {
        try {
            final double parsed = Double.parseDouble(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static Integer parseInteger(final String value, final int min, final int max) {
        try {
            final int parsed = Integer.parseInt(value.trim());
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static boolean validHttpUrl(final String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            return false;
        }
        try {
            final URI uri = URI.create(value);
            final String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
        } catch (final IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean invalidOptionalUrl(final String value) {
        return value != null && !value.isBlank() && !validHttpUrl(value);
    }

    private static String plainChatMessage(final String value) {
        if (value == null) {
            return "";
        }
        final String text = ChatColor.stripColor(value.replace('\n', ' ').replace('\r', ' ').trim());
        if (text == null) {
            return "";
        }
        return text.length() > 240 ? text.substring(0, 240).trim() : text;
    }

    private void addChatLine(final String sender, final String source, final String message) {
        final String cleanMessage = plainChatMessage(message);
        if (cleanMessage.isBlank()) {
            return;
        }
        final String cleanSource = normalizeChatSource(source);
        synchronized (this.chatLines) {
            this.chatLines.addLast(new WebChatLine(
                System.currentTimeMillis(),
                plainChatMessage(sender).isBlank() ? "unknown" : plainChatMessage(sender),
                cleanSource,
                cleanMessage
            ));
            while (this.chatLines.size() > 120) {
                this.chatLines.removeFirst();
            }
        }
    }

    private static String normalizeChatSource(final String source) {
        if (source == null || source.isBlank()) {
            return "game";
        }
        final String normalized = source.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "game", "web", "ai", "ai-npc", "ai-fake-player", "system" -> normalized;
            default -> "system";
        };
    }

    private static boolean containsHeaderBreak(final String value) {
        return value != null && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0);
    }

    private static List<String> parseCommandList(final String rawCommands) {
        final List<String> commands = new ArrayList<>();
        for (final String raw : rawCommands.split("[,\\s]+")) {
            final String command = commandRoot(raw);
            if (command.isBlank()) {
                continue;
            }
            if (command.length() > 64 || !command.matches("[a-z0-9*_.:-]+")) {
                return null;
            }
            if (!commands.contains(command)) {
                commands.add(command);
            }
            if (commands.size() > 64) {
                return null;
            }
        }
        return commands;
    }

    private static List<HunterToolsPreferences.AiChatProfile> parseAiChatProfiles(
        final String raw,
        final List<HunterToolsPreferences.AiChatProfile> fallback
    ) {
        try {
            final JsonElement root = JsonParser.parseString(raw == null || raw.isBlank() ? "[]" : raw);
            if (!root.isJsonArray()) {
                return null;
            }
            final List<HunterToolsPreferences.AiChatProfile> profiles = new ArrayList<>();
            int index = 1;
            for (final JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    return null;
                }
                final JsonObject object = element.getAsJsonObject();
                final String displayName = jsonString(object, "displayName").trim();
                final String id = jsonString(object, "id").trim();
                final String prompt = jsonString(object, "systemPrompt").trim();
                final String format = jsonString(object, "responseFormat").trim();
                final List<String> aliases = jsonStringList(object.get("aliases"), 12, 48);
                if (aliases == null || displayName.isBlank() || displayName.length() > 48 || prompt.length() > 4096 || format.length() > 256) {
                    return null;
                }
                profiles.add(new HunterToolsPreferences.AiChatProfile(
                    id.isBlank() ? "ai-" + index : id,
                    displayName,
                    aliases,
                    prompt,
                    format.isBlank() ? "&b%name% &8> &f%response%" : format,
                    jsonBoolean(object, "enabled", true)
                ));
                index++;
                if (profiles.size() > 16) {
                    return null;
                }
            }
            return profiles.isEmpty() ? fallback : profiles;
        } catch (final JsonSyntaxException | IllegalStateException ex) {
            return null;
        }
    }

    private static List<HunterToolsPreferences.FakeBotAlias> parseFakeBotAliases(final String raw) {
        try {
            final JsonElement root = JsonParser.parseString(raw == null || raw.isBlank() ? "[]" : raw);
            if (!root.isJsonArray()) {
                return null;
            }
            final List<HunterToolsPreferences.FakeBotAlias> aliases = new ArrayList<>();
            int index = 1;
            for (final JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    return null;
                }
                final JsonObject object = element.getAsJsonObject();
                final String target = jsonString(object, "target").trim();
                final String id = jsonString(object, "id").trim();
                final List<String> names = jsonStringList(object.get("aliases"), 12, 48);
                if (names == null || target.isBlank() || target.length() > 48) {
                    return null;
                }
                aliases.add(new HunterToolsPreferences.FakeBotAlias(
                    id.isBlank() ? "bot-" + index : id,
                    target,
                    names,
                    jsonBoolean(object, "enabled", true)
                ));
                index++;
                if (aliases.size() > 64) {
                    return null;
                }
            }
            return aliases;
        } catch (final JsonSyntaxException | IllegalStateException ex) {
            return null;
        }
    }

    private static List<HunterToolsPreferences.FakeAiPersonaProfile> parseFakeAiPersonaProfiles(final String raw) {
        try {
            final JsonElement root = JsonParser.parseString(raw == null || raw.isBlank() ? "[]" : raw);
            if (!root.isJsonArray()) {
                return null;
            }
            final List<HunterToolsPreferences.FakeAiPersonaProfile> profiles = new ArrayList<>();
            int index = 1;
            for (final JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    return null;
                }
                final JsonObject object = element.getAsJsonObject();
                final String displayName = jsonString(object, "displayName").trim();
                final String id = jsonString(object, "id").trim();
                final String prompt = jsonString(object, "systemPrompt").trim();
                final String defaultGoal = jsonString(object, "defaultGoal").trim();
                final List<String> aliases = jsonStringList(object.get("aliases"), 12, 48);
                if (aliases == null || displayName.isBlank() || displayName.length() > 48
                    || prompt.isBlank() || prompt.length() > 4096 || defaultGoal.length() > 512) {
                    return null;
                }
                profiles.add(new HunterToolsPreferences.FakeAiPersonaProfile(
                    id.isBlank() ? "persona-" + index : id,
                    displayName,
                    aliases,
                    prompt,
                    defaultGoal,
                    jsonBoolean(object, "enabled", true)
                ));
                index++;
                if (profiles.size() > 32) {
                    return null;
                }
            }
            return profiles;
        } catch (final JsonSyntaxException | IllegalStateException ex) {
            return null;
        }
    }

    private static List<HunterToolsPreferences.StoryPhaseConfig> parseStoryPhaseConfigs(
        final String raw,
        final List<HunterToolsPreferences.StoryPhaseConfig> fallback
    ) {
        try {
            final JsonElement root = JsonParser.parseString(raw == null || raw.isBlank() ? "[]" : raw);
            if (!root.isJsonArray()) {
                return null;
            }
            final List<HunterToolsPreferences.StoryPhaseConfig> phases = new ArrayList<>();
            for (final JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    return null;
                }
                final JsonObject object = element.getAsJsonObject();
                final String id = jsonString(object, "id").trim();
                final String prompt = jsonString(object, "systemPrompt").trim();
                final Integer duration = parseInteger(String.valueOf(jsonNumber(object, "durationSeconds", 60)), 1, 3600);
                final List<String> playerLines = jsonStringList(object.get("playerLines"), 16, 240);
                final List<String> allowedActions = jsonStringList(object.get("allowedActions"), 24, 64);
                if (id.isBlank() || duration == null || playerLines == null || allowedActions == null || prompt.isBlank() || prompt.length() > 4096) {
                    return null;
                }
                phases.add(new HunterToolsPreferences.StoryPhaseConfig(
                    id,
                    duration,
                    prompt,
                    playerLines,
                    allowedActions,
                    jsonBoolean(object, "allowAiFree", false)
                ));
            }
            return phases.isEmpty() ? fallback : phases;
        } catch (final JsonSyntaxException | IllegalStateException ex) {
            return null;
        }
    }

    private static List<String> jsonStringList(final JsonElement element, final int maxItems, final int maxLength) {
        final List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (!element.isJsonArray()) {
            return null;
        }
        for (final JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                return null;
            }
            final String value = item.getAsString().trim();
            if (value.isBlank()) {
                continue;
            }
            if (value.length() > maxLength) {
                return null;
            }
            if (!values.contains(value)) {
                values.add(value);
            }
            if (values.size() > maxItems) {
                return null;
            }
        }
        return values;
    }

    private static int jsonNumber(final JsonObject object, final String key, final int fallback) {
        final JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsInt();
    }

    private static String jsonString(final JsonObject object, final String field) {
        final JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
    }

    private static boolean jsonBoolean(final JsonObject object, final String field, final boolean fallback) {
        final JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : fallback;
    }

    private static ParsedAllowedCommands parseAllowedCommands(final String mode, final String rawCommands) {
        final String normalizedMode = mode == null ? "inherit" : mode.trim().toLowerCase(Locale.ROOT);
        if (normalizedMode.equals("inherit")) {
            return new ParsedAllowedCommands(true, null);
        }
        if (normalizedMode.equals("none")) {
            return new ParsedAllowedCommands(true, List.of());
        }
        if (!normalizedMode.equals("custom")) {
            return new ParsedAllowedCommands(false, List.of());
        }
        final List<String> commands = new ArrayList<>();
        for (final String raw : rawCommands.split("[,\\s]+")) {
            final String command = commandRoot(raw);
            if (!command.isBlank() && !commands.contains(command)) {
                commands.add(command);
            }
        }
        return new ParsedAllowedCommands(true, commands);
    }

    private static List<String> commandMessageLines(final String raw) {
        final String value = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n');
        if (value.length() > 4096) {
            return null;
        }
        final List<String> lines = new ArrayList<>(List.of(value.split("\n", -1)));
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        if (lines.size() > 24) {
            return null;
        }
        for (final String line : lines) {
            if (line.length() > 256) {
                return null;
            }
        }
        return lines;
    }

    private static String onOff(final boolean enabled) {
        return enabled ? "on" : "off";
    }

    private static String stringArrayJson(final List<String> values) {
        final StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (final String value : values) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escapeJson(value)).append('"');
        }
        return json.append(']').toString();
    }

    private static StringBuilder field(final StringBuilder json, final String name, final String value) {
        json.append('"').append(escapeJson(name)).append("\":\"").append(escapeJson(value == null ? "" : value)).append('"');
        return json;
    }

    private static StringBuilder numberField(final StringBuilder json, final String name, final double value) {
        json.append('"').append(escapeJson(name)).append("\":").append(String.format(Locale.ROOT, "%.3f", value));
        return json;
    }

    private static StringBuilder numberField(final StringBuilder json, final String name, final long value) {
        json.append('"').append(escapeJson(name)).append("\":").append(value);
        return json;
    }

    private static StringBuilder booleanField(final StringBuilder json, final String name, final boolean value) {
        json.append('"').append(escapeJson(name)).append("\":").append(value);
        return json;
    }

    private static String escapeJson(final String value) {
        final StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String normalizeRole(final String role) {
        final String normalized = role == null ? "player" : role.toLowerCase(Locale.ROOT);
        return normalized.equals("admin") ? "admin" : "player";
    }

    private static String webAsset(final String name, final String fallback) {
        try (InputStream input = HunterWebPanelManager.class.getResourceAsStream("/web-panel/" + name)) {
            if (input == null) {
                return fallback;
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException ex) {
            return fallback;
        }
    }

    private static String webPanelHtml(final String mode) {
        final String html = webAsset("index.html", INDEX_HTML);
        final String safeMode = mode.equals("frontend") ? "frontend" : "backend";
        return html.replaceFirst("<body([^>]*)data-panel-mode=\"[^\"]*\"([^>]*)>", "<body$1data-panel-mode=\"" + safeMode + "\"$2>");
    }

    private static byte[] webAssetBytes(final String name) {
        try (InputStream input = HunterWebPanelManager.class.getResourceAsStream("/web-panel/" + name)) {
            if (input == null) {
                return new byte[0];
            }
            return input.readAllBytes();
        } catch (final IOException ex) {
            return new byte[0];
        }
    }

    private record WebSession(
        String token,
        String csrfToken,
        String username,
        String displayName,
        String role,
        String authSource,
        boolean commandExecution,
        boolean allowedCommandsConfigured,
        List<String> allowedCommands,
        long expiresAtMillis
    ) {
        boolean admin() {
            return this.role.equals("admin");
        }
    }

    private record CachedResponse(String body, long expiresAtMillis) {
    }

    private record CommandResult(boolean dispatched, String message, String output) {
    }

    private record WebChatLine(long time, String sender, String source, String message) {
    }

    private record PluginOperationResult(boolean ok, String status, String message, String output) {
        static PluginOperationResult ok(final String status, final String message, final String output) {
            return new PluginOperationResult(true, status, message, output == null ? "" : output);
        }

        static PluginOperationResult fail(final String status, final String message) {
            return new PluginOperationResult(false, status, message, "");
        }
    }

    private record PluginJarMetadata(String name, String version, String main, String descriptor) {
    }

    private record PluginJarScan(PluginJarMetadata metadata, Path path) {
    }

    private record ParsedAllowedCommands(boolean valid, List<String> commands) {
    }

    private record WorldPanelStats(String name, int players, int loadedChunks, int entities, long time) {
    }

    private record HealthAlert(String severity, String label, String detail) {
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static final class CapturingConsoleCommandSender implements ConsoleCommandSender {
        private final ConsoleCommandSender delegate;
        private final int maxLines;
        private final int maxChars;
        private final List<String> lines = new ArrayList<>();
        private final Spigot spigot = new Spigot() {
            @Override
            public void sendMessage(final BaseComponent component) {
                CapturingConsoleCommandSender.this.capture(component.toLegacyText());
            }

            @Override
            public void sendMessage(final BaseComponent... components) {
                CapturingConsoleCommandSender.this.capture(new TextComponent(components).toLegacyText());
            }

            @Override
            public void sendMessage(final java.util.UUID sender, final BaseComponent component) {
                CapturingConsoleCommandSender.this.capture(component.toLegacyText());
            }

            @Override
            public void sendMessage(final java.util.UUID sender, final BaseComponent... components) {
                CapturingConsoleCommandSender.this.capture(new TextComponent(components).toLegacyText());
            }
        };
        private int chars;
        private boolean truncated;

        private CapturingConsoleCommandSender(final ConsoleCommandSender delegate, final int maxLines, final int maxChars) {
            this.delegate = delegate;
            this.maxLines = maxLines;
            this.maxChars = maxChars;
        }

        @Override
        public void sendMessage(final String message) {
            this.capture(message);
        }

        @Override
        public void sendMessage(final String... messages) {
            for (final String message : messages) {
                this.capture(message);
            }
        }

        @Override
        public void sendMessage(final java.util.UUID sender, final String message) {
            this.capture(message);
        }

        @Override
        public void sendMessage(final java.util.UUID sender, final String... messages) {
            for (final String message : messages) {
                this.capture(message);
            }
        }

        @Override
        public Server getServer() {
            return this.delegate.getServer();
        }

        @Override
        public String getName() {
            return "HunterCore Web Console";
        }

        @Override
        public net.kyori.adventure.text.Component name() {
            return net.kyori.adventure.text.Component.text(this.getName());
        }

        @Override
        public Spigot spigot() {
            return this.spigot;
        }

        @Override
        public boolean isPermissionSet(final String name) {
            return this.delegate.isPermissionSet(name);
        }

        @Override
        public boolean isPermissionSet(final Permission perm) {
            return this.delegate.isPermissionSet(perm);
        }

        @Override
        public boolean hasPermission(final String name) {
            return this.delegate.hasPermission(name);
        }

        @Override
        public boolean hasPermission(final Permission perm) {
            return this.delegate.hasPermission(perm);
        }

        @Override
        public PermissionAttachment addAttachment(final Plugin plugin, final String name, final boolean value) {
            return this.delegate.addAttachment(plugin, name, value);
        }

        @Override
        public PermissionAttachment addAttachment(final Plugin plugin) {
            return this.delegate.addAttachment(plugin);
        }

        @Override
        public PermissionAttachment addAttachment(final Plugin plugin, final String name, final boolean value, final int ticks) {
            return this.delegate.addAttachment(plugin, name, value, ticks);
        }

        @Override
        public PermissionAttachment addAttachment(final Plugin plugin, final int ticks) {
            return this.delegate.addAttachment(plugin, ticks);
        }

        @Override
        public void removeAttachment(final PermissionAttachment attachment) {
            this.delegate.removeAttachment(attachment);
        }

        @Override
        public void recalculatePermissions() {
            this.delegate.recalculatePermissions();
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return this.delegate.getEffectivePermissions();
        }

        @Override
        public boolean isOp() {
            return this.delegate.isOp();
        }

        @Override
        public void setOp(final boolean value) {
            this.delegate.setOp(value);
        }

        @Override
        public boolean isConversing() {
            return this.delegate.isConversing();
        }

        @Override
        public void acceptConversationInput(final String input) {
            this.delegate.acceptConversationInput(input);
        }

        @Override
        public boolean beginConversation(final Conversation conversation) {
            return this.delegate.beginConversation(conversation);
        }

        @Override
        public void abandonConversation(final Conversation conversation) {
            this.delegate.abandonConversation(conversation);
        }

        @Override
        public void abandonConversation(final Conversation conversation, final ConversationAbandonedEvent details) {
            this.delegate.abandonConversation(conversation, details);
        }

        @Override
        public void sendRawMessage(final String message) {
            this.capture(message);
        }

        @Override
        public void sendRawMessage(final java.util.UUID sender, final String message) {
            this.capture(message);
        }

        private void capture(final String message) {
            if (message == null || this.truncated) {
                return;
            }
            for (final String rawLine : message.split("\\R", -1)) {
                if (this.lines.size() >= this.maxLines) {
                    this.truncated = true;
                    return;
                }
                String line = ChatColor.stripColor(rawLine);
                if (line == null) {
                    line = rawLine;
                }
                final int remaining = this.maxChars - this.chars;
                if (remaining <= 0) {
                    this.truncated = true;
                    return;
                }
                if (line.length() > remaining) {
                    this.lines.add(line.substring(0, remaining));
                    this.chars += remaining;
                    this.truncated = true;
                    return;
                }
                this.lines.add(line);
                this.chars += line.length() + 1;
            }
        }

        private String output() {
            final String output = String.join("\n", this.lines);
            return this.truncated ? output + "\n[output truncated]" : output;
        }
    }

    private static final class MethodMismatchException extends RuntimeException {
    }

    private static final String INDEX_HTML = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>HunterCore Panel</title>
          <link rel="stylesheet" href="/assets/app.css">
        </head>
        <body>
          <main class="shell">
            <section class="topbar">
              <nav class="authActions" aria-label="Account">
                <button id="openLoginButton" type="button">Login</button>
                <button id="openRegisterButton" type="button">Register</button>
                <button id="logoutButton" type="button" hidden>Logout</button>
              </nav>
            </section>
            <div id="modalBackdrop" class="modalBackdrop" hidden></div>
            <section id="loginModal" class="authModal" role="dialog" aria-modal="true" aria-labelledby="loginTitle" hidden>
              <form id="loginForm" class="authBox">
                <div class="sectionTitle">
                  <h2 id="loginTitle">Login</h2>
                  <button class="ghostButton" type="button" data-close-modal="true">Close</button>
                </div>
                <input id="username" name="username" autocomplete="username" placeholder="username">
                <input id="password" name="password" type="password" autocomplete="current-password" placeholder="password">
                <button type="submit">Login</button>
                <p id="loginMessage" class="muted"></p>
              </form>
            </section>
            <section id="registerModal" class="authModal" role="dialog" aria-modal="true" aria-labelledby="registerTitle" hidden>
              <form id="registerForm" class="authBox">
                <div class="sectionTitle">
                  <h2 id="registerTitle">Register</h2>
                  <button class="ghostButton" type="button" data-close-modal="true">Close</button>
                </div>
                <input id="registerUsername" name="username" autocomplete="username" placeholder="player name">
                <input id="registerPassword" name="password" type="password" autocomplete="new-password" placeholder="password">
                <input id="registerPasswordConfirm" name="confirmPassword" type="password" autocomplete="new-password" placeholder="confirm password">
                <button type="submit">Register</button>
                <p id="registerMessage" class="muted"></p>
              </form>
            </section>
            <section class="metrics">
              <div><span>TPS</span><strong id="tps">--</strong></div>
              <div><span>MSPT</span><strong id="mspt">--</strong></div>
              <div><span>Players</span><strong id="players">--</strong></div>
              <div><span>Memory</span><strong id="memory">--</strong></div>
            </section>
            <section class="grid">
              <article id="profilePanel" class="widePanel">
                <div class="sectionTitle">
                  <h2>Hunter Profile</h2>
                  <strong class="healthBadge">player</strong>
                </div>
                <div id="profileCard" class="profileCard muted">Login to view your character, inventory, ender chest, and level.</div>
              </article>
              <article id="adminDashboardPanel" class="widePanel" hidden>
                <div class="sectionTitle">
                  <h2>Admin Command Center</h2>
                  <strong class="healthBadge">admin</strong>
                </div>
                <div id="adminDashboard" class="list muted">Login as admin to view server operations.</div>
              </article>
            </section>
            <section class="grid">
              <article>
                <div class="sectionTitle">
                  <h2>Health</h2>
                  <strong id="healthStatus" class="healthBadge">--</strong>
                </div>
                <div id="healthList" class="list"></div>
              </article>
              <article>
                <h2>Worlds</h2>
                <div id="worlds" class="list"></div>
              </article>
              <article>
                <h2>Players</h2>
                <div id="playerList" class="list muted">Login to view player detail.</div>
              </article>
              <article>
                <h2>Plugins</h2>
                <div id="pluginList" class="list muted">Login to view plugin detail.</div>
              </article>
              <article>
                <h2>Optimization</h2>
                <div id="optimizationList" class="list"></div>
              </article>
              <article id="actorPanel" hidden>
                <div class="sectionTitle">
                  <h2>Actors</h2>
                  <strong class="healthBadge">admin</strong>
                </div>
                <form id="actorForm" class="command actorForm">
                  <select id="actorModule">
                    <option value="npcs">npc</option>
                    <option value="fake-players">fake player</option>
                  </select>
                  <select id="actorKind">
                    <option value="villager">villager</option>
                    <option value="mannequin">mannequin</option>
                  </select>
                  <input id="actorName" placeholder="name">
                  <select id="actorWorld">
                    <option value="">spawn</option>
                  </select>
                  <input id="actorX" class="coord" placeholder="x">
                  <input id="actorY" class="coord" placeholder="y">
                  <input id="actorZ" class="coord" placeholder="z">
                  <button type="submit">Spawn</button>
                </form>
                <div id="actorList" class="list muted">Login as admin to manage actors.</div>
              </article>
              <article id="opsPanel" hidden>
                <div class="sectionTitle">
                  <h2>Operations</h2>
                  <strong class="healthBadge">admin</strong>
                </div>
                <h3>Modules</h3>
                <div id="moduleControls" class="list"></div>
                <h3>Commands</h3>
                <div id="commandControls" class="list"></div>
              </article>
              <article id="usersPanel" hidden>
                <div class="sectionTitle">
                  <h2>Web Users</h2>
                  <strong class="healthBadge">admin</strong>
                </div>
                <form id="webUserForm" class="command userForm">
                  <input id="webUserName" placeholder="username">
                  <select id="webUserRole">
                    <option value="player">player</option>
                    <option value="admin">admin</option>
                  </select>
                  <input id="webUserPassword" type="password" autocomplete="new-password" placeholder="new password">
                  <label class="inlineToggle"><input id="webUserCommandExecution" type="checkbox" checked> commands</label>
                  <select id="webUserAllowedMode">
                    <option value="inherit">inherit</option>
                    <option value="custom">custom</option>
                    <option value="none">none</option>
                  </select>
                  <input id="webUserAllowedCommands" placeholder="list spawn or *">
                  <button type="submit">Save</button>
                </form>
                <div id="webUserList" class="list muted">Login as admin to manage web users.</div>
              </article>
              <article>
                <h2>Command</h2>
                <form id="commandForm" class="command">
                  <input id="commandInput" placeholder="list">
                  <button type="submit">Run</button>
                </form>
                <pre id="commandResult">Login to run allowed commands.</pre>
              </article>
            </section>
            <section class="mapPanel">
              <div class="mapHeader">
                <h2>BlueMap</h2>
                <a id="mapLink" href="#" target="_blank" rel="noreferrer">Open map</a>
              </div>
              <iframe id="mapFrame" title="BlueMap"></iframe>
            </section>
            <footer class="pageTitle">
              <h1>HunterCore</h1>
              <p id="serverLine">Loading server status...</p>
            </footer>
          </main>
          <script src="/assets/app.js"></script>
        </body>
        </html>
        """;

    private static final String APP_CSS = """
        :root { color-scheme: dark; --bg:#0d1117; --panel:#161b22; --line:#30363d; --text:#e6edf3; --muted:#8b949e; --accent:#2f81f7; --good:#3fb950; --warn:#d29922; --bad:#f85149; }
        * { box-sizing: border-box; }
        html { min-height:100%; overflow-y:auto; scroll-behavior:smooth; }
        body { min-height:100%; margin:0; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background:var(--bg); color:var(--text); overflow-y:auto; }
        .shell { width:min(1280px, calc(100% - 32px)); margin:0 auto; padding:18px 0 36px; }
        .topbar { position:sticky; top:0; z-index:20; display:flex; justify-content:center; gap:16px; align-items:center; padding:10px 0 16px; margin-bottom:18px; background:linear-gradient(180deg, var(--bg) 0%, color-mix(in srgb, var(--bg) 92%, transparent) 100%); border-bottom:1px solid var(--line); backdrop-filter:blur(10px); }
        h1, h2, h3, p { margin:0; }
        h1 { font-size:28px; }
        h2 { font-size:16px; margin-bottom:12px; }
        h3 { color:var(--muted); font-size:12px; font-weight:700; margin:12px 0 4px; text-transform:uppercase; }
        p, .muted { color:var(--muted); }
        input, select, button { height:36px; border-radius:6px; border:1px solid var(--line); background:#0d1117; color:var(--text); padding:0 10px; }
        button { background:var(--accent); border-color:var(--accent); font-weight:650; cursor:pointer; }
        .authActions, .command { display:flex; gap:8px; flex-wrap:wrap; justify-content:center; }
        .ghostButton { background:#0d1117; border-color:var(--line); color:var(--muted); }
        .modalBackdrop { position:fixed; inset:0; z-index:80; background:rgba(1, 4, 9, .62); backdrop-filter:blur(7px); }
        .authModal { position:fixed; inset:0; z-index:90; display:grid; place-items:start center; padding:76px 16px 24px; overflow:auto; }
        .authModal[hidden], .modalBackdrop[hidden] { display:none; }
        .authBox { width:min(380px, 100%); display:grid; gap:10px; padding:16px; background:var(--panel); border:1px solid var(--line); border-radius:8px; box-shadow:0 20px 50px rgba(0,0,0,.32); }
        .authBox input, .authBox button { width:100%; }
        .metrics { display:grid; grid-template-columns:repeat(4, minmax(0,1fr)); gap:12px; margin:18px 0; }
        .metrics div, article, .mapPanel { background:var(--panel); border:1px solid var(--line); border-radius:8px; padding:14px; }
        .metrics span { display:block; color:var(--muted); font-size:12px; }
        .metrics strong { display:block; margin-top:4px; font-size:22px; }
        .grid { display:grid; grid-template-columns:repeat(2, minmax(0,1fr)); gap:12px; }
        .widePanel { grid-column:1 / -1; }
        .profileCard { display:grid; gap:14px; }
        .profileHero { display:flex; gap:14px; align-items:center; padding:12px; border:1px solid var(--line); border-radius:10px; background:linear-gradient(135deg, rgba(47,129,247,.18), rgba(63,185,80,.08)); }
        .profileHero img { width:72px; height:72px; border-radius:12px; image-rendering:pixelated; background:#0d1117; }
        .bars { display:grid; gap:8px; }
        .bar { height:10px; overflow:hidden; border:1px solid var(--line); border-radius:999px; background:#0d1117; }
        .bar span { display:block; height:100%; background:linear-gradient(90deg, var(--accent), var(--good)); }
        .inventoryGrid { display:grid; grid-template-columns:repeat(9, 42px); gap:5px; align-items:center; overflow:auto; padding-bottom:4px; }
        .slot { width:42px; height:42px; display:grid; place-items:center; border:1px solid var(--line); border-radius:6px; background:#0d1117; color:var(--muted); font-size:10px; position:relative; }
        .slot.filled { color:var(--text); border-color:color-mix(in srgb, var(--accent) 45%, var(--line)); }
        .slot small { position:absolute; right:4px; bottom:2px; margin:0; color:var(--good); }
        .sectionTitle { display:flex; justify-content:space-between; align-items:center; gap:12px; margin-bottom:12px; }
        .sectionTitle h2 { margin-bottom:0; }
        .list { display:grid; gap:8px; }
        .item { display:flex; justify-content:space-between; gap:12px; padding:8px 0; border-top:1px solid var(--line); }
        .item:first-child { border-top:0; }
        .healthBadge { border:1px solid var(--line); border-radius:999px; padding:2px 8px; font-size:12px; text-transform:uppercase; }
        .healthBadge.ok { color:var(--good); border-color:color-mix(in srgb, var(--good) 55%, var(--line)); }
        .healthBadge.warning { color:var(--warn); border-color:color-mix(in srgb, var(--warn) 55%, var(--line)); }
        .healthBadge.critical { color:var(--bad); border-color:color-mix(in srgb, var(--bad) 55%, var(--line)); }
        .healthBadge.disabled { color:var(--muted); }
        .alert.warning strong { color:var(--warn); }
        .alert.critical strong { color:var(--bad); }
        .toggle input { width:18px; height:18px; padding:0; accent-color:var(--accent); }
        .toggle input:disabled { opacity:.45; cursor:not-allowed; }
        .inlineToggle { height:36px; display:inline-flex; align-items:center; gap:6px; color:var(--muted); }
        .inlineToggle input { width:18px; height:18px; accent-color:var(--accent); }
        .commandGroup { display:grid; gap:6px; }
        .actorForm .coord { width:74px; }
        .userForm input[type="password"] { min-width:150px; }
        .userActions { display:flex; gap:6px; align-items:center; }
        small { display:block; color:var(--muted); margin-top:2px; }
        .item button { height:30px; padding:0 8px; }
        pre { min-height:72px; white-space:pre-wrap; color:var(--muted); }
        .mapPanel { margin-top:12px; padding:0; overflow:hidden; }
        .mapHeader { display:flex; justify-content:space-between; align-items:center; padding:14px; border-bottom:1px solid var(--line); }
        a { color:#58a6ff; }
        iframe { display:block; width:100%; height:min(70vh, 720px); border:0; background:#0d1117; }
        .pageTitle { display:grid; place-items:center; gap:4px; padding:20px 0 4px; text-align:center; color:var(--muted); }
        .pageTitle h1 { color:var(--text); }
        @media (max-width: 800px) {
          .topbar, .grid { grid-template-columns:1fr; display:grid; }
          .topbar { justify-items:center; }
          .metrics { grid-template-columns:repeat(2, minmax(0,1fr)); }
        }
        """;

    private static final String APP_JS = """
        const state = { session: null, csrf: '', webUsers: [], auth: {} };
        const $ = (id) => document.getElementById(id);
        const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({
          '&': '&amp;',
          '<': '&lt;',
          '>': '&gt;',
          '"': '&quot;',
          "'": '&#39;'
        })[char]);
        const item = (left, right = '') => `<div class="item"><span>${esc(left)}</span><strong>${esc(right)}</strong></div>`;
        const toggleItem = (left, checked, attrs = '', disabled = false) =>
          `<label class="item toggle"><span>${esc(left)}</span><input type="checkbox" ${checked ? 'checked' : ''} ${disabled ? 'disabled' : ''} ${attrs}></label>`;
        const severityClass = (value) => ['ok', 'warning', 'critical', 'disabled'].includes(value) ? value : 'ok';
        const alertItem = (alert) => `<div class="item alert ${severityClass(alert.severity)}"><span>${esc(alert.label)}</span><strong>${esc(alert.detail)}</strong></div>`;
        const pct = (value, max) => Math.max(0, Math.min(100, max > 0 ? (Number(value || 0) / Number(max)) * 100 : 0));
        const bar = (label, value, max) => `<div><div class="item"><span>${esc(label)}</span><strong>${esc(value)}/${esc(max)}</strong></div><div class="bar"><span style="width:${pct(value, max).toFixed(1)}%"></span></div></div>`;
        const slot = (item) => {
          if (!item || item.empty) return '<div class="slot">·</div>';
          const title = `${item.displayName || item.type || item.material} x${item.amount}`;
          return `<div class="slot filled" title="${esc(title)}">${esc((item.material || '?').split('_').map(p => p[0]).join('').slice(0, 3))}<small>${item.amount > 1 ? esc(item.amount) : ''}</small></div>`;
        };
        const grid = (items) => `<div class="inventoryGrid">${(items || []).map(slot).join('')}</div>`;
        const actorLine = (actor) => {
          const location = actor.world ? `${actor.world} ${Number(actor.x).toFixed(1)} ${Number(actor.y).toFixed(1)} ${Number(actor.z).toFixed(1)}` : 'not configured';
          return `<div class="item"><span>${esc(actor.displayName)} <small>${actor.live ? 'live' : 'configured'} · ${esc(actor.module)} · ${esc(actor.kind)} · pose: ${esc(actor.pose || 'standing')} · ${esc(location)}</small></span><button type="button" data-actor-remove="true" data-actor-module="${esc(actor.module)}" data-actor-id="${esc(actor.id)}">Remove</button></div>`;
        };
        const allowedLine = (user) => {
          if (!user.allowedCommandsConfigured) return 'inherit';
          return user.allowedCommands?.length ? user.allowedCommands.join(', ') : 'none';
        };
        const webUserLine = (user) =>
          `<div class="item"><span>${esc(user.displayName)} <small>${esc(user.role)} · ${user.passwordConfigured ? 'password set' : 'no password'} · commands ${user.commandExecution ? 'on' : 'off'} · ${esc(allowedLine(user))}</small></span><span class="userActions"><button type="button" data-user-edit="${esc(user.id)}">Edit</button><button type="button" data-user-remove="${esc(user.id)}">Remove</button></span></div>`;

        function closeAuthModals() {
          $('modalBackdrop').hidden = true;
          $('loginModal').hidden = true;
          $('registerModal').hidden = true;
          $('loginMessage').textContent = '';
          $('registerMessage').textContent = '';
        }

        function openAuthModal(kind) {
          if (state.session) {
            closeAuthModals();
            return;
          }
          $('modalBackdrop').hidden = false;
          $('loginModal').hidden = kind !== 'login';
          $('registerModal').hidden = kind !== 'register';
          if (kind === 'register' && !state.auth.webRegistrationEnabled) {
            $('registerMessage').textContent = 'Web registration is not enabled on this server.';
          }
          setTimeout(() => (kind === 'register' ? $('registerUsername') : $('username'))?.focus(), 0);
        }

        function updateAuthControls() {
          const loggedIn = Boolean(state.session);
          $('openLoginButton').hidden = loggedIn;
          $('openRegisterButton').hidden = loggedIn || !state.auth.webRegistrationEnabled;
          $('logoutButton').hidden = !loggedIn;
          if (loggedIn) closeAuthModals();
        }

        async function json(url, options = {}) {
          const headers = { ...(options.headers || {}) };
          if (state.csrf) headers['X-HunterCore-CSRF'] = state.csrf;
          if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
          const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
          return response.json();
        }

        async function refresh() {
          const data = await json('/api/status');
          state.session = data.session;
          state.csrf = data.session?.csrf || state.csrf;
          state.auth = data.auth || {};
          $('serverLine').textContent = `${data.server.name} · ${data.server.version}`;
          $('tps').textContent = Number(data.server.tps1).toFixed(2);
          $('mspt').textContent = Number(data.server.mspt).toFixed(1);
          $('players').textContent = `${data.server.online}/${data.server.maxPlayers}`;
          $('memory').textContent = data.server.memory;
          const health = data.health || { status: 'unknown', alerts: [] };
          $('healthStatus').textContent = health.status;
          $('healthStatus').className = `healthBadge ${severityClass(health.status)}`;
          $('healthList').innerHTML = health.alerts.length
            ? health.alerts.map(alertItem).join('')
            : `<p class="muted">No health alerts · Heap ${Number(health.memoryUsagePercent || 0).toFixed(1)}%</p>`;
          $('worlds').innerHTML = data.worlds.map(w => item(w.name, `${w.players} players · ${w.loadedChunks} chunks · ${w.entities} entities`)).join('');
          $('optimizationList').innerHTML = [
            item('CPU threads', data.optimization.cpuThreads),
            item('Paper workers', data.optimization.paperWorkers),
            item('Core workers', data.optimization.coreWorkers),
            item('Netty IO', data.optimization.nettyIoThreads),
            item('ForkJoin', data.optimization.forkJoinParallelism),
            item('HunterTools workers', data.optimization.hunterToolsWorkers),
            item('Web workers', data.optimization.webPanelWorkers),
            item('Guest cache', `${data.optimization.guestStatusCacheMillis}ms`)
          ].join('');
          updateAuthControls();
          if (data.players) $('playerList').innerHTML = data.players.map(p => item(p.name, `${p.world} · ${p.ping}ms`)).join('') || '<p class="muted">No players online.</p>';
          if (data.plugins) $('pluginList').innerHTML = data.plugins.map(p => item(p.name, p.enabled ? p.version : 'disabled')).join('');
          renderPlayerProfile();
          renderAdminDashboard(data);
          renderActorWorlds(data.worlds || []);
          renderActors(data.actorDetails || []);
          renderOperations(data.modules || []);
          renderWebUsers(data.webUsers || []);
        }

        async function renderPlayerProfile() {
          if (!state.session) {
            $('profileCard').innerHTML = 'Login to view your character, inventory, ender chest, and level.';
            return;
          }
          const [profile, inventory, ender] = await Promise.all([
            json('/api/player/self'),
            json('/api/player/self/inventory'),
            json('/api/player/self/enderchest')
          ]);
          if (!profile.ok) {
            $('profileCard').innerHTML = '<p class="muted">Your web account is not linked to an online player right now.</p>';
            return;
          }
          const p = profile.player;
          $('profileCard').innerHTML = `
            <div class="profileHero">
              <img src="${esc(p.skinAvatarUrl)}" alt="">
              <div>
                <h2>${esc(p.name)}</h2>
                <p>${esc(p.world)} · ${Number(p.x).toFixed(1)}, ${Number(p.y).toFixed(1)}, ${Number(p.z).toFixed(1)} · ${esc(p.gameMode)} · ${p.ping}ms</p>
                <strong>Level ${esc(p.level)} · XP ${(Number(p.expProgress || 0) * 100).toFixed(0)}%</strong>
              </div>
            </div>
            <div class="bars">
              ${bar('Health', Number(p.health).toFixed(0), Number(p.maxHealth).toFixed(0))}
              ${bar('Food', p.food, 20)}
              ${bar('XP', Math.round(Number(p.expProgress || 0) * 100), 100)}
            </div>
            <h3>Inventory</h3>${grid(inventory.inventory?.storage || [])}
            <h3>Armor / Offhand</h3>${grid([...(inventory.inventory?.armor || []), ...(inventory.inventory?.extra || [])])}
            <h3>Ender Chest</h3>${grid(ender.enderChest || [])}
          `;
        }

        function renderAdminDashboard(data) {
          const admin = Boolean(state.session?.admin);
          $('adminDashboardPanel').hidden = !admin;
          if (!admin) return;
          const disabled = (data.plugins || []).filter(p => !p.enabled).length;
          $('adminDashboard').innerHTML = [
            item('Server', `${data.server.name} · ${data.server.version}`),
            item('Performance', `${Number(data.server.tps1).toFixed(2)} TPS · ${Number(data.server.mspt).toFixed(1)} MSPT`),
            item('Memory', data.server.memory),
            item('Worlds', (data.worlds || []).length),
            item('Plugins', `${(data.plugins || []).length} installed · ${disabled} disabled`),
            item('Actors', `${data.actors.fakePlayers} fake · ${data.actors.realFakePlayers} real fake · ${data.actors.npcs} NPC`),
            item('Health', data.health?.status || 'unknown')
          ].join('');
        }

        function renderActorWorlds(worlds) {
          const selected = $('actorWorld').value;
          const names = worlds.map(world => world.name);
          $('actorWorld').innerHTML = '<option value="">spawn</option>' + names.map(name => `<option value="${esc(name)}">${esc(name)}</option>`).join('');
          if (names.includes(selected)) $('actorWorld').value = selected;
        }

        function renderActors(actors) {
          const admin = Boolean(state.session?.admin);
          $('actorPanel').hidden = !admin;
          if (!admin) return;
          $('actorList').classList.remove('muted');
          $('actorList').innerHTML = actors.length ? actors.map(actorLine).join('') : '<p class="muted">No configured actors.</p>';
        }

        function renderOperations(modules) {
          const admin = Boolean(state.session?.admin && modules.length);
          $('opsPanel').hidden = !admin;
          if (!admin) return;
          $('moduleControls').innerHTML = modules
            .map(module => toggleItem(module.name, module.enabled, `data-module="${esc(module.name)}"`, !module.toggleable))
            .join('');
          $('commandControls').innerHTML = modules
            .filter(module => module.commands?.length)
            .map(module => `<div class="commandGroup"><h3>${esc(module.name)}</h3>${module.commands
              .map(command => toggleItem(command.name, command.enabled, `data-command-module="${esc(module.name)}" data-command="${esc(command.name)}"`))
              .join('')}</div>`)
            .join('');
        }

        function renderWebUsers(users) {
          const admin = Boolean(state.session?.admin);
          $('usersPanel').hidden = !admin;
          state.webUsers = users;
          if (!admin) return;
          $('webUserList').classList.remove('muted');
          $('webUserList').innerHTML = users.length ? users.map(webUserLine).join('') : '<p class="muted">No web users.</p>';
        }

        function editWebUser(id) {
          const user = state.webUsers.find(candidate => candidate.id === id);
          if (!user) return;
          $('webUserName').value = user.displayName;
          $('webUserRole').value = user.role;
          $('webUserPassword').value = '';
          $('webUserCommandExecution').checked = Boolean(user.commandExecution);
          $('webUserAllowedMode').value = user.allowedCommandsConfigured
            ? (user.allowedCommands?.length ? 'custom' : 'none')
            : 'inherit';
          $('webUserAllowedCommands').value = user.allowedCommands?.join(', ') || '';
        }

        function updateActorKind() {
          const fake = $('actorModule').value === 'fake-players';
          $('actorKind').disabled = fake;
          if (fake) $('actorKind').value = 'mannequin';
        }

        async function refreshMap() {
          const map = await json('/api/map');
          if (map.ok) {
            $('mapLink').href = map.url;
            $('mapFrame').src = map.url;
          }
        }

        $('openLoginButton').addEventListener('click', () => openAuthModal('login'));
        $('openRegisterButton').addEventListener('click', () => openAuthModal('register'));
        $('modalBackdrop').addEventListener('click', closeAuthModals);
        document.querySelectorAll('[data-close-modal="true"]').forEach(button => button.addEventListener('click', closeAuthModals));
        window.addEventListener('keydown', event => {
          if (event.key === 'Escape') closeAuthModals();
        });

        $('loginForm').addEventListener('submit', async (event) => {
          event.preventDefault();
          const payload = JSON.stringify({ username: $('username').value, password: $('password').value });
          const result = await json('/api/login', { method: 'POST', body: payload });
          $('password').value = '';
          if (result.ok) {
            state.session = result.session;
            state.csrf = result.session.csrf || '';
            closeAuthModals();
          }
          $('loginMessage').textContent = result.ok ? `Logged in as ${result.session.username}.` : 'Login failed.';
          $('commandResult').textContent = result.ok ? `Logged in as ${result.session.username} (${result.session.role}).` : 'Login failed.';
          await refresh();
        });

        $('registerForm').addEventListener('submit', async (event) => {
          event.preventDefault();
          if (!state.auth.webRegistrationEnabled) {
            $('registerMessage').textContent = 'Web registration is not enabled on this server.';
            return;
          }
          const username = $('registerUsername').value;
          const password = $('registerPassword').value;
          const confirmPassword = $('registerPasswordConfirm').value;
          const minimum = Number(state.auth.minimumPasswordLength || 4);
          if (password.length < minimum || password !== confirmPassword) {
            $('registerMessage').textContent = `Password must be at least ${minimum} characters and match confirmation.`;
            return;
          }
          const result = await json('/api/auth/register', {
            method: 'POST',
            body: JSON.stringify({ username, password, confirmPassword })
          });
          if (!result.ok) {
            $('registerMessage').textContent = `Register failed: ${result.error || 'unknown error'}`;
            return;
          }
          const login = await json('/api/login', {
            method: 'POST',
            body: JSON.stringify({ username, password })
          });
          $('registerPassword').value = '';
          $('registerPasswordConfirm').value = '';
          if (login.ok) {
            state.session = login.session;
            state.csrf = login.session.csrf || '';
            closeAuthModals();
            $('commandResult').textContent = `Registered and logged in as ${login.session.username}.`;
          } else {
            $('registerMessage').textContent = 'Registered. Please log in.';
            openAuthModal('login');
          }
          await refresh();
        });

        $('logoutButton').addEventListener('click', async () => {
          await json('/api/logout', { method: 'POST' });
          state.session = null;
          state.csrf = '';
          closeAuthModals();
          $('commandResult').textContent = 'Logged out.';
          await refresh();
        });

        $('commandForm').addEventListener('submit', async (event) => {
          event.preventDefault();
          const payload = JSON.stringify({ command: $('commandInput').value });
          const result = await json('/api/command', { method: 'POST', body: payload });
          $('commandResult').textContent = result.ok ? `${result.message}${result.output ? `\\n\\n${result.output}` : ''}` : `Error: ${result.error}`;
          await refresh();
        });

        $('actorModule').addEventListener('change', updateActorKind);

        $('actorForm').addEventListener('submit', async (event) => {
          event.preventDefault();
          const payload = {
            module: $('actorModule').value,
            kind: $('actorKind').value,
            name: $('actorName').value
          };
          if ($('actorWorld').value || $('actorX').value || $('actorY').value || $('actorZ').value) {
            payload.world = $('actorWorld').value;
            payload.x = $('actorX').value;
            payload.y = $('actorY').value;
            payload.z = $('actorZ').value;
          }
          const result = await json('/api/admin/actor/spawn', { method: 'POST', body: JSON.stringify(payload) });
          $('commandResult').textContent = result.ok ? `${result.message}${result.output ? `\\n\\n${result.output}` : ''}` : `Error: ${result.error}`;
          if (result.ok) $('actorName').value = '';
          await refresh();
        });

        $('actorList').addEventListener('click', async (event) => {
          const target = event.target;
          if (!(target instanceof HTMLButtonElement) || !target.dataset.actorRemove) return;
          const payload = { module: target.dataset.actorModule, id: target.dataset.actorId };
          const result = await json('/api/admin/actor/remove', { method: 'POST', body: JSON.stringify(payload) });
          $('commandResult').textContent = result.ok ? `${result.message}${result.output ? `\\n\\n${result.output}` : ''}` : `Error: ${result.error}`;
          await refresh();
        });

        $('webUserForm').addEventListener('submit', async (event) => {
          event.preventDefault();
          const payload = {
            username: $('webUserName').value,
            role: $('webUserRole').value,
            password: $('webUserPassword').value,
            commandExecution: String($('webUserCommandExecution').checked),
            allowedCommandsMode: $('webUserAllowedMode').value,
            allowedCommands: $('webUserAllowedCommands').value
          };
          const result = await json('/api/admin/web-user/save', { method: 'POST', body: JSON.stringify(payload) });
          $('commandResult').textContent = result.ok ? 'Web user saved.' : `Error: ${result.error}`;
          if (result.ok) $('webUserPassword').value = '';
          await refresh();
        });

        $('webUserList').addEventListener('click', async (event) => {
          const target = event.target;
          if (!(target instanceof HTMLButtonElement)) return;
          if (target.dataset.userEdit) {
            editWebUser(target.dataset.userEdit);
            return;
          }
          if (!target.dataset.userRemove) return;
          const result = await json('/api/admin/web-user/remove', {
            method: 'POST',
            body: JSON.stringify({ username: target.dataset.userRemove })
          });
          $('commandResult').textContent = result.ok ? 'Web user removed.' : `Error: ${result.error}`;
          await refresh();
        });

        $('opsPanel').addEventListener('change', async (event) => {
          const target = event.target;
          if (!(target instanceof HTMLInputElement)) return;
          const endpoint = target.dataset.module ? '/api/admin/module' : '/api/admin/command';
          const payload = target.dataset.module
            ? { module: target.dataset.module, enabled: String(target.checked) }
            : { module: target.dataset.commandModule, command: target.dataset.command, enabled: String(target.checked) };
          const result = await json(endpoint, { method: 'POST', body: JSON.stringify(payload) });
          $('commandResult').textContent = result.ok ? `${result.message}${result.output ? `\\n\\n${result.output}` : ''}` : `Error: ${result.error}`;
          if (!result.ok) target.checked = !target.checked;
          await refresh();
        });

        refresh()
          .then(() => {
            if (location.hash === '#register') openAuthModal('register');
            if (location.hash === '#login') openAuthModal('login');
          })
          .catch(err => $('serverLine').textContent = err.message);
        refreshMap().catch(() => {});
        updateActorKind();
        setInterval(refresh, 5000);
        """;
}
