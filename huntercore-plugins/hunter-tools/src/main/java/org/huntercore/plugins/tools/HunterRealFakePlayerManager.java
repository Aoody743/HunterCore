package org.huntercore.plugins.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.huntercore.api.HunterCoreProvider;
import org.huntercore.api.fakeplayer.FakePlayerActionResult;
import org.huntercore.api.fakeplayer.FakePlayerSnapshot;
import org.huntercore.api.fakeplayer.HunterFakePlayerService;
import org.jetbrains.annotations.Nullable;

final class HunterRealFakePlayerManager {
    private static final String MODULE = "real-fake-players";
    private static final HttpClient SKIN_HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    private static final Set<Material> HIGH_RISK_MATERIALS = Set.of(
        Material.TNT,
        Material.TNT_MINECART,
        Material.END_CRYSTAL,
        Material.FLINT_AND_STEEL,
        Material.FIRE_CHARGE,
        Material.LAVA_BUCKET,
        Material.RESPAWN_ANCHOR,
        Material.WITHER_SKELETON_SKULL
    );

    private final HunterToolsPlugin plugin;
    private final HunterToolsPreferences preferences;
    private final HunterGameplayRuleManager gameplayRuleManager;
    private final Map<String, BukkitTask> loops = new HashMap<>();
    private final Map<String, String> clickCommands = new HashMap<>();
    private final Map<String, FakeAiProfile> aiProfiles = new HashMap<>();
    private final Map<String, BukkitTask> aiTasks = new HashMap<>();
    private final Map<String, String> aiLastActions = new HashMap<>();
    private final Map<String, String> aiLastResponseFingerprints = new HashMap<>();
    private final Map<String, RecentFakeAiLine> recentFakeAiLines = new HashMap<>();
    private final Map<String, PendingRiskApproval> pendingRiskApprovals = new HashMap<>();
    private final Map<String, Long> chatControlCooldowns = new HashMap<>();
    private final Map<String, Long> recentChatTaskFingerprints = new HashMap<>();
    private final Map<String, List<QuickActionLock>> quickActionLocks = new HashMap<>();
    private final Set<String> aiFreePlayers = new HashSet<>();
    private final Map<String, List<BuildStep>> buildPlans = new HashMap<>();
    private final Map<String, List<BuildStep>> activeBuildPlacements = new HashMap<>();
    private final Map<String, Deque<List<BuildStep>>> buildHistory = new HashMap<>();
    private final List<String> aiBusy = new ArrayList<>();
    private final Deque<ObservedChat> recentChat = new ArrayDeque<>();
    private HunterAiManager aiManager;

    HunterRealFakePlayerManager(
        final HunterToolsPlugin plugin,
        final HunterToolsPreferences preferences,
        final HunterAiManager aiManager,
        final HunterGameplayRuleManager gameplayRuleManager
    ) {
        this.plugin = plugin;
        this.preferences = preferences;
        this.aiManager = aiManager;
        this.gameplayRuleManager = gameplayRuleManager;
    }

    void setAiManager(final HunterAiManager aiManager) {
        this.aiManager = aiManager;
    }

    void shutdown() {
        this.stopAllAi();
        this.stopAllLoops();
        if (this.preferences.booleanValue("modules.real-fake-players.remove-on-disable", true)) {
            this.service().removeAll();
        }
        this.clickCommands.clear();
        this.recentChatTaskFingerprints.clear();
        this.recentFakeAiLines.clear();
        this.quickActionLocks.clear();
        this.aiFreePlayers.clear();
        this.buildPlans.clear();
        this.activeBuildPlacements.clear();
        this.buildHistory.clear();
    }

    int liveCount() {
        return this.service().list().size();
    }

    boolean command(final CommandSender sender, final String label, final String[] args) {
        if (!this.preferences.moduleEnabled(MODULE)) {
            sender.sendMessage("HunterCore real fake players module is disabled in preferences.yml.");
            return true;
        }
        if (args.length == 0) {
            this.usage(sender, label);
            return true;
        }
        final String sub = HunterToolsPreferences.normalize(args[0]);
        final String command = switch (sub) {
            case "come", "here" -> "tphere";
            default -> sub;
        };
        if (!this.preferences.commandEnabled(MODULE, command)) {
            sender.sendMessage("HunterCore real fake players command " + command + " is disabled in preferences.yml.");
            return true;
        }
        return switch (sub) {
            case "spawn" -> this.spawn(sender, label, args);
            case "respawn" -> this.respawn(sender, label, args);
            case "remove" -> this.remove(sender, label, args);
            case "list" -> this.list(sender);
            case "inv", "inventory" -> this.inventory(sender, label, args);
            case "skin" -> this.skin(sender, label, args);
            case "tp" -> this.teleport(sender, label, args);
            case "tphere", "come", "here" -> this.teleportHere(sender, label, args);
            case "look" -> this.look(sender, label, args);
            case "move" -> this.move(sender, label, args);
            case "sneak" -> this.toggle(sender, label, args, "sneak");
            case "sprint" -> this.toggle(sender, label, args, "sprint");
            case "jump" -> this.repeating(sender, label, args, "jump");
            case "use" -> this.repeating(sender, label, args, "use");
            case "attack" -> this.repeating(sender, label, args, "attack");
            case "stop" -> this.stop(sender, label, args);
            case "click" -> this.clickCommand(sender, label, args);
            case "drop" -> this.drop(sender, label, args, false);
            case "dropstack" -> this.drop(sender, label, args, true);
            case "swap" -> this.swap(sender, label, args);
            case "gm", "gamemode" -> this.gameMode(sender, label, args);
            case "slot" -> this.slot(sender, label, args);
            case "ai" -> this.ai(sender, label, args);
            case "info" -> this.info(sender, label, args);
            case "clear" -> this.clear(sender);
            default -> {
                this.usage(sender, label);
                yield true;
            }
        };
    }

    List<String> completions(final String[] args) {
        if (args.length == 1) {
            final List<String> commands = new ArrayList<>(HunterToolsPreferences.realFakePlayerCommands());
            commands.add("help");
            commands.add("come");
            commands.add("here");
            return matching(args[0], commands);
        }
        final String sub = HunterToolsPreferences.normalize(args[0]);
        if (args.length == 2 && sub.equals("help")) {
            return matching(args[1], HunterToolsPreferences.realFakePlayerCommands());
        }
        if (args.length == 2 && List.of(
            "respawn", "remove", "inv", "inventory", "skin", "tp", "tphere", "come", "here", "look", "move", "sneak", "sprint", "jump", "use", "attack", "stop",
            "click", "drop", "dropstack", "swap", "gm", "gamemode", "slot", "ai", "info"
        ).contains(sub)) {
            return matching(args[1], this.names());
        }
        if (args.length == 3 && List.of("use", "attack", "jump").contains(sub)) {
            return matching(args[2], List.of("once", "continuous", "stop"));
        }
        if (args.length == 3 && List.of("sneak", "sprint").contains(sub)) {
            return matching(args[2], List.of("on", "off"));
        }
        if (args.length == 3 && sub.equals("look")) {
            return matching(args[2], List.of("north", "south", "east", "west", "up", "down"));
        }
        if (args.length == 3 && List.of("gm", "gamemode").contains(sub)) {
            return matching(args[2], List.of("survival", "creative", "adventure", "spectator"));
        }
        if (args.length == 3 && sub.equals("slot")) {
            return matching(args[2], List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"));
        }
        if (args.length == 3 && sub.equals("move")) {
            return matching(args[2], List.of("forward", "back", "left", "right", "stop", "1", "-1"));
        }
        if (args.length == 3 && sub.equals("ai")) {
            return matching(args[2], List.of("status", "on", "off", "goal", "once", "approve", "deny", "rules"));
        }
        if (args.length == 4 && sub.equals("ai") && HunterToolsPreferences.normalize(args[2]).equals("rules")) {
            return matching(args[3], List.of("list", "clear"));
        }
        if (sub.equals("spawn") && args.length == 3) {
            final List<String> values = new ArrayList<>(Bukkit.getWorlds().stream().map(World::getName).toList());
            values.add("-aifree");
            return matching(args[2], values);
        }
        if (sub.equals("tp") && args.length == 3) {
            return matching(args[2], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        return List.of();
    }

    private boolean spawn(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " spawn <name> [-aifree] [world x y z [yaw pitch]]");
            return true;
        }
        final boolean aiFree = hasAiFreeFlag(args);
        if (aiFree && sender instanceof final Player player && !player.isOp()) {
            sender.sendMessage(ChatColor.RED + "AI-Free mode is dangerous and can only be created by OP players or console.");
            return true;
        }
        final int max = Math.max(1, this.preferences.intValue("modules.real-fake-players.max-active", 16));
        if (this.liveCount() >= max) {
            sender.sendMessage("HunterCore real fake player limit reached: " + max);
            return true;
        }
        final String[] locationArgs = aiFree ? removeAiFreeFlags(args) : args;
        final Location location = this.location(sender, locationArgs, 2);
        if (location == null) {
            return true;
        }
        final FakePlayerActionResult result = this.service().spawn(args[1], location);
        this.send(sender, result);
        if (result.success()) {
            final HunterToolsPreferences.FakeAiPersonaProfile persona = this.fakeAiPersona(args[1]);
            if (persona != null) {
                sender.sendMessage(ChatColor.AQUA + "Matched AI persona " + persona.displayName() + " for fake player " + args[1] + ".");
            }
        }
        if (result.success() && aiFree) {
            this.enableAiFree(args[1], sender);
        }
        return true;
    }

    private boolean respawn(final CommandSender sender, final String label, final String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " respawn <name>");
            return true;
        }
        this.send(sender, this.service().respawn(args[1]));
        return true;
    }

    private boolean remove(final CommandSender sender, final String label, final String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " remove <name>");
            return true;
        }
        this.stopAi(args[1]);
        this.stopLoops(args[1]);
        this.clickCommands.remove(playerId(args[1]));
        this.aiFreePlayers.remove(playerId(args[1]));
        this.send(sender, this.service().remove(args[1]));
        return true;
    }

    private boolean list(final CommandSender sender) {
        final List<FakePlayerSnapshot> snapshots = new ArrayList<>(this.service().list());
        snapshots.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        sender.sendMessage(ChatColor.GOLD + "HunterCore real fake players: " + ChatColor.WHITE + snapshots.size() + " live");
        for (final FakePlayerSnapshot snapshot : snapshots) {
            sender.sendMessage("- " + snapshot.name()
                + ": " + snapshot.gameMode().name().toLowerCase(Locale.ROOT)
                + ", " + locationLine(snapshot.location())
                + ", sneaking=" + snapshot.sneaking()
                + ", sprinting=" + snapshot.sprinting()
                + ", loops=" + this.loopLine(snapshot.name()));
        }
        return true;
    }

    private boolean inventory(final CommandSender sender, final String label, final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("Usage: /" + label + " inv <name> must be run by a player.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " inv <name>");
            return true;
        }
        this.send(sender, this.service().openInventoryEditor(args[1], player));
        return true;
    }

    private boolean teleport(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " tp <name> [world x y z [yaw pitch]]");
            return true;
        }
        final Location location = this.location(sender, args, 2);
        if (location == null) {
            return true;
        }
        this.send(sender, this.service().teleport(args[1], location));
        return true;
    }

    private boolean teleportHere(final CommandSender sender, final String label, final String[] args) {
        if (!(sender instanceof final Player player)) {
            sender.sendMessage("Usage: /" + label + " tphere <name> must be run by a player.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " tphere <name>");
            return true;
        }
        this.send(sender, this.service().teleport(args[1], player.getLocation()));
        return true;
    }

    private boolean look(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2 || args.length > 4) {
            sender.sendMessage("Usage: /" + label + " look <name> [yaw pitch|north|south|east|west|up|down]");
            return true;
        }
        final float[] rotation = this.rotation(sender, label, args, 2);
        if (rotation == null) {
            return true;
        }
        this.send(sender, this.service().look(args[1], rotation[0], rotation[1]));
        return true;
    }

    private boolean move(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 3 || args.length > 6) {
            sender.sendMessage("Usage: /" + label + " move <name> <forward|back|left|right|stop|forwardValue> [sidewaysValue] [ticks] [jump]");
            return true;
        }
        final MovePlan plan = movePlan(String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)));
        if (plan == null) {
            sender.sendMessage("Move must be forward/back/left/right/stop or numeric forward/sideways values.");
            return true;
        }
        if (plan.ticks() <= 1) {
            this.send(sender, this.service().move(args[1], plan.forward(), plan.sideways(), plan.jump(), plan.sprinting(), plan.sneaking()));
            return true;
        }
        this.startMoveLoop(args[1], plan);
        sender.sendMessage("Started move loop for " + args[1] + " (" + plan.ticks() + " ticks).");
        return true;
    }

    private boolean toggle(final CommandSender sender, final String label, final String[] args, final String action) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " " + action + " <name> <on|off>");
            return true;
        }
        final Boolean enabled = parseToggle(args[2]);
        if (enabled == null) {
            sender.sendMessage("Use on/off.");
            return true;
        }
        final FakePlayerActionResult result = action.equals("sneak")
            ? this.service().setSneaking(args[1], enabled)
            : this.service().setSprinting(args[1], enabled);
        this.send(sender, result);
        return true;
    }

    private boolean repeating(final CommandSender sender, final String label, final String[] args, final String action) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("Usage: /" + label + " " + action + " <name> [once|continuous|stop]");
            return true;
        }
        final String mode = args.length == 3 ? HunterToolsPreferences.normalize(args[2]) : "once";
        if (mode.equals("stop")) {
            this.stopLoop(args[1], action);
            sender.sendMessage("Stopped " + action + " loop for " + args[1] + ".");
            return true;
        }
        if (mode.equals("continuous")) {
            this.startLoop(sender, args[1], action);
            return true;
        }
        if (!mode.equals("once")) {
            sender.sendMessage("Mode must be once, continuous, or stop.");
            return true;
        }
        this.send(sender, this.runAction(action, args[1]));
        return true;
    }

    private boolean stop(final CommandSender sender, final String label, final String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " stop <name>");
            return true;
        }
        this.stopLoops(args[1]);
        this.send(sender, this.service().stopActions(args[1]));
        return true;
    }

    private boolean clickCommand(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " click <name> [command|clear]");
            return true;
        }
        final var snapshot = this.service().snapshot(args[1]);
        if (snapshot.isEmpty()) {
            sender.sendMessage("Fake player not found: " + args[1]);
            return true;
        }
        final String id = snapshot.get().id();
        if (args.length == 2) {
            final String command = this.clickCommands.getOrDefault(id, "");
            sender.sendMessage("HunterCore real fake player " + snapshot.get().name() + " click command: "
                + (command.isBlank() ? "not configured" : command));
            return true;
        }
        final String command = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim();
        final String savedCommand = List.of("clear", "none", "off", "-").contains(HunterToolsPreferences.normalize(command)) ? "" : sanitizeCommand(command);
        if (!this.setClickCommand(args[1], savedCommand)) {
            sender.sendMessage("Fake player not found: " + args[1]);
            return true;
        }
        sender.sendMessage("HunterCore real fake player " + snapshot.get().name() + " click command "
            + (savedCommand.isBlank() ? "cleared." : "set."));
        return true;
    }

    private boolean drop(final CommandSender sender, final String label, final String[] args, final boolean stack) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " " + (stack ? "dropstack" : "drop") + " <name>");
            return true;
        }
        this.send(sender, this.service().dropSelected(args[1], stack));
        return true;
    }

    private boolean swap(final CommandSender sender, final String label, final String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /" + label + " swap <name>");
            return true;
        }
        this.send(sender, this.service().swapHands(args[1]));
        return true;
    }

    private boolean gameMode(final CommandSender sender, final String label, final String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " gm <name> <survival|creative|adventure|spectator>");
            return true;
        }
        final GameMode mode = parseGameMode(args[2]);
        if (mode == null) {
            sender.sendMessage("Game mode must be survival, creative, adventure, or spectator.");
            return true;
        }
        this.send(sender, this.service().setGameMode(args[1], mode));
        return true;
    }

    private boolean slot(final CommandSender sender, final String label, final String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " slot <name> <1-9>");
            return true;
        }
        try {
            this.send(sender, this.service().setSelectedSlot(args[1], Integer.parseInt(args[2])));
        } catch (final NumberFormatException ex) {
            sender.sendMessage("Hotbar slot must be 1-9.");
        }
        return true;
    }

    private boolean ai(final CommandSender sender, final String label, final String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " ai <name> [status|on|off|goal <text>|once]");
            return true;
        }
        final var snapshot = this.service().snapshot(args[1]);
        if (snapshot.isEmpty()) {
            sender.sendMessage("Fake player not found: " + args[1]);
            return true;
        }
        final FakePlayerSnapshot fake = snapshot.get();
        final FakeAiProfile profile = this.aiProfiles.get(fake.id());
        if (args.length == 2 || args[2].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "HunterCore real fake player AI " + fake.name());
            sender.sendMessage(ChatColor.GRAY + "Enabled: " + ChatColor.WHITE + (profile != null && profile.enabled()));
            sender.sendMessage(ChatColor.GRAY + "AI-Free: " + ChatColor.WHITE + this.isAiFree(fake.id()));
            final HunterToolsPreferences.FakeAiPersonaProfile persona = this.fakeAiPersona(fake.name());
            if (persona != null) {
                sender.sendMessage(ChatColor.GRAY + "Persona: " + ChatColor.WHITE + persona.displayName());
            }
            sender.sendMessage(ChatColor.GRAY + "Goal: " + ChatColor.WHITE + this.defaultGoal(fake.name(), this.isAiFree(fake.id()), profile == null ? "" : profile.goal()));
            sender.sendMessage(ChatColor.GRAY + "Last action: " + ChatColor.WHITE + this.aiLastActions.getOrDefault(fake.id(), "idle"));
            return true;
        }
        final String action = HunterToolsPreferences.normalize(args[2]);
        switch (action) {
            case "on", "enable", "enabled", "true" -> {
                final String goal = args.length >= 4
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)).trim()
                    : this.defaultGoal(fake.name(), false, profile == null ? "" : profile.goal());
                this.setAi(fake.name(), true, goal);
                sender.sendMessage("HunterCore AI control enabled for " + fake.name() + ".");
                return true;
            }
            case "off", "disable", "disabled", "false" -> {
                this.setAi(fake.name(), false, "");
                this.aiFreePlayers.remove(fake.id());
                sender.sendMessage("HunterCore AI control disabled for " + fake.name() + ".");
                return true;
            }
            case "goal" -> {
                if (args.length < 4) {
                    sender.sendMessage("Usage: /" + label + " ai <name> goal <text>");
                    return true;
                }
                final String goal = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)).trim();
                this.setAi(fake.name(), profile != null && profile.enabled(), goal);
                sender.sendMessage("HunterCore AI goal updated for " + fake.name() + ".");
                return true;
            }
            case "once" -> {
                final String goal = this.defaultGoal(fake.name(), this.isAiFree(fake.id()), profile == null ? "" : profile.goal());
                final boolean shortcutApplied = this.quickResponseEnabled() && this.applyQuickGoalTask(fake, goal);
                this.aiLastResponseFingerprints.remove(fake.id());
                this.aiProfiles.put(fake.id(), new FakeAiProfile(
                    fake.id(),
                    fake.name(),
                    !shortcutApplied && profile != null && profile.enabled(),
                    goal,
                    profile == null ? null : profile.controllerUuid(),
                    profile == null ? null : profile.controllerName(),
                    profile == null ? 0L : profile.highRiskAllowedUntilMillis(),
                    shortcutApplied ? 0 : profile == null ? -1 : profile.remainingTurns()
                ));
                if (shortcutApplied) {
                    this.aiLastActions.put(fake.id(), "quick local task: " + truncatePlain(goal, 80));
                    sender.sendMessage("HunterCore AI quick task applied for " + fake.name() + ".");
                } else {
                    this.requestAi(fake.id(), true);
                    sender.sendMessage("HunterCore AI one-shot requested for " + fake.name() + ".");
                }
                return true;
            }
            case "approve" -> {
                return this.approveRisk(sender, fake.name());
            }
            case "deny" -> {
                return this.denyRisk(sender, fake.name());
            }
            case "rules", "rule" -> {
                if (args.length >= 4 && List.of("clear", "restore", "reset").contains(HunterToolsPreferences.normalize(args[3]))) {
                    sender.sendMessage(this.gameplayRuleManager.clear());
                    return true;
                }
                sender.sendMessage(ChatColor.GRAY + "Temporary gameplay rules: " + ChatColor.WHITE + this.gameplayRuleManager.summary());
                sender.sendMessage(ChatColor.GRAY + "Restore with /" + label + " ai " + fake.name() + " rules clear");
                return true;
            }
            default -> {
                sender.sendMessage("Usage: /" + label + " ai <name> [status|on|off|goal <text>|once|approve|deny|rules clear]");
                return true;
            }
        }
    }

    private boolean approveRisk(final CommandSender sender, final String name) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            sender.sendMessage("Fake player not found: " + name);
            return true;
        }
        final String key = snapshot.get().id();
        final PendingRiskApproval pending = this.pendingRiskApprovals.get(key);
        if (pending == null || pending.expiresAtMillis() <= System.currentTimeMillis()) {
            this.pendingRiskApprovals.remove(key);
            sender.sendMessage("No pending high-risk AI action for " + name + ".");
            return true;
        }
        final FakeAiProfile profile = this.aiProfiles.get(key);
        final long allowedUntil = System.currentTimeMillis() + Math.max(15, this.preferences.intValue("modules.ai.fake-players.high-risk-approval-window-seconds", 120)) * 1000L;
        if (profile != null) {
            this.aiProfiles.put(key, new FakeAiProfile(
                profile.id(), profile.name(), profile.enabled(), profile.goal(),
                profile.controllerUuid(), profile.controllerName(), allowedUntil, profile.remainingTurns()
            ));
        }
        this.pendingRiskApprovals.remove(key);
        sender.sendMessage(ChatColor.GREEN + "Approved one high-risk AI action for " + name + ".");
        return true;
    }

    private boolean denyRisk(final CommandSender sender, final String name) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            sender.sendMessage("Fake player not found: " + name);
            return true;
        }
        final PendingRiskApproval removed = this.pendingRiskApprovals.remove(snapshot.get().id());
        if (removed == null) {
            sender.sendMessage("No pending high-risk AI action for " + name + ".");
            return true;
        }
        this.stopLoops(name);
        this.service().stopActions(name);
        sender.sendMessage(ChatColor.YELLOW + "Denied high-risk AI action for " + name + ".");
        return true;
    }

    private boolean skin(final CommandSender sender, final String label, final String[] args) {
        if (args.length != 3) {
            sender.sendMessage("Usage: /" + label + " skin <name> <minecraftName|clear>");
            return true;
        }
        final String name = args[1];
        final String source = args[2].trim();
        if (List.of("clear", "none", "off", "-").contains(HunterToolsPreferences.normalize(source))) {
            this.send(sender, this.service().setSkinProfile(name, null));
            return true;
        }
        if (source.contains("/") || source.contains("\\") || source.toLowerCase(Locale.ROOT).endsWith(".png")) {
            sender.sendMessage("Local skin image files are not directly supported yet. Use an official Minecraft player name, or clear.");
            return true;
        }
        sender.sendMessage("Loading real fake player skin " + source + " for " + name + ".");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> fetchMojangSkinTexture(source)).thenAccept(texture -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            try {
                this.send(sender, this.service().setSkinTexture(name, texture.value(), texture.signature()));
            } catch (final RuntimeException ex) {
                sender.sendMessage(ChatColor.RED + "Skin apply failed: " + cleanError(ex));
                this.plugin.getLogger().warning("HunterCore real fake player skin apply failed for " + name + ": " + cleanError(ex));
            }
        })).exceptionally(error -> {
            sender.sendMessage(ChatColor.RED + "Skin load failed: " + cleanError(error));
            return null;
        });
        return true;
    }

    private boolean info(final CommandSender sender, final String label, final String[] args) {
        if (args.length > 2) {
            sender.sendMessage("Usage: /" + label + " info [name]");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage(ChatColor.GOLD + "HunterCore real fake players");
            sender.sendMessage("- True ServerPlayer instances: online list, chunk loading, player events and plugin visibility.");
            sender.sendMessage("- Commands: spawn, remove, list, inv, skin, tp, tphere/come, look, move, sneak, sprint, jump, use, attack, stop, click, drop, dropstack, swap, gm, slot, ai, info, clear.");
            sender.sendMessage("- Dangerous OP-only mode: /" + label + " spawn <name> -aifree lets AI act autonomously and run server commands.");
            sender.sendMessage("- use/attack/jump support once, continuous, and stop. AI can drive look, move, mine, place, use, slot and toggles.");
            sender.sendMessage("- Click command placeholders: %player%, %player_uuid%, %actor%, %actor_name%, %actor_uuid%, %module%, %world%, %x%, %y%, %z%.");
            return true;
        }
        final String name = args[1];
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            sender.sendMessage("Fake player not found: " + name);
            return true;
        }
        final FakePlayerSnapshot view = snapshot.get();
        sender.sendMessage(ChatColor.GOLD + "HunterCore real fake player " + view.name());
        sender.sendMessage("- uuid: " + view.uuid());
        sender.sendMessage("- game mode: " + view.gameMode().name().toLowerCase(Locale.ROOT));
        sender.sendMessage("- location: " + locationLine(view.location()));
        sender.sendMessage("- sneaking: " + view.sneaking() + ", sprinting: " + view.sprinting() + ", using item: " + view.usingItem());
        sender.sendMessage("- loops: " + this.loopLine(view.name()));
        sender.sendMessage("- click command: " + this.clickCommands.getOrDefault(view.id(), "not configured"));
        sender.sendMessage("- AI-Free: " + this.isAiFree(view.id()));
        final FakeAiProfile profile = this.aiProfiles.get(view.id());
        sender.sendMessage("- AI: " + (profile != null && profile.enabled() ? "enabled" : "disabled")
            + ", goal: " + (profile == null || profile.goal().isBlank() ? defaultFakeAiGoal(view.name()) : profile.goal())
            + ", last: " + this.aiLastActions.getOrDefault(view.id(), "idle"));
        return true;
    }

    private boolean clear(final CommandSender sender) {
        this.stopAllAi();
        this.stopAllLoops();
        this.clickCommands.clear();
        this.aiFreePlayers.clear();
        this.send(sender, this.service().removeAll());
        return true;
    }

    List<RealFakePlayerView> views() {
        final List<FakePlayerSnapshot> snapshots = new ArrayList<>(this.service().list());
        snapshots.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        final List<RealFakePlayerView> views = new ArrayList<>();
        for (final FakePlayerSnapshot snapshot : snapshots) {
            final Location location = snapshot.location();
            final FakeAiProfile profile = this.aiProfiles.get(snapshot.id());
            views.add(new RealFakePlayerView(
                MODULE,
                snapshot.id(),
                snapshot.name(),
                location.getWorld() == null ? "" : location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                snapshot.gameMode().name().toLowerCase(Locale.ROOT),
                this.loopLine(snapshot.name()),
                this.clickCommands.getOrDefault(snapshot.id(), ""),
                profile != null && profile.enabled(),
                this.defaultGoal(snapshot.name(), this.isAiFree(snapshot.id()), profile == null ? "" : profile.goal()),
                this.aiLastActions.getOrDefault(snapshot.id(), "idle"),
                this.isAiFree(snapshot.id()),
                snapshot.uuid().toString()
            ));
        }
        return views;
    }

    boolean setClickCommand(final String name, final String command) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return false;
        }
        final String sanitized = sanitizeCommand(command);
        if (sanitized.isBlank()) {
            this.clickCommands.remove(snapshot.get().id());
        } else {
            this.clickCommands.put(snapshot.get().id(), sanitized);
        }
        return true;
    }

    boolean setAi(final String name, final boolean enabled, final String goal) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return false;
        }
        final FakePlayerSnapshot fake = snapshot.get();
        final String cleanGoal = sanitizeGoal(goal);
        this.aiLastResponseFingerprints.remove(fake.id());
        if (!enabled) {
            this.stopAi(fake.id());
            this.aiFreePlayers.remove(fake.id());
            if (!cleanGoal.isBlank()) {
                this.aiProfiles.put(fake.id(), new FakeAiProfile(fake.id(), fake.name(), false, cleanGoal, null, null, 0L, 0));
            }
            return true;
        }
        this.aiProfiles.put(fake.id(), new FakeAiProfile(
            fake.id(),
            fake.name(),
            true,
            this.defaultGoal(fake.name(), false, cleanGoal),
            null,
            null,
            0L,
            -1
        ));
        this.startAiLoop(fake.id());
        this.requestAi(fake.id(), true);
        return true;
    }

    private void enableAiFree(final String name, final CommandSender sender) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return;
        }
        final FakePlayerSnapshot fake = snapshot.get();
        this.aiFreePlayers.add(fake.id());
        this.aiLastResponseFingerprints.remove(fake.id());
        this.pendingRiskApprovals.remove(fake.id());
        this.aiProfiles.put(fake.id(), new FakeAiProfile(
            fake.id(),
            fake.name(),
            true,
            this.defaultGoal(fake.name(), true, ""),
            sender instanceof Player player ? player.getUniqueId() : null,
            sender.getName(),
            0L,
            -1
        ));
        this.startAiLoop(fake.id());
        this.requestAi(fake.id(), true);
        sender.sendMessage(ChatColor.RED + "AI-Free enabled for " + fake.name() + ". This mode is OP-only and dangerous: the AI may move, act, build, chat, and run server commands by itself.");
    }

    void observeChat(final Player player, final String rawMessage) {
        if (this.isRealFakePlayer(player)) {
            return;
        }
        final String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            return;
        }
        this.observeChatLine(player.getName(), Bukkit.isPrimaryThread() ? player.getWorld().getName() : "unknown", message);
    }

    void observeWebChat(final String sender, final String rawMessage) {
        final String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            return;
        }
        final String author = (sender == null || sender.isBlank()) ? "web" : "web:" + sender.trim();
        this.observeChatLine(author, "web", message);
        if (this.preferences.booleanValue("modules.ai.fake-players.chat-control.ambient-enabled", true)) {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.handleWebAmbientChatControlMain(author, message));
        }
    }

    private void observeChatLine(final String sender, final String world, final String message) {
        synchronized (this.recentChat) {
            this.recentChat.addLast(new ObservedChat(
                truncatePlain(sender, 48),
                truncatePlain(world, 48),
                truncatePlain(message, 240)
            ));
            while (this.recentChat.size() > 48) {
                this.recentChat.removeFirst();
            }
        }
    }

    boolean handleChatControl(final Player player, final String rawMessage) {
        if (this.isRealFakePlayer(player)) {
            return false;
        }
        final String message = rawMessage == null ? "" : rawMessage.trim();
        final String prefix = this.preferences.stringValue("modules.ai.fake-players.chat-control.trigger-prefix", "@bot").trim();
        if (!this.preferences.booleanValue("modules.ai.fake-players.chat-control.enabled", true)) {
            return false;
        }
        if (!prefix.isBlank() && message.startsWith(prefix)) {
            final String body = message.substring(prefix.length()).trim();
            final UUID playerId = player.getUniqueId();
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.handleChatControlMain(playerId, prefix, body));
            return true;
        }
        final MentionedFakePlayer mentioned = this.mentionedFakePlayer(message);
        if (mentioned == null) {
            if (!this.preferences.booleanValue("modules.ai.fake-players.chat-control.ambient-enabled", true)) {
                return false;
            }
            if (this.service().list().isEmpty()) {
                return false;
            }
            final String aiPrefix = this.preferences.stringValue("modules.ai.chat.trigger-prefix", "@ai").trim();
            if (!aiPrefix.isBlank() && message.startsWith(aiPrefix)) {
                return false;
            }
            final UUID playerId = player.getUniqueId();
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.handleAmbientChatControlMain(playerId, message));
            return true;
        }
        final String body = mentioned.snapshot().name() + " " + mentioned.task();
        final UUID playerId = player.getUniqueId();
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.handleChatControlMain(playerId, mentioned.snapshot().name(), body));
        return true;
    }

    private boolean isRealFakePlayer(final Player player) {
        for (final FakePlayerSnapshot snapshot : this.service().list()) {
            if (snapshot.uuid().equals(player.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    boolean handleInteract(final Player player, final Entity clicked) {
        if (!this.preferences.moduleEnabled(MODULE)) {
            return false;
        }
        if (!(clicked instanceof Player)) {
            return false;
        }
        for (final FakePlayerSnapshot snapshot : this.service().list()) {
            if (!snapshot.uuid().equals(clicked.getUniqueId())) {
                continue;
            }
            final String command = this.clickCommands.getOrDefault(snapshot.id(), "");
            if (command.isBlank()) {
                return false;
            }
            final String rendered = renderClickCommand(command, player, snapshot);
            if (rendered.isBlank()) {
                return false;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered);
            return true;
        }
        return false;
    }

    private @Nullable MentionedFakePlayer mentionedFakePlayer(final String message) {
        if (message.isBlank()) {
            return null;
        }
        for (final FakePlayerSnapshot snapshot : this.service().list()) {
            final List<String> names = new ArrayList<>();
            names.add(snapshot.name());
            names.addAll(this.fakeBotAliases(snapshot.name()));
            for (final String name : names) {
                if (!containsName(message, name)) {
                    continue;
                }
                final String task = removeFirstName(message, name).trim();
                return new MentionedFakePlayer(snapshot, task);
            }
        }
        return null;
    }

    private List<String> fakeBotAliases(final String targetName) {
        final String targetId = HunterToolsPreferences.normalize(targetName);
        final List<String> aliases = new ArrayList<>();
        for (final HunterToolsPreferences.FakeBotAlias alias : this.preferences.fakeBotAliases()) {
            if (!alias.enabled() || !HunterToolsPreferences.normalize(alias.target()).equals(targetId)) {
                continue;
            }
            aliases.addAll(alias.aliases());
        }
        return aliases;
    }

    private void handleChatControlMain(final UUID playerId, final String prefix, final String body) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (!this.preferences.moduleEnabled(MODULE) || !this.preferences.moduleEnabled("ai")) {
            player.sendMessage(ChatColor.RED + "HunterCore real fake player AI is disabled.");
            return;
        }
        if (!this.preferences.booleanValue("modules.ai.fake-players.enabled", true)) {
            player.sendMessage(ChatColor.RED + "HunterCore fake player AI planning is disabled.");
            return;
        }
        if (this.preferences.booleanValue("modules.ai.fake-players.chat-control.require-permission", false)) {
            final String permission = this.preferences.stringValue("modules.ai.fake-players.chat-control.permission", "huntertools.ai.fakeplayer").trim();
            if (!permission.isBlank() && !player.hasPermission(permission)) {
                player.sendMessage(ChatColor.RED + "You do not have permission to control fake player AI from chat.");
                return;
            }
        }
        final long waitMillis = this.chatControlCooldown(player.getUniqueId().toString());
        if (waitMillis > 0L) {
            player.sendMessage(ChatColor.YELLOW + "Fake player chat control cooldown: " + Math.max(1L, (waitMillis + 999L) / 1000L) + "s.");
            return;
        }
        if (body.isBlank() || body.equalsIgnoreCase("help")) {
            player.sendMessage(ChatColor.GOLD + "Usage: " + prefix + " <fakePlayer|all> <task>");
            player.sendMessage(ChatColor.GRAY + "Examples: " + prefix + " Bot follow me, " + prefix + " Bot mine the stone in front, " + prefix + " stop Bot");
            return;
        }
        if (body.equalsIgnoreCase("list")) {
            final List<String> names = this.service().list().stream().map(FakePlayerSnapshot::name).sorted(String::compareToIgnoreCase).toList();
            player.sendMessage(ChatColor.GOLD + "Real fake players: " + ChatColor.WHITE + (names.isEmpty() ? "none" : String.join(", ", names)));
            return;
        }

        final String normalized = HunterToolsPreferences.normalize(firstValue(body));
        if (normalized.equals("stop")) {
            this.handleChatStop(player, body.substring(Math.min(body.length(), firstValue(body).length())).trim());
            return;
        }
        if (normalized.equals("all")) {
            final String task = body.substring(Math.min(body.length(), firstValue(body).length())).trim();
            if (task.isBlank()) {
                player.sendMessage(ChatColor.RED + "Usage: " + prefix + " all <task>");
                return;
            }
            final List<FakePlayerSnapshot> snapshots = new ArrayList<>(this.service().list());
            if (snapshots.isEmpty()) {
                player.sendMessage(ChatColor.RED + "No real fake players are online.");
                return;
            }
            for (final FakePlayerSnapshot snapshot : snapshots) {
                this.assignChatTask(player, snapshot, task);
            }
            player.sendMessage(ChatColor.GREEN + "Assigned task to " + snapshots.size() + " real fake player(s).");
            return;
        }

        final TargetTask targetTask = this.targetTask(player, body);
        if (targetTask == null) {
            player.sendMessage(ChatColor.RED + "Could not choose a fake player. Use " + prefix + " list, then " + prefix + " <name> <task>.");
            return;
        }
        if (targetTask.task().isBlank()) {
            player.sendMessage(ChatColor.RED + "Tell " + targetTask.snapshot().name() + " what to do after its name.");
            return;
        }
        this.assignChatTask(player, targetTask.snapshot(), targetTask.task());
    }

    private void handleAmbientChatControlMain(final UUID playerId, final String body) {
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null || body == null || body.isBlank()) {
            return;
        }
        if (!this.preferences.moduleEnabled(MODULE)
            || !this.preferences.moduleEnabled("ai")
            || !this.preferences.booleanValue("modules.ai.fake-players.enabled", true)
            || this.service().list().isEmpty()) {
            return;
        }
        if (this.preferences.booleanValue("modules.ai.fake-players.chat-control.require-permission", false)) {
            final String permission = this.preferences.stringValue("modules.ai.fake-players.chat-control.permission", "huntertools.ai.fakeplayer").trim();
            if (!permission.isBlank() && !player.hasPermission(permission)) {
                return;
            }
        }
        final long waitMillis = this.chatControlCooldown(player.getUniqueId().toString());
        if (waitMillis > 0L) {
            return;
        }
        final TargetTask targetTask = this.targetTask(player, body);
        if (targetTask == null || targetTask.task().isBlank()) {
            return;
        }
        this.assignChatTask(player, targetTask.snapshot(), targetTask.task());
    }

    private void handleWebAmbientChatControlMain(final String sender, final String body) {
        if (body == null || body.isBlank()
            || !this.preferences.moduleEnabled(MODULE)
            || !this.preferences.moduleEnabled("ai")
            || !this.preferences.booleanValue("modules.ai.fake-players.enabled", true)
            || this.service().list().isEmpty()) {
            return;
        }
        final String normalized = HunterToolsPreferences.normalize(firstValue(body));
        if (normalized.equals("stop")) {
            final String target = body.substring(Math.min(body.length(), firstValue(body).length())).trim();
            if (target.isBlank() || HunterToolsPreferences.normalize(target).equals("all")) {
                for (final FakePlayerSnapshot snapshot : this.service().list()) {
                    this.stopAi(snapshot.id());
                    this.stopLoops(snapshot.name());
                    this.service().stopActions(snapshot.name());
                }
                return;
            }
        }
        if (normalized.equals("all")) {
            final String task = body.substring(Math.min(body.length(), firstValue(body).length())).trim();
            if (!task.isBlank()) {
                for (final FakePlayerSnapshot snapshot : this.service().list()) {
                    this.assignExternalChatTask(sender, snapshot, task);
                }
            }
            return;
        }
        final TargetTask targetTask = this.targetTask(null, body);
        if (targetTask != null && !targetTask.task().isBlank()) {
            this.assignExternalChatTask(sender, targetTask.snapshot(), targetTask.task());
        }
    }

    private void handleChatStop(final Player player, final String target) {
        if (target.isBlank() || HunterToolsPreferences.normalize(target).equals("all")) {
            for (final FakePlayerSnapshot snapshot : this.service().list()) {
                this.stopAi(snapshot.id());
                this.stopLoops(snapshot.name());
                this.service().stopActions(snapshot.name());
            }
            player.sendMessage(ChatColor.GREEN + "Stopped all real fake player AI tasks.");
            return;
        }
        final var snapshot = this.service().snapshot(firstValue(target));
        if (snapshot.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Fake player not found: " + firstValue(target));
            return;
        }
        this.stopAi(snapshot.get().id());
        this.stopLoops(snapshot.get().name());
        this.service().stopActions(snapshot.get().name());
        player.sendMessage(ChatColor.GREEN + "Stopped " + snapshot.get().name() + ".");
    }

    private TargetTask targetTask(final Player player, final String body) {
        final String first = firstValue(body);
        final String rest = body.substring(Math.min(body.length(), first.length())).trim();
        if (!first.isBlank()) {
            final var named = this.service().snapshot(first);
            if (named.isPresent()) {
                return new TargetTask(named.get(), rest);
            }
        }
        final List<FakePlayerSnapshot> snapshots = new ArrayList<>(this.service().list());
        if (snapshots.isEmpty()) {
            return null;
        }
        if (snapshots.size() == 1) {
            return new TargetTask(snapshots.getFirst(), body);
        }
        if (player == null) {
            snapshots.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
            return new TargetTask(snapshots.getFirst(), body);
        }
        FakePlayerSnapshot nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (final FakePlayerSnapshot snapshot : snapshots) {
            final Location location = snapshot.location();
            if (location.getWorld() == null || !location.getWorld().equals(player.getWorld())) {
                continue;
            }
            final double distance = location.distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearest = snapshot;
                nearestDistance = distance;
            }
        }
        return nearest == null ? null : new TargetTask(nearest, body);
    }

    private void assignChatTask(final Player player, final FakePlayerSnapshot fake, final String task) {
        if (this.chatTaskRecentlyHandled(player, fake, task)) {
            this.aiLastActions.put(fake.id(), "ignored duplicate chat task: " + truncatePlain(task, 80));
            return;
        }
        final Location location = player.getLocation();
        final List<String> quickActions = new ArrayList<>();
        final boolean shortcutApplied = this.quickResponseEnabled() && this.applyQuickChatTask(player, fake, task, quickActions);
        if (shortcutApplied && quickActions.isEmpty()) {
            quickActions.add("one or more matching local quick actions");
        }
        final String quickLine = quickActions.isEmpty()
            ? "Local quick executor: no local action was applied before model planning.\n"
            : "Local quick executor already applied: " + String.join("; ", quickActions)
                + ". These action categories are server-locked for this turn; do not repeat them or use build/worldedit/place as a second construction step.\n";
        final String goal = "Player " + player.getName() + " assigned this real fake player a chat task: " + sanitizeGoal(task) + "\n"
            + "Controller location: world=" + player.getWorld().getName()
            + " x=" + format(location.getX())
            + " y=" + format(location.getY())
            + " z=" + format(location.getZ())
            + " yaw=" + format(location.getYaw())
            + " pitch=" + format(location.getPitch()) + "\n"
            + quickLine
            + "Recent chat:\n" + this.recentChatContext() + "\n"
            + "If the player is only having casual conversation, reply with one short [say:...] and do not perform physical work. If the message asks for help, movement, mining, building, combat, inventory, or other work, perform useful actions.\n"
            + "Behave like a cooperative Minecraft helper. For follow/come tasks use [follow:player=" + player.getName() + ",ticks=200]. "
            + "For travel tasks use [goto:x y z,ticks=200]. For mining or tool work, look at the target and use [mine:ticks=40] or [use]. "
            + "For building use exactly one preset action: [build-house], [build-cabin], [build-cottage], [build-barn], [build-greenhouse], [build-bunker], [build-farm], [build-stairs], [build-tower], [build-bridge], [build-rope-bridge], [build-wall], [build-platform], [build-dock], [build-well], [build-camp], [build-mine], [build-market], [build-gate], [build-road], or [build-windmill]. Use [clear-build] to dismantle the latest build. Do not combine build/worldedit/place in the same reply. "
            + "For armor use all needed wear actions: [wear:material=iron_helmet], [wear:material=iron_chestplate], [wear:material=iron_leggings], [wear:material=iron_boots]. "
            + "For combat use [attack-player:player=name,ticks=120] or [attack-nearest:ticks=120]. "
            + "Reply only with one short [say:...] after useful actions. Never explain reasoning. Stop if the task says stop.";
        final int turns = 1;
        this.aiLastResponseFingerprints.remove(fake.id());
        this.aiProfiles.put(fake.id(), new FakeAiProfile(
            fake.id(),
            fake.name(),
            true,
            goal,
            player.getUniqueId(),
            player.getName(),
            0L,
            turns
        ));
        this.startAiLoop(fake.id());
        this.requestAi(fake.id(), true);
        this.aiLastActions.put(fake.id(), "assigned by " + player.getName() + ": " + truncatePlain(task, 80));
    }

    private void assignExternalChatTask(final String sender, final FakePlayerSnapshot fake, final String task) {
        final String controller = (sender == null || sender.isBlank()) ? "web" : sender;
        final String key = "external:" + controller + ":" + fake.id() + ":" + HunterToolsPreferences.normalize(task).replaceAll("\\s+", " ").trim();
        final long now = System.currentTimeMillis();
        this.recentChatTaskFingerprints.entrySet().removeIf(entry -> now - entry.getValue() > 30_000L);
        final Long previous = this.recentChatTaskFingerprints.put(key, now);
        if (previous != null && now - previous < 15_000L) {
            this.aiLastActions.put(fake.id(), "ignored duplicate web chat task: " + truncatePlain(task, 80));
            return;
        }
        final Location location = fake.location();
        final String goal = "Web chat from " + controller + " is visible to this real fake player: " + sanitizeGoal(task) + "\n"
            + "Fake player location: world=" + (location.getWorld() == null ? "unknown" : location.getWorld().getName())
            + " x=" + format(location.getX())
            + " y=" + format(location.getY())
            + " z=" + format(location.getZ())
            + " yaw=" + format(location.getYaw())
            + " pitch=" + format(location.getPitch()) + "\n"
            + "Recent chat:\n" + this.recentChatContext() + "\n"
            + "If this is casual conversation, reply with one short [say:...] and do not perform physical work. If it asks for help, movement, mining, building, combat, inventory, or other work, perform useful actions. "
            + "For travel use [goto:x y z,ticks=200] or [follow:player=name,ticks=200] when a player is named. For building, use exactly one build/worldedit/place action. Never explain reasoning.";
        this.aiLastResponseFingerprints.remove(fake.id());
        this.aiProfiles.put(fake.id(), new FakeAiProfile(
            fake.id(),
            fake.name(),
            true,
            goal,
            null,
            controller,
            0L,
            1
        ));
        this.startAiLoop(fake.id());
        this.requestAi(fake.id(), true);
        this.aiLastActions.put(fake.id(), "web chat by " + controller + ": " + truncatePlain(task, 80));
    }

    private boolean chatTaskRecentlyHandled(final Player player, final FakePlayerSnapshot fake, final String task) {
        final long now = System.currentTimeMillis();
        this.recentChatTaskFingerprints.entrySet().removeIf(entry -> now - entry.getValue() > 30_000L);
        final String fingerprint = player.getUniqueId()
            + ":" + fake.id()
            + ":" + HunterToolsPreferences.normalize(task == null ? "" : task).replaceAll("\\s+", " ").trim();
        final Long previous = this.recentChatTaskFingerprints.put(fingerprint, now);
        return previous != null && now - previous < 15_000L;
    }

    private boolean applyQuickGoalTask(final FakePlayerSnapshot fake, final String task) {
        final String lower = task == null ? "" : task.toLowerCase(Locale.ROOT);
        final String normalized = HunterToolsPreferences.normalize(task == null ? "" : task);
        boolean applied = false;
        if (isObservationOnlyRequest(lower, normalized)) {
            return false;
        }
        if (hasAny(lower, normalized, "stop", "halt", "cancel", "停止", "停下", "别动", "不要动")) {
            this.stopLoops(fake.name());
            this.service().stopActions(fake.name());
            return true;
        }
        if (isRespawnRequest(lower, normalized)) {
            this.service().respawn(fake.name());
            this.recordQuickAction(fake.id(), "respawn", "respawn", 30_000L);
            applied = true;
        }
        if (isBuildClearRequest(lower, normalized)) {
            this.aiClearBuild(fake.name());
            this.recordQuickAction(fake.id(), "clear-build", "clear build", 45_000L);
            return true;
        }
        final Player targetPlayer = this.quickTargetPlayer(task, null, fake.uuid());
        if (targetPlayer != null && isComeRequest(lower, normalized)) {
            this.startFollowLoop(fake.name(), targetPlayer.getUniqueId(), 260, 2.4D, true);
            this.recordQuickAction(fake.id(), "movement", "follow " + targetPlayer.getName(), 30_000L);
            applied = true;
        }
        if (targetPlayer != null && hasAny(lower, normalized, "come", "follow", "go to", "walk to", "move to", "approach", "find", "跟着", "跟随", "过来", "来我", "到我", "靠近", "走向", "走到", "去找", "过去", "接近")) {
            this.startFollowLoop(fake.name(), targetPlayer.getUniqueId(), 260, 2.4D, true);
            applied = true;
        }
        if (hasAny(lower, normalized, "sneak", "crouch", "shift", "潜行", "蹲下", "蹲着")) {
            this.service().setSneaking(fake.name(), true);
            applied = true;
        }
        if (hasAny(lower, normalized, "jump", "跳")) {
            final int times = hasAny(lower, normalized, "twice", "两", "二", "2") ? 2 : 1;
            for (int i = 0; i < times; i++) {
                this.service().jump(fake.name());
            }
            applied = true;
        }
        if (hasAny(lower, normalized, "armor", "armour", "wear", "穿甲", "穿戴", "护甲", "盔甲", "铠甲", "穿上") || isArmorRequest(lower, normalized)) {
            final String tier = armorTier(lower, normalized);
            this.aiWear(fake.name(), "material=" + tier + "_helmet");
            this.aiWear(fake.name(), "material=" + tier + "_chestplate");
            this.aiWear(fake.name(), "material=" + tier + "_leggings");
            this.aiWear(fake.name(), "material=" + tier + "_boots");
            applied = true;
        }
        final Material requestedItem = quickItem(lower, normalized);
        if (requestedItem != null) {
            this.aiEquip(fake.name(), "slot=1,material=" + requestedItem.key().value() + ",amount=" + Math.max(1, Math.min(64, requestedItem.getMaxStackSize())));
            applied = true;
        }
        if (isBuildRequest(task)) {
            final String kind = buildKindClean(task);
            this.startBuildPreset(fake.name(), fake.location(), kind);
            this.recordQuickAction(fake.id(), "construction", "build " + kind, 120_000L);
            applied = true;
        }
        if (isCombatRequest(lower, normalized)) {
            final Entity target = nearestCombatTarget(fake, "target=hostile");
            if (target != null) {
                this.startCombatLoop(fake.name(), target.getUniqueId(), 180, true);
            } else {
                this.startTimedActionLoop(fake.name(), "attack", 60);
            }
            applied = true;
        }
        return applied;
    }

    private boolean applyQuickChatTask(final Player controller, final FakePlayerSnapshot fake, final String task, final List<String> quickActions) {
        final String lower = task == null ? "" : task.toLowerCase(Locale.ROOT);
        final String normalized = HunterToolsPreferences.normalize(task == null ? "" : task);
        boolean applied = false;
        if (isObservationOnlyRequest(lower, normalized)) {
            return false;
        }
        if (hasAny(lower, normalized, "stop", "halt", "cancel", "停止", "停下", "别动", "不要动")) {
            this.stopLoops(fake.name());
            this.service().stopActions(fake.name());
            return true;
        }
        if (isRespawnRequest(lower, normalized)) {
            this.service().respawn(fake.name());
            applied = true;
        }
        if (isBuildClearRequest(lower, normalized)) {
            this.aiClearBuild(fake.name());
            quickActions.add("cleared latest recorded build");
            return true;
        }
        final Player targetPlayer = this.quickTargetPlayer(task, controller, fake.uuid());
        if (targetPlayer != null && isComeRequest(lower, normalized)) {
            this.startFollowLoop(fake.name(), targetPlayer.getUniqueId(), 260, 2.4D, true);
            applied = true;
        }
        if (targetPlayer != null && hasAny(lower, normalized, "come", "follow", "go to", "walk to", "move to", "approach", "find", "跟着", "跟随", "过来", "来我", "到我", "靠近", "走向", "走到", "去找", "过去", "接近")) {
            this.startFollowLoop(fake.name(), targetPlayer.getUniqueId(), 260, 2.4D, true);
            applied = true;
        }
        if (hasAny(lower, normalized, "sneak", "crouch", "shift", "潜行", "蹲下", "蹲着")) {
            this.service().setSneaking(fake.name(), true);
            applied = true;
        }
        if (hasAny(lower, normalized, "stand", "unsneak", "站起来", "别蹲", "取消蹲")) {
            this.service().setSneaking(fake.name(), false);
            applied = true;
        }
        if (hasAny(lower, normalized, "jump", "跳")) {
            final int times = hasAny(lower, normalized, "twice", "两", "二", "2") ? 2 : 1;
            for (int i = 0; i < times; i++) {
                this.service().jump(fake.name());
            }
            applied = true;
        }
        if (hasAny(lower, normalized, "walk", "forward", "前进", "往前", "走路", "向前")) {
            this.startMoveLoop(fake.name(), new MovePlan(1.0D, 0.0D, 60, false, true, false));
            applied = true;
        }
        if (hasAny(lower, normalized, "armor", "armour", "wear", "穿甲", "穿戴", "护甲", "盔甲", "铠甲", "穿上") || isArmorRequest(lower, normalized)) {
            final String tier = armorTier(lower, normalized);
            this.aiWear(fake.name(), "material=" + tier + "_helmet");
            this.aiWear(fake.name(), "material=" + tier + "_chestplate");
            this.aiWear(fake.name(), "material=" + tier + "_leggings");
            this.aiWear(fake.name(), "material=" + tier + "_boots");
            applied = true;
        }
        final Material requestedItem = quickItem(lower, normalized);
        if (requestedItem != null) {
            this.aiEquip(fake.name(), "slot=1,material=" + requestedItem.key().value() + ",amount=" + Math.max(1, Math.min(64, requestedItem.getMaxStackSize())));
            applied = true;
        }
        if (isBuildRequest(task)) {
            final String kind = buildKindClean(task);
            this.startBuildPreset(fake.name(), controller.getLocation(), kind);
            this.recordQuickAction(fake.id(), "construction", "build " + kind, 120_000L);
            quickActions.add("started local " + kind + " preset build");
            applied = true;
        }
        if (isCombatRequest(lower, normalized) || hasAny(lower, normalized, "attack", "fight", "hit", "kill", "攻击", "打", "揍")) {
            if (targetPlayer != null && hasAny(lower, normalized, targetPlayer.getName().toLowerCase(Locale.ROOT), "me", "我", "玩家")) {
                this.startCombatLoop(fake.name(), targetPlayer.getUniqueId(), 120, true);
            } else {
                final Entity target = nearestCombatTarget(fake, "target=hostile");
                if (target != null) {
                    this.startCombatLoop(fake.name(), target.getUniqueId(), 120, true);
                } else {
                    this.startTimedActionLoop(fake.name(), "attack", 60);
                }
            }
            applied = true;
        }
        return applied;
    }

    private @Nullable Player quickTargetPlayer(final String task, final @Nullable Player fallback, final UUID actorUuid) {
        final String text = task == null ? "" : task.toLowerCase(Locale.ROOT);
        final String normalized = HunterToolsPreferences.normalize(task == null ? "" : task);
        if (fallback != null && !fallback.getUniqueId().equals(actorUuid) && (hasAny(text, normalized, "me", "my", "我", "我这里", "我身边")
            || text.contains(fallback.getName().toLowerCase(Locale.ROOT)))) {
            return fallback;
        }
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(actorUuid)) {
                continue;
            }
            final String name = online.getName();
            if (text.contains(name.toLowerCase(Locale.ROOT)) || normalized.contains(HunterToolsPreferences.normalize(name))) {
                return online;
            }
        }
        return fallback;
    }

    private @Nullable Entity nearestCombatTarget(final FakePlayerSnapshot fake, final String args) {
        final Location location = fake.location();
        final World world = location.getWorld();
        if (world == null) {
            return null;
        }
        final double radius = clamp(doubleArg(args, "radius", 8.0D), 2.0D, 24.0D);
        final String wanted = HunterToolsPreferences.normalize(firstNonBlank(
            valueFor(args, "target"),
            valueFor(args, "name"),
            valueFor(args, "type"),
            firstValue(args)
        ));
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (final Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (!(entity instanceof final LivingEntity living) || living.isDead()) {
                continue;
            }
            if (entity.getUniqueId().equals(fake.uuid()) || this.isFakePlayerUuid(entity.getUniqueId())) {
                continue;
            }
            if (!wanted.isBlank() && !combatTargetMatches(entity, wanted)) {
                continue;
            }
            double score = entity.getLocation().distanceSquared(location);
            if (entity instanceof org.bukkit.entity.Monster) {
                score -= 16.0D;
            }
            if (score < bestScore) {
                best = entity;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean combatTargetMatches(final Entity entity, final String wanted) {
        if (wanted.isBlank() || wanted.equals("nearest") || wanted.equals("any") || wanted.equals("target")) {
            return true;
        }
        if (wanted.equals("player")) {
            return entity instanceof Player;
        }
        if (wanted.equals("mob") || wanted.equals("monster") || wanted.equals("hostile")) {
            return !(entity instanceof Player);
        }
        return HunterToolsPreferences.normalize(entity.getName()).contains(wanted)
            || HunterToolsPreferences.normalize(entity.getType().key().value()).contains(wanted);
    }

    private boolean isFakePlayerUuid(final UUID uuid) {
        for (final FakePlayerSnapshot snapshot : this.service().list()) {
            if (snapshot.uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private String recentChatContext() {
        final int limit = Math.max(0, Math.min(24, this.preferences.intValue("modules.ai.chat.context-lines", 12)));
        if (limit <= 0) {
            return "none";
        }
        final List<ObservedChat> copy;
        synchronized (this.recentChat) {
            copy = new ArrayList<>(this.recentChat);
        }
        final int start = Math.max(0, copy.size() - limit);
        final List<String> lines = new ArrayList<>();
        for (int i = start; i < copy.size(); i++) {
            final ObservedChat chat = copy.get(i);
            lines.add(chat.player() + "@" + chat.world() + ": " + chat.message());
        }
        return lines.isEmpty() ? "none" : String.join("\n", lines);
    }

    private void startAiLoop(final String id) {
        this.stopAiTask(id);
        final FakeAiProfile profile = this.aiProfiles.get(playerId(id));
        if (profile == null || !profile.enabled()) {
            return;
        }
        if (profile.remainingTurns() == 0) {
            this.stopAiTask(profile.id());
            this.aiProfiles.put(profile.id(), new FakeAiProfile(
                profile.id(), profile.name(), false, profile.goal(),
                profile.controllerUuid(), profile.controllerName(), profile.highRiskAllowedUntilMillis(), 0
            ));
            return;
        }
        final long interval = Math.max(1L, this.runtimeFakePlayerIntervalSeconds()) * 20L;
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> this.requestAi(profile.id(), false), interval, interval);
        this.aiTasks.put(profile.id(), task);
    }

    private void requestAi(final String id, final boolean manual) {
        final String key = playerId(id);
        final FakeAiProfile profile = this.aiProfiles.get(key);
        if (profile == null || this.aiBusy.contains(key)) {
            return;
        }
        if (!manual && profile.remainingTurns() == 0) {
            this.stopAiTask(profile.id());
            return;
        }
        if (!manual && (!profile.enabled()
            || !this.preferences.moduleEnabled("ai")
            || !this.preferences.booleanValue("modules.ai.fake-players.enabled", true))) {
            return;
        }
        if (!manual && this.preferences.booleanValue("modules.ai.adaptive-throttling.enabled", true)) {
            final double throttleFactor = this.plugin.metricsSnapshot().adaptiveBudget().aiThrottleFactor();
            if (throttleFactor >= 2.5D && (System.currentTimeMillis() / 1000L) % Math.max(2L, Math.round(throttleFactor)) != 0L) {
                this.aiLastActions.put(key, "throttled by MSPT " + String.format(Locale.ROOT, "%.1fx", throttleFactor));
                return;
            }
        }
        if (this.aiManager == null) {
            this.aiLastActions.put(key, "AI manager unavailable");
            return;
        }
        final var snapshot = this.service().snapshot(profile.name());
        if (snapshot.isEmpty()) {
            this.stopAi(key);
            return;
        }
        final FakePlayerSnapshot fake = snapshot.get();
        final String system = this.isAiFree(fake.id()) ? this.aiFreeSystemPrompt(fake.name()) : this.fakeAiSystemPrompt(fake.name());
        final String prompt = this.fakeAiContext(profile, fake);
        this.aiBusy.add(key);
        this.aiLastActions.put(key, "thinking");
        this.aiManager.completeFakePlayerPlan(system, prompt).whenComplete((response, error) -> {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                this.aiBusy.remove(key);
                if (error != null) {
                    this.aiLastActions.put(key, "AI failed: " + cleanError(error));
                    this.plugin.getLogger().warning("HunterCore fake player AI failed for " + fake.name() + ": " + cleanError(error));
                    return;
                }
                final String responseFingerprint = responseFingerprint(response);
                if (!responseFingerprint.isBlank() && responseFingerprint.equals(this.aiLastResponseFingerprints.get(key))) {
                    this.aiLastActions.put(key, "ignored repeated AI plan");
                    this.stopAiTask(key);
                    return;
                }
                this.aiLastResponseFingerprints.put(key, responseFingerprint);
                this.applyAiPlan(fake.name(), response);
                final FakeAiProfile current = this.aiProfiles.get(key);
                if (current != null && current.remainingTurns() > 0) {
                    final int remaining = current.remainingTurns() - 1;
                    this.aiProfiles.put(key, new FakeAiProfile(
                        current.id(), current.name(), remaining > 0, current.goal(),
                        current.controllerUuid(), current.controllerName(), current.highRiskAllowedUntilMillis(), remaining
                    ));
                    if (remaining <= 0) {
                        this.stopAiTask(key);
                    }
                }
            });
        });
    }

    private int runtimeFakePlayerIntervalSeconds() {
        final int base = Math.max(1, this.preferences.intValue("modules.ai.fake-players.interval-seconds", 6));
        if (!this.preferences.booleanValue("modules.ai.adaptive-throttling.enabled", true)) {
            return base;
        }
        return Math.max(base, this.plugin.metricsSnapshot().adaptiveBudget().fakePlayerIntervalSeconds());
    }

    private String fakeAiSystemPrompt(final String fakeName) {
        final String configured = this.preferences.stringValue("modules.ai.fake-players.system-prompt", "").trim();
        final String base;
        if (configured.isBlank()) {
            base = defaultFakeAiPrompt();
        } else {
            final String normalized = HunterToolsPreferences.normalize(configured);
            if (normalized.startsWith("you control a huntercore real fake player")
                && (!normalized.contains("respawn")
                    || !normalized.contains("attack-player")
                    || !normalized.contains("clear-build")
                    || !normalized.contains("we-fill")
                    || !normalized.contains("build-windmill")
                    || !normalized.contains("build-greenhouse")
                    || !normalized.contains("build-rope-bridge"))) {
                base = defaultFakeAiPrompt();
            } else {
                base = configured;
            }
        }
        return this.decorateSystemPrompt(base, fakeName, false);
    }

    private String aiFreeSystemPrompt(final String fakeName) {
        return this.decorateSystemPrompt(defaultAiFreePrompt(), fakeName, true);
    }

    private String fakeAiContext(final FakeAiProfile profile, final FakePlayerSnapshot snapshot) {
        final StringBuilder context = new StringBuilder(1024);
        if (this.isAiFree(snapshot.id())) {
            context.append("AI-FREE MODE: You are autonomous, OP-level, dangerous, and allowed to run server commands with [command:...]. ")
                .append("You are an AI with your own thoughts; do whatever you want in this Minecraft world while keeping actions visible when useful.\n");
        }
        final Location location = snapshot.location();
        context.append("Goal: ").append(this.defaultGoal(snapshot.name(), this.isAiFree(snapshot.id()), profile.goal())).append('\n');
        context.append("Fake player: ").append(snapshot.name())
            .append(" mode=").append(snapshot.gameMode().name().toLowerCase(Locale.ROOT))
            .append(" sneaking=").append(snapshot.sneaking())
            .append(" sprinting=").append(snapshot.sprinting())
            .append(" usingItem=").append(snapshot.usingItem())
            .append(" loops=").append(this.loopLine(snapshot.name())).append('\n');
        context.append("Location: world=").append(location.getWorld() == null ? "unknown" : location.getWorld().getName())
            .append(" x=").append(format(location.getX()))
            .append(" y=").append(format(location.getY()))
            .append(" z=").append(format(location.getZ()))
            .append(" yaw=").append(format(location.getYaw()))
            .append(" pitch=").append(format(location.getPitch())).append('\n');
        final Player liveFakePlayer = Bukkit.getPlayer(snapshot.uuid());
        if (liveFakePlayer != null) {
            context.append("Vitals: health=").append(format(liveFakePlayer.getHealth()))
                .append('/').append(format(liveFakePlayer.getMaxHealth()))
                .append(" food=").append(liveFakePlayer.getFoodLevel())
                .append(" saturation=").append(format(liveFakePlayer.getSaturation()))
                .append(" fireTicks=").append(liveFakePlayer.getFireTicks())
                .append(" fallDistance=").append(format(liveFakePlayer.getFallDistance()))
                .append(" dead=").append(liveFakePlayer.isDead() || liveFakePlayer.getHealth() <= 0.0D)
                .append('\n');
            if (liveFakePlayer.isDead() || liveFakePlayer.getHealth() <= 0.0D) {
                context.append("Critical: you are dead. Use [respawn] before movement, combat, building, items, or chat.\n");
            }
            context.append("Armor:");
            final ItemStack helmet = liveFakePlayer.getInventory().getHelmet();
            final ItemStack chestplate = liveFakePlayer.getInventory().getChestplate();
            final ItemStack leggings = liveFakePlayer.getInventory().getLeggings();
            final ItemStack boots = liveFakePlayer.getInventory().getBoots();
            context.append(" head=").append(itemLine(helmet))
                .append(" chest=").append(itemLine(chestplate))
                .append(" legs=").append(itemLine(leggings))
                .append(" feet=").append(itemLine(boots))
                .append('\n');
            context.append("Hotbar:");
            for (int slot = 0; slot < 9; slot++) {
                final ItemStack item = liveFakePlayer.getInventory().getItem(slot);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                context.append(' ')
                    .append(slot + 1)
                    .append('=')
                    .append(item.getType().key().value())
                    .append('x')
                    .append(item.getAmount());
                if (slot == liveFakePlayer.getInventory().getHeldItemSlot()) {
                    context.append("(selected)");
                }
                context.append(';');
            }
            context.append('\n');
            context.append("Inventory:");
            int inventoryCount = 0;
            for (int slot = 9; slot < liveFakePlayer.getInventory().getSize() && inventoryCount < 16; slot++) {
                final ItemStack item = liveFakePlayer.getInventory().getItem(slot);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                context.append(' ')
                    .append(slot)
                    .append('=')
                    .append(item.getType().key().value())
                    .append('x')
                    .append(item.getAmount())
                    .append(';');
                inventoryCount++;
            }
            if (inventoryCount == 0) {
                context.append(" empty");
            }
            context.append('\n');
        }

        final World world = location.getWorld();
        if (world == null) {
            return context.append("World is not loaded.\n").toString();
        }
        context.append("World state: time=").append(world.getTime())
            .append(" fullTime=").append(world.getFullTime())
            .append(" difficulty=").append(world.getDifficulty().name().toLowerCase(Locale.ROOT))
            .append(" storm=").append(world.hasStorm())
            .append(" thundering=").append(world.isThundering())
            .append('\n');
        context.append("Useful gamerules: keepInventory=").append(world.getGameRuleValue(GameRule.KEEP_INVENTORY))
            .append(" doDaylightCycle=").append(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))
            .append(" doWeatherCycle=").append(world.getGameRuleValue(GameRule.DO_WEATHER_CYCLE))
            .append(" mobGriefing=").append(world.getGameRuleValue(GameRule.MOB_GRIEFING))
            .append(" doMobSpawning=").append(world.getGameRuleValue(GameRule.DO_MOB_SPAWNING))
            .append('\n');
        context.append("Temporary gameplay rules: ").append(this.gameplayRuleManager.summary()).append('\n');
        final int radius = Math.max(2, Math.min(12, this.preferences.intValue("modules.ai.fake-players.nearby-radius-blocks", 6)));
        final Location eye = location.clone().add(0.0D, 1.62D, 0.0D);
        final RayTraceResult ray = world.rayTraceBlocks(eye, location.getDirection(), Math.max(2.0D, radius), FluidCollisionMode.NEVER, true);
        if (ray != null && ray.getHitBlock() != null) {
            context.append("Looking at block: ").append(blockLine(ray.getHitBlock())).append('\n');
        } else {
            context.append("Looking at block: none\n");
        }
        context.append("Feet: ").append(blockLine(location.getBlock())).append('\n');
        context.append("Below: ").append(blockLine(location.clone().add(0.0D, -1.0D, 0.0D).getBlock())).append('\n');
        final Vector direction = location.getDirection().setY(0.0D);
        if (direction.lengthSquared() > 0.0D) {
            direction.normalize();
            context.append("Front: ").append(blockLine(location.clone().add(direction).getBlock())).append('\n');
        }
        context.append("WorldEdit anchor guide: use relative dx/dy/dz from your feet. Suggested build space starts at front-left/right around dx=0..8, dy=0..6, dz=2..10; keep one operation within ")
            .append(Math.max(1, this.preferences.intValue("modules.ai.fake-players.worldedit.max-volume-blocks", 8192)))
            .append(" blocks.\n");

        context.append("Nearby blocks:");
        int blockCount = 0;
        final int baseX = location.getBlockX();
        final int baseY = location.getBlockY();
        final int baseZ = location.getBlockZ();
        for (int y = -1; y <= 2 && blockCount < 24; y++) {
            for (int x = -2; x <= 2 && blockCount < 24; x++) {
                for (int z = -2; z <= 2 && blockCount < 24; z++) {
                    final Block block = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    final Material type = block.getType();
                    if (!type.isAir()) {
                        context.append(' ').append(type.key().value()).append('@').append(x).append(',').append(y).append(',').append(z).append(';');
                        blockCount++;
                    }
                }
            }
        }
        if (blockCount == 0) {
            context.append(" none");
        }
        context.append('\n');

        context.append("Nearby entities:");
        int entityCount = 0;
        for (final Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (entity.getUniqueId().equals(snapshot.uuid())) {
                continue;
            }
            final Location other = entity.getLocation();
            context.append(' ')
                .append(entity.getType().key().value())
                .append('(').append(entity.getName()).append(')')
                .append('@')
                .append(format(other.getX() - location.getX())).append(',')
                .append(format(other.getY() - location.getY())).append(',')
                .append(format(other.getZ() - location.getZ())).append(';');
            entityCount++;
            if (entityCount >= 12) {
                break;
            }
        }
        if (entityCount == 0) {
            context.append(" none");
        }
        context.append('\n');
        context.append("Nearby players:");
        int playerCount = 0;
        for (final Player player : world.getPlayers()) {
            if (player.getUniqueId().equals(snapshot.uuid()) || player.getLocation().distanceSquared(location) > radius * radius) {
                continue;
            }
            final Location other = player.getLocation();
            context.append(' ')
                .append(player.getName())
                .append('@')
                .append(format(other.getX() - location.getX())).append(',')
                .append(format(other.getY() - location.getY())).append(',')
                .append(format(other.getZ() - location.getZ())).append(';');
            playerCount++;
            if (playerCount >= 8) {
                break;
            }
        }
        if (playerCount == 0) {
            context.append(" none");
        }
        context.append('\n');
        context.append("Recent chat visible to you:\n").append(this.recentChatContext()).append('\n');
        context.append("Last action result: ").append(this.aiLastActions.getOrDefault(snapshot.id(), "none")).append('\n');
        context.append("Return only bracketed action lines. No prose, no reasoning, no analysis. Execute quickly: prefer 1-4 useful actions. If dead, first use [respawn]. Use [goto:x y z,ticks=240,sprint=true] or [follow:player=name,ticks=260,distance=2.4] for movement. Use [move:forward=1,ticks=60,sprint=true], [sneak:on], [sneak:off], [jump]. For combat use [attack-player:player=name,ticks=120], [attack-nearest:ticks=120], or [look-at-player:player=name] then [attack:ticks=60]. For building use exactly one refined survival preset: house/home => [build-house], cabin => [build-cabin], cottage => [build-cottage], barn => [build-barn], greenhouse => [build-greenhouse], bunker => [build-bunker], farm => [build-farm], stairs => [build-stairs], tower => [build-tower], bridge => [build-bridge], rope bridge => [build-rope-bridge], wall => [build-wall], platform/floor => [build-platform], dock => [build-dock], well => [build-well], camp => [build-camp], mine entrance => [build-mine], market => [build-market], gate => [build-gate], road => [build-road], windmill => [build-windmill]. Use [clear-build] to dismantle your latest preset build. Never output more than one construction macro in one reply, and do not combine build/worldedit/place for one task. For HunterCore bundled WorldEdit, choose anchors from observed blocks or relative offsets and use [we-fill:dx1=0,dy1=0,dz1=2,dx2=5,dy2=3,dz2=7,material=oak_planks], [we-clear:dx1=0,dy1=0,dz1=2,dx2=5,dy2=3,dz2=7], or [we-undo:steps=1]. WorldEdit commands are executed with temporary permission and can be undone. For armor output every needed armor piece: [wear:material=iron_helmet], [wear:material=iron_chestplate], [wear:material=iron_leggings], [wear:material=iron_boots]. For items use [equip:slot=1,material=oak_planks,amount=64]. Use [say:OK.] only once after useful actions, never for thinking. ")
            .append("For temporary world settings use [gamerule:name=value,duration=60s], [weather:clear,duration=120s], [time:day,duration=60s], or [difficulty:peaceful,duration=120s]. ")
            .append("For temporary gameplay logic use reviewed declarative rules, never arbitrary code: ")
            .append("[rule:trigger=sneak,action=give,material=stone,amount=16,cooldown=3,duration=120s], ")
            .append("[rule:trigger=sneak,action=setblock,material=diamond_block,dx=0,dy=-1,dz=0,duration=30s], ")
            .append("[rule:trigger=chat,contains=gift,action=drop,material=oak_log,amount=4,duration=120s], ")
            .append("[recipe:result=torch,amount=4,ingredients=coal+stick,duration=120s]. ")
            .append("Use [rule:clear] to restore temporary gameplay rules.");
        return context.toString();
    }

    private boolean quickResponseEnabled() {
        return !this.quickResponseMode().equals("off");
    }

    private boolean quickResponseLocked() {
        return this.quickResponseMode().equals("locked");
    }

    private String quickResponseMode() {
        final String mode = HunterToolsPreferences.normalize(this.preferences.stringValue("modules.ai.fake-players.quick-response.mode", "off"));
        return switch (mode) {
            case "locked", "lock", "safe", "on" -> "locked";
            default -> "off";
        };
    }

    private void recordQuickAction(final String id, final String category, final String detail, final long windowMillis) {
        if (!this.quickResponseLocked()) {
            return;
        }
        final long now = System.currentTimeMillis();
        final String key = playerId(id);
        this.quickActionLocks.entrySet().removeIf(entry -> entry.getValue().stream().noneMatch(lock -> lock.expiresAtMillis() > now));
        final List<QuickActionLock> locks = this.quickActionLocks.computeIfAbsent(key, ignored -> new ArrayList<>());
        locks.removeIf(lock -> lock.expiresAtMillis() <= now || lock.category().equals(category));
        locks.add(new QuickActionLock(category, detail, now + Math.max(1_000L, windowMillis)));
    }

    private String suppressedByQuickAction(final String name, final String action) {
        if (!this.quickResponseLocked()) {
            return "";
        }
        final String category = quickActionCategory(action);
        if (category.isBlank()) {
            return "";
        }
        final long now = System.currentTimeMillis();
        final String key = playerId(name);
        final List<QuickActionLock> locks = this.quickActionLocks.get(key);
        if (locks == null || locks.isEmpty()) {
            return "";
        }
        locks.removeIf(lock -> lock.expiresAtMillis() <= now);
        for (final QuickActionLock lock : locks) {
            if (lock.category().equals(category)) {
                return "suppressed duplicate " + category + " after quick response: " + lock.detail();
            }
        }
        return "";
    }

    private static String quickActionCategory(final String action) {
        final String normalized = HunterToolsPreferences.normalize(action);
        if (isConstructionMacroAction(normalized) || isPlaceAction(normalized)) {
            return "construction";
        }
        return switch (normalized) {
            case "respawn", "revive" -> "respawn";
            case "move", "walk", "goto", "go-to", "walk-to", "walkto", "follow", "jump", "sneak", "sprint" -> "movement";
            case "attack", "left-click", "leftclick", "attack-player", "attackplayer", "fight-player", "fightplayer", "attack-nearest", "attacknearest", "fight", "combat" -> "combat";
            case "wear", "armor", "armour", "put-on" -> "armor";
            case "equip", "item", "give-item", "material", "slot", "drop", "dropstack", "swap" -> "item";
            case "clear-build", "clearbuild", "remove-build", "removebuild", "demolish", "dismantle" -> "clear-build";
            default -> "";
        };
    }

    private static boolean isConstructionMacroAction(final String action) {
        final String normalized = HunterToolsPreferences.normalize(action);
        return normalized.equals("build")
            || normalized.startsWith("build-")
            || Set.of(
                "house", "home", "cabin", "hut", "cottage", "bunker", "farm", "stairs", "tower", "bridge", "wall", "platform",
                "barn", "greenhouse", "dock", "well", "camp", "mine", "market", "gate", "road", "rope-bridge", "ropebridge", "windmill",
                "we-fill", "worldedit-fill", "world-edit-fill", "we-set", "worldedit-set", "we-clear", "worldedit-clear", "world-edit-clear"
            ).contains(normalized);
    }

    private static boolean isPlaceAction(final String action) {
        final String normalized = HunterToolsPreferences.normalize(action);
        return normalized.equals("place") || normalized.equals("place-block") || normalized.equals("placeblock");
    }

    private void applyAiPlan(final String name, final String response) {
        final int budget = Math.max(1, Math.min(12, this.preferences.intValue("modules.ai.fake-players.max-actions", 5)));
        final List<String> results = new ArrayList<>();
        int applied = 0;
        boolean constructionMacroApplied = false;
        int placeActionsApplied = 0;
        for (final String body : extractAiActionBodies(response)) {
            if (applied >= budget) {
                break;
            }
            if (body.isBlank()) {
                continue;
            }
            final String action;
            final String args;
            final int colon = body.indexOf(':');
            if (colon >= 0) {
                action = HunterToolsPreferences.normalize(body.substring(0, colon).trim());
                args = body.substring(colon + 1).trim();
            } else {
                action = HunterToolsPreferences.normalize(body);
                args = "";
            }
            final String suppressed = this.suppressedByQuickAction(name, action);
            if (!suppressed.isBlank()) {
                results.add(suppressed);
                applied++;
                continue;
            }
            if (isConstructionMacroAction(action)) {
                if (constructionMacroApplied) {
                    results.add("suppressed extra construction action " + action);
                    applied++;
                    continue;
                }
                constructionMacroApplied = true;
            } else if (isPlaceAction(action)) {
                placeActionsApplied++;
                if (placeActionsApplied > 4) {
                    results.add("suppressed extra place action");
                    applied++;
                    continue;
                }
            }
            final String result = this.applyAiAction(name, action, args);
            if (!result.isBlank()) {
                results.add(result);
                applied++;
            }
        }
        if (results.isEmpty()) {
            final String fallback = this.applyNaturalAiFallback(name, response);
            if (!fallback.isBlank()) {
                results.add(fallback);
            }
        }
        this.aiLastActions.put(playerId(name), results.isEmpty() ? "no valid action: " + truncatePlain(response, 80) : String.join("; ", results));
    }

    private String applyNaturalAiFallback(final String name, final String response) {
        final String text = stripCodeFences(response).replace('\r', ' ').replace('\n', ' ').trim();
        if (text.isBlank()) {
            return "";
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("stop") || text.contains("停止") || text.contains("停下")) {
            this.stopLoops(name);
            return resultLine(this.service().stopActions(name));
        }
        if (lower.contains("jump") || text.contains("跳")) {
            final int times = lower.contains("twice") || text.contains("两") || text.contains("二") ? 2 : 1;
            String result = "";
            for (int i = 0; i < times; i++) {
                result = resultLine(this.service().jump(name));
            }
            return result.isBlank() ? "jump" : result;
        }
        if (lower.contains("attack") || lower.contains("mine") || text.contains("攻击") || text.contains("挖") || text.contains("打")) {
            this.startTimedActionLoop(name, "attack", Math.max(1, this.preferences.intValue("modules.ai.fake-players.max-action-ticks", 80)));
            return "attack";
        }
        if (lower.contains("use") || lower.contains("right click") || text.contains("右键") || text.contains("使用")) {
            this.startTimedActionLoop(name, "use", Math.max(1, this.preferences.intValue("modules.ai.fake-players.max-action-ticks", 80)));
            return "use";
        }
        return "ignored non-action AI text";
    }

    private static List<String> extractAiActionBodies(final String response) {
        final List<String> actions = new ArrayList<>();
        final String text = stripCodeFences(response);
        boolean escaped = false;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '[') {
                start = i + 1;
            } else if (c == ']' && start >= 0) {
                final String body = text.substring(start, i).trim();
                if (!body.isBlank() && body.length() <= 320) {
                    actions.add(body);
                }
                start = -1;
            }
        }
        return actions;
    }

    private static String stripCodeFences(final String response) {
        if (response == null) {
            return "";
        }
        return response
            .replace("```json", "")
            .replace("```text", "")
            .replace("```", "")
            .trim();
    }

    private static String responseFingerprint(final String response) {
        return HunterToolsPreferences.normalize(stripCodeFences(response))
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String applyAiAction(final String name, final String action, final String args) {
        final String blocked = this.guardHighRiskAction(name, action);
        if (!blocked.isBlank()) {
            return blocked;
        }
        return switch (action) {
            case "respawn", "revive" -> resultLine(this.service().respawn(name));
            case "look" -> this.aiLook(name, args);
            case "look-at", "lookat" -> this.aiLookAt(name, args);
            case "turn", "rotate" -> this.aiTurn(name, args);
            case "look-at-player", "lookatplayer", "face-player", "faceplayer" -> this.aiLookAtPlayer(name, args);
            case "move", "walk" -> this.aiMove(name, args);
            case "goto", "go-to", "walk-to", "walkto" -> this.aiGoto(name, args);
            case "follow" -> this.aiFollow(name, args);
            case "mine", "dig", "break" -> this.aiTimedAction(name, "attack", args, "mine", true);
            case "attack", "left-click", "leftclick" -> this.aiTimedAction(name, "attack", args, "attack", true);
            case "attack-player", "attackplayer", "fight-player", "fightplayer" -> this.aiAttackPlayer(name, args);
            case "attack-nearest", "attacknearest", "fight", "combat" -> this.aiAttackNearest(name, args);
            case "use", "right-click", "rightclick", "interact" -> this.aiTimedAction(name, "use", args, "use", false);
            case "place", "place-block", "placeblock" -> this.aiPlace(name, args);
            case "build", "build-house", "house", "home", "build-cabin", "cabin", "hut", "cottage", "build-cottage",
                "build-bunker", "bunker", "build-farm", "farm", "build-stairs", "stairs", "build-tower", "tower",
                "build-bridge", "bridge", "build-rope-bridge", "rope-bridge", "ropebridge", "build-wall", "wall",
                "build-platform", "platform", "build-barn", "barn", "build-greenhouse", "greenhouse", "build-dock", "dock",
                "build-well", "well", "build-camp", "camp", "build-mine", "build-market", "market",
                "build-gate", "gate", "build-road", "road", "build-windmill", "windmill" -> this.aiBuildPreset(name, action, args);
            case "clear-build", "clearbuild", "remove-build", "removebuild", "demolish", "dismantle" -> this.aiClearBuild(name);
            case "equip", "item", "give-item", "material" -> this.aiEquip(name, args);
            case "wear", "armor", "armour", "put-on" -> this.aiWear(name, args);
            case "rule", "gameplay-rule", "temporary-rule", "temp-rule" -> this.gameplayRuleManager.applyAiRule(args);
            case "recipe", "crafting-recipe" -> this.gameplayRuleManager.applyAiRule("action=recipe " + args);
            case "gamerule", "game-rule" -> this.aiGameRule(name, args);
            case "weather" -> this.aiWeather(name, args);
            case "time", "set-time" -> this.aiTime(name, args);
            case "difficulty", "diff" -> this.aiDifficulty(name, args);
            case "we-fill", "worldedit-fill", "world-edit-fill", "we-set", "worldedit-set" -> this.aiWorldEditFill(name, args, false);
            case "we-clear", "worldedit-clear", "world-edit-clear" -> this.aiWorldEditFill(name, args, true);
            case "we-undo", "worldedit-undo", "world-edit-undo", "undo-worldedit" -> this.aiWorldEditUndo(name, args);
            case "command", "cmd", "dispatch", "run-command", "runcommand" -> this.aiDispatchCommand(name, args);
            case "say", "chat" -> this.aiSay(name, args);
            case "wait", "pause" -> "wait " + Math.max(1, intArg(args, "ticks", 20)) + " ticks";
            case "jump" -> resultLine(this.service().jump(name));
            case "sneak" -> resultLine(this.service().setSneaking(name, parseOnOff(args, true)));
            case "sprint" -> resultLine(this.service().setSprinting(name, parseOnOff(args, true)));
            case "slot" -> this.aiSlot(name, args);
            case "drop" -> resultLine(this.service().dropSelected(name, false));
            case "dropstack" -> resultLine(this.service().dropSelected(name, true));
            case "swap" -> resultLine(this.service().swapHands(name));
            case "stop" -> {
                this.stopLoops(name);
                yield resultLine(this.service().stopActions(name));
            }
            default -> "";
        };
    }

    private String guardHighRiskAction(final String name, final String action) {
        if (this.isAiFree(name)) {
            return "";
        }
        if (!this.preferences.booleanValue("modules.ai.fake-players.high-risk-protection", true)) {
            return "";
        }
        final HighRiskAction risk = this.detectHighRiskAction(name, action);
        if (risk == null) {
            return "";
        }
        final String key = playerId(name);
        final FakeAiProfile profile = this.aiProfiles.get(key);
        if (profile != null && profile.highRiskAllowedUntilMillis() > System.currentTimeMillis()) {
            this.aiProfiles.put(key, new FakeAiProfile(
                profile.id(), profile.name(), profile.enabled(), profile.goal(),
                profile.controllerUuid(), profile.controllerName(), 0L, profile.remainingTurns()
            ));
            this.pendingRiskApprovals.remove(key);
            return "";
        }
        final long expiresAt = System.currentTimeMillis() + Math.max(15, this.preferences.intValue("modules.ai.fake-players.high-risk-approval-window-seconds", 120)) * 1000L;
        final PendingRiskApproval pending = new PendingRiskApproval(
            key,
            name,
            risk.label(),
            risk.detail(),
            profile == null ? null : profile.controllerUuid(),
            profile == null ? "" : profile.controllerName(),
            expiresAt
        );
        this.pendingRiskApprovals.put(key, pending);
        this.notifyAdminsForRiskApproval(pending);
        this.stopLoops(name);
        this.service().stopActions(name);
        return "awaiting admin approval: " + risk.label();
    }

    private @Nullable HighRiskAction detectHighRiskAction(final String name, final String action) {
        if (!(action.equals("use") || action.equals("right-click") || action.equals("rightclick") || action.equals("interact")
            || action.equals("place") || action.equals("place-block") || action.equals("placeblock") || action.equals("build")
            || action.equals("attack") || action.equals("attack-player") || action.equals("attackplayer") || action.equals("attack-nearest") || action.equals("attacknearest")
            || action.equals("fight") || action.equals("combat") || action.equals("left-click") || action.equals("leftclick")
            || action.equals("mine") || action.equals("dig") || action.equals("break"))) {
            return null;
        }
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return null;
        }
        final Player player = Bukkit.getPlayer(snapshot.get().uuid());
        if (player == null) {
            return null;
        }
        final ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && HIGH_RISK_MATERIALS.contains(mainHand.getType())) {
            return new HighRiskAction(mainHand.getType().key().value(), "holding " + mainHand.getType().key().value());
        }
        final Location eye = player.getEyeLocation();
        final RayTraceResult ray = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), 6.0D, FluidCollisionMode.NEVER, true);
        if (ray != null && ray.getHitBlock() != null) {
            final Material target = ray.getHitBlock().getType();
            if (target == Material.TNT || target == Material.RESPAWN_ANCHOR) {
                return new HighRiskAction(target.key().value(), "targeting " + target.key().value());
            }
        }
        return null;
    }

    private void notifyAdminsForRiskApproval(final PendingRiskApproval pending) {
        final String message = ChatColor.RED + "[HunterCore] " + ChatColor.YELLOW + pending.fakePlayerName()
            + ChatColor.RED + " requested high-risk AI action: " + ChatColor.WHITE + pending.label()
            + ChatColor.GRAY + " (" + pending.detail() + ")"
            + (pending.controllerName().isBlank() ? "" : ChatColor.GRAY + " requested by " + pending.controllerName())
            + ChatColor.DARK_GRAY + " | /player ai " + pending.fakePlayerName() + " approve";
        for (final Player online : Bukkit.getOnlinePlayers()) {
            if (online.isOp() || online.hasPermission("huntertools.command.admin")) {
                online.sendMessage(message);
            }
        }
        this.plugin.getLogger().warning("High-risk fake player action blocked for " + pending.fakePlayerName() + ": " + pending.label() + " (" + pending.detail() + ")");
    }

    private String aiLook(final String name, final String args) {
        final double keyedYaw = doubleArg(args, "yaw", Double.NaN);
        final double keyedPitch = doubleArg(args, "pitch", Double.NaN);
        if (!Double.isNaN(keyedYaw) && !Double.isNaN(keyedPitch)) {
            return resultLine(this.service().look(name, (float) keyedYaw, (float) keyedPitch));
        }
        final double[] values = parseNumbers(args, 2);
        if (values.length < 2) {
            return "";
        }
        return resultLine(this.service().look(name, (float) values[0], (float) values[1]));
    }

    private String aiLookAt(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        final double[] values = coordinateArgs(args);
        if (snapshot.isEmpty() || values.length < 3) {
            return "";
        }
        final float[] rotation = rotationTo(snapshot.get().location(), values[0], values[1], values[2]);
        return resultLine(this.service().look(name, rotation[0], rotation[1]));
    }

    private String aiTurn(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final double yaw = doubleArg(args, "yaw", doubleArg(args, "dyaw", Double.NaN));
        final double pitch = doubleArg(args, "pitch", doubleArg(args, "dpitch", 0.0D));
        final double[] values = parseNumbers(args, 2);
        final double yawDelta = Double.isNaN(yaw) ? (values.length >= 1 ? values[0] : 0.0D) : yaw;
        final double pitchDelta = values.length >= 2 && !hasKey(args, "pitch") && !hasKey(args, "dpitch") ? values[1] : pitch;
        final Location location = snapshot.get().location();
        return resultLine(this.service().look(
            name,
            (float) (location.getYaw() + clamp(yawDelta, -180.0D, 180.0D)),
            (float) clamp(location.getPitch() + clamp(pitchDelta, -90.0D, 90.0D), -90.0D, 90.0D)
        ));
    }

    private String aiLookAtPlayer(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        final Player target = playerArg(args, snapshot.map(FakePlayerSnapshot::location).orElse(null));
        if (snapshot.isEmpty() || target == null) {
            return "";
        }
        final Location eye = target.getEyeLocation();
        final float[] rotation = rotationTo(snapshot.get().location(), eye.getX(), eye.getY(), eye.getZ());
        return resultLine(this.service().look(name, rotation[0], rotation[1]));
    }

    private String aiMove(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-movement", true)) {
            return "movement disabled";
        }
        final MovePlan plan = movePlan(args);
        if (plan == null) {
            return "";
        }
        if (plan.ticks() <= 1) {
            return resultLine(this.service().move(name, plan.forward(), plan.sideways(), plan.jump(), plan.sprinting(), plan.sneaking()));
        }
        this.startMoveLoop(name, plan);
        return "move " + plan.ticks() + " ticks";
    }

    private String aiGoto(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-movement", true)) {
            return "movement disabled";
        }
        final double[] values = coordinateArgs(args);
        if (values.length < 3) {
            return "";
        }
        final int ticks = Math.max(1, Math.min(
            Math.max(200, this.preferences.intValue("modules.ai.fake-players.max-move-ticks", 200)),
            intArg(args, "ticks", 60)
        ));
        final boolean sprint = parseBooleanArg(args, "sprint", true);
        this.startGotoLoop(name, values[0], values[1], values[2], ticks, sprint);
        return "goto " + format(values[0]) + "," + format(values[1]) + "," + format(values[2]);
    }

    private String aiFollow(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-movement", true)) {
            return "movement disabled";
        }
        final var snapshot = this.service().snapshot(name);
        final Player target = playerArg(args, snapshot.map(FakePlayerSnapshot::location).orElse(null));
        if (target == null) {
            return "";
        }
        final int ticks = Math.max(1, Math.min(
            Math.max(200, this.preferences.intValue("modules.ai.fake-players.max-move-ticks", 200)),
            intArg(args, "ticks", 80)
        ));
        final double distance = clamp(doubleArg(args, "distance", 2.5D), 1.0D, 8.0D);
        final boolean sprint = parseBooleanArg(args, "sprint", true);
        this.startFollowLoop(name, target.getUniqueId(), ticks, distance, sprint);
        return "follow " + target.getName();
    }

    private String aiTimedAction(final String name, final String action, final String args, final String label, final boolean breaking) {
        if (breaking && !this.preferences.booleanValue("modules.ai.fake-players.allow-breaking", true)) {
            return "breaking disabled";
        }
        if (!breaking && !this.preferences.booleanValue("modules.ai.fake-players.allow-interaction", true)) {
            return "interaction disabled";
        }
        final int ticks = Math.max(1, Math.min(
            Math.max(1, this.preferences.intValue("modules.ai.fake-players.max-action-ticks", 80)),
            intArg(args, "ticks", action.equals("attack") ? 40 : 1)
        ));
        if (ticks <= 1) {
            return resultLine(this.runAction(action, name));
        }
        this.startTimedActionLoop(name, action, ticks);
        return label + " " + ticks + " ticks";
    }

    private String aiAttackPlayer(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        final Player target = playerArg(args, snapshot.map(FakePlayerSnapshot::location).orElse(null));
        if (snapshot.isEmpty() || target == null) {
            return "";
        }
        if (target.getUniqueId().equals(snapshot.get().uuid())) {
            return "attack failed: target is self";
        }
        final int ticks = Math.max(20, Math.min(
            Math.max(80, this.preferences.intValue("modules.ai.fake-players.max-action-ticks", 80) * 3),
            intArg(args, "ticks", 120)
        ));
        this.startCombatLoop(name, target.getUniqueId(), ticks, true);
        return "attack player " + target.getName();
    }

    private String aiAttackNearest(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final Entity target = nearestCombatTarget(snapshot.get(), args);
        if (target == null) {
            return "attack failed: no nearby target";
        }
        final int ticks = Math.max(20, Math.min(
            Math.max(80, this.preferences.intValue("modules.ai.fake-players.max-action-ticks", 80) * 3),
            intArg(args, "ticks", 120)
        ));
        this.startCombatLoop(name, target.getUniqueId(), ticks, true);
        return "attack nearest " + target.getName();
    }

    private String aiPlace(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-placing", true)) {
            return "placing disabled";
        }
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final Location target = placeTarget(snapshot.get(), args);
        if (target == null || target.getWorld() == null) {
            return "";
        }
        final int maxDistance = Math.max(1, this.preferences.intValue("modules.ai.fake-players.max-place-distance-blocks", 6));
        if (snapshot.get().location().getWorld() == null
            || !snapshot.get().location().getWorld().equals(target.getWorld())
            || snapshot.get().location().distanceSquared(target) > maxDistance * maxDistance) {
            return "place target too far";
        }
        final int slot = intArg(args, "slot", -1);
        if (slot >= 1 && slot <= 9) {
            final FakePlayerActionResult slotResult = this.service().setSelectedSlot(name, slot);
            if (!slotResult.success()) {
                return resultLine(slotResult);
            }
        }
        final Block targetBlock = target.getBlock();
        if (!targetBlock.getType().isAir()) {
            return "place blocked: target occupied by " + targetBlock.getType().key().value();
        }
        final BlockFace requestedFace = blockFaceArg(args);
        final PlaceSurface surface = requestedFace == null ? autoPlaceSurface(targetBlock) : requestedPlaceSurface(targetBlock, requestedFace);
        if (surface == null || surface.clickedBlock().getType().isAir()) {
            return "place blocked: no adjacent support";
        }
        final float[] rotation = rotationTo(snapshot.get().location(), targetBlock.getX() + 0.5D, targetBlock.getY() + 0.5D, targetBlock.getZ() + 0.5D);
        this.service().look(name, rotation[0], rotation[1]);
        return resultLine(this.service().placeBlock(name, surface.clickedBlock().getLocation(), surface.face()));
    }

    private String aiGameRule(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-game-rules", true)) {
            return "game rule changes disabled";
        }
        final World world = this.aiWorld(name, args);
        if (world == null) {
            return "";
        }
        final String ruleName = firstNonBlank(valueFor(args, "name"), valueFor(args, "rule"), firstValue(args), firstAssignmentKey(args));
        final String rawValue = firstNonBlank(valueFor(args, "value"), valueFor(args, "set"), firstAssignmentValue(args), valueAfterFirst(args));
        if (ruleName.isBlank() || rawValue.isBlank()) {
            return "";
        }
        final GameRule<?> rule = GameRule.getByName(ruleName);
        if (rule == null) {
            return "unknown gamerule " + ruleName;
        }
        final Object previous = world.getGameRuleValue(rule);
        final Object value = parseGameRuleValue(rule, rawValue);
        if (value == null) {
            return "invalid gamerule value " + rawValue;
        }
        if (!setGameRuleValue(world, rule, value)) {
            return "failed gamerule " + rule.getName();
        }
        final int seconds = durationSeconds(args, 0);
        if (seconds > 0 && previous != null) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> setGameRuleValue(world, rule, previous), seconds * 20L);
        }
        return "gamerule " + rule.getName() + "=" + value + (seconds > 0 ? " for " + seconds + "s" : "");
    }

    private String aiWeather(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-game-rules", true)) {
            return "weather changes disabled";
        }
        final World world = this.aiWorld(name, args);
        final String mode = HunterToolsPreferences.normalize(firstNonBlank(valueFor(args, "mode"), firstValue(args)));
        if (world == null || mode.isBlank()) {
            return "";
        }
        final boolean previousStorm = world.hasStorm();
        final boolean previousThunder = world.isThundering();
        switch (mode) {
            case "clear", "sun", "sunny" -> {
                world.setStorm(false);
                world.setThundering(false);
            }
            case "rain", "storm" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "thunder", "thundering" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            default -> {
                return "unknown weather " + mode;
            }
        }
        final int seconds = durationSeconds(args, 0);
        if (seconds > 0) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                world.setStorm(previousStorm);
                world.setThundering(previousThunder);
            }, seconds * 20L);
        }
        return "weather " + mode + (seconds > 0 ? " for " + seconds + "s" : "");
    }

    private String aiTime(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-game-rules", true)) {
            return "time changes disabled";
        }
        final World world = this.aiWorld(name, args);
        if (world == null) {
            return "";
        }
        final long previous = world.getTime();
        final String value = firstNonBlank(valueFor(args, "value"), valueFor(args, "set"), firstValue(args));
        final Long ticks = parseTimeTicks(value);
        if (ticks == null) {
            return "unknown time " + value;
        }
        world.setTime(ticks);
        final int seconds = durationSeconds(args, 0);
        if (seconds > 0) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> world.setTime(previous), seconds * 20L);
        }
        return "time " + ticks + (seconds > 0 ? " for " + seconds + "s" : "");
    }

    private String aiDifficulty(final String name, final String args) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-game-rules", true)) {
            return "difficulty changes disabled";
        }
        final World world = this.aiWorld(name, args);
        final String value = HunterToolsPreferences.normalize(firstNonBlank(valueFor(args, "value"), valueFor(args, "mode"), firstValue(args)));
        if (world == null || value.isBlank()) {
            return "";
        }
        final Difficulty difficulty = switch (value) {
            case "peaceful", "0" -> Difficulty.PEACEFUL;
            case "easy", "1" -> Difficulty.EASY;
            case "normal", "2" -> Difficulty.NORMAL;
            case "hard", "3" -> Difficulty.HARD;
            default -> null;
        };
        if (difficulty == null) {
            return "unknown difficulty " + value;
        }
        final Difficulty previous = world.getDifficulty();
        world.setDifficulty(difficulty);
        final int seconds = durationSeconds(args, 0);
        if (seconds > 0) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> world.setDifficulty(previous), seconds * 20L);
        }
        return "difficulty " + difficulty.name().toLowerCase(Locale.ROOT) + (seconds > 0 ? " for " + seconds + "s" : "");
    }

    private String aiWorldEditFill(final String name, final String args, final boolean clear) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-placing", true)) {
            return "worldedit disabled: placing disabled";
        }
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            return "worldedit unavailable";
        }
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final Player player = Bukkit.getPlayer(snapshot.get().uuid());
        if (player == null) {
            return "worldedit failed: fake player is not online";
        }
        final WorldEditRegion region = worldEditRegion(snapshot.get(), args);
        if (region == null) {
            return "worldedit failed: missing region anchors";
        }
        final int maxVolume = Math.max(1, this.preferences.intValue("modules.ai.fake-players.worldedit.max-volume-blocks", 8192));
        if (region.volume() > maxVolume) {
            return "worldedit blocked: region volume " + region.volume() + " exceeds " + maxVolume;
        }
        final Material material = clear ? Material.AIR : materialArg(args);
        if (material == null || (!clear && !material.isBlock())) {
            return "worldedit failed: unknown block material";
        }
        if (!clear && this.preferences.booleanValue("modules.ai.fake-players.high-risk-protection", true) && HIGH_RISK_MATERIALS.contains(material)) {
            return "worldedit blocked: high-risk material " + material.key().value();
        }
        final String pattern = clear ? "air" : material.key().value();
        final boolean ok = this.runPrivilegedWorldEdit(player, List.of(
            "/pos1 " + blockCoords(region.first()),
            "/pos2 " + blockCoords(region.second()),
            "/set " + pattern
        ));
        return ok
            ? "worldedit " + (clear ? "cleared" : "set " + pattern) + " volume=" + region.volume()
            : "worldedit command failed";
    }

    private String aiWorldEditUndo(final String name, final String args) {
        if (Bukkit.getPluginManager().getPlugin("WorldEdit") == null) {
            return "worldedit unavailable";
        }
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final Player player = Bukkit.getPlayer(snapshot.get().uuid());
        if (player == null) {
            return "worldedit undo failed: fake player is not online";
        }
        final int steps = Math.max(1, Math.min(10, intArg(args, "steps", intArg(args, "count", 1))));
        boolean ok = true;
        for (int i = 0; i < steps; i++) {
            ok &= this.runPrivilegedWorldEdit(player, List.of("/undo"));
        }
        return ok ? "worldedit undo " + steps : "worldedit undo command failed";
    }

    private String aiSlot(final String name, final String args) {
        final int slot = intArg(args, "slot", -1);
        if (slot < 1 || slot > 9) {
            return "";
        }
        return resultLine(this.service().setSelectedSlot(name, slot));
    }

    private String aiEquip(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final Player player = Bukkit.getPlayer(snapshot.get().uuid());
        if (player == null) {
            return "equip failed: fake player is not online";
        }
        final int slot = intArg(args, "slot", 1);
        if (slot < 1 || slot > 9) {
            return "equip failed: slot must be 1-9";
        }
        final Material material = materialArg(args);
        if (material == null || !material.isItem()) {
            return "equip failed: unknown item material";
        }
        if (this.preferences.booleanValue("modules.ai.fake-players.high-risk-protection", true) && HIGH_RISK_MATERIALS.contains(material)) {
            return "equip blocked: high-risk material " + material.key().value();
        }
        final int amount = Math.max(1, Math.min(material.getMaxStackSize(), intArg(args, "amount", material.getMaxStackSize())));
        player.getInventory().setItem(slot - 1, new ItemStack(material, amount));
        final FakePlayerActionResult slotResult = this.service().setSelectedSlot(name, slot);
        return slotResult.success()
            ? "equipped " + material.key().value() + " in slot " + slot
            : resultLine(slotResult);
    }

    private String aiWear(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final Player player = Bukkit.getPlayer(snapshot.get().uuid());
        if (player == null) {
            return "wear failed: fake player is not online";
        }
        final Material material = materialArg(args);
        if (material == null || !material.isItem()) {
            return "wear failed: unknown armor material";
        }
        final String type = material.name().toLowerCase(Locale.ROOT);
        if (!(type.endsWith("_helmet") || type.equals("turtle_helmet") || type.equals("carved_pumpkin")
            || type.endsWith("_chestplate") || type.equals("elytra") || type.endsWith("_leggings") || type.endsWith("_boots"))) {
            return "wear failed: " + material.key().value() + " is not armor";
        }
        final int inventorySlot = firstInventorySlot(player, material);
        final FakePlayerActionResult result = this.service().equipArmor(name, new ItemStack(material, 1));
        if (result.success() && inventorySlot >= 0) {
            final ItemStack existing = player.getInventory().getItem(inventorySlot);
            if (existing != null && existing.getType() == material) {
                final int nextAmount = existing.getAmount() - 1;
                player.getInventory().setItem(inventorySlot, nextAmount <= 0 ? null : new ItemStack(material, nextAmount));
                player.updateInventory();
            }
        }
        return resultLine(result);
    }

    private static int firstInventorySlot(final Player player, final Material material) {
        final ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            final ItemStack item = contents[slot];
            if (item != null && item.getType() == material && item.getAmount() > 0) {
                return slot;
            }
        }
        return -1;
    }

    private String aiBuildPreset(final String name, final String action, final String args) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        String kind = firstNonBlank(valueFor(args, "kind"), valueFor(args, "type"), valueFor(args, "preset"));
        if (kind.isBlank()) {
            kind = switch (action) {
                case "build-tower", "tower" -> "tower";
                case "build-bridge", "bridge" -> "bridge";
                case "build-wall", "wall" -> "wall";
                case "build-platform", "platform" -> "platform";
                case "build-cabin", "cabin", "hut" -> "cabin";
                case "build-cottage", "cottage" -> "cottage";
                case "build-bunker", "bunker" -> "bunker";
                case "build-farm", "farm" -> "farm";
                case "build-stairs", "stairs" -> "stairs";
                case "build-rope-bridge", "rope-bridge", "ropebridge" -> "rope-bridge";
                case "build-barn", "barn" -> "barn";
                case "build-greenhouse", "greenhouse" -> "greenhouse";
                case "build-dock", "dock" -> "dock";
                case "build-well", "well" -> "well";
                case "build-camp", "camp" -> "camp";
                case "build-mine" -> "mine";
                case "build-market", "market" -> "market";
                case "build-gate", "gate" -> "gate";
                case "build-road", "road" -> "road";
                case "build-windmill", "windmill" -> "windmill";
                default -> "house";
            };
        }
        this.startBuildPreset(name, snapshot.get().location(), HunterToolsPreferences.normalize(kind));
        return "building " + HunterToolsPreferences.normalize(kind);
    }

    private String aiClearBuild(final String name) {
        this.stopLoop(name, "build");
        this.buildPlans.remove(playerId(name));
        final List<BuildStep> active = this.activeBuildPlacements.remove(playerId(name));
        final List<BuildStep> target;
        if (active != null && !active.isEmpty()) {
            target = active;
        } else {
            final Deque<List<BuildStep>> history = this.buildHistory.get(playerId(name));
            target = history == null || history.isEmpty() ? List.of() : history.removeLast();
        }
        if (target.isEmpty()) {
            return "clear-build skipped: no recorded build";
        }
        int cleared = 0;
        for (int i = target.size() - 1; i >= 0; i--) {
            final BuildStep step = target.get(i);
            final Block block = step.location().getBlock();
            if (block.getType() == step.material()) {
                block.setType(Material.AIR, false);
                cleared++;
            }
        }
        this.aiLastActions.put(playerId(name), "cleared build " + cleared + "/" + target.size());
        return "cleared latest build " + cleared + "/" + target.size();
    }

    private String aiSay(final String name, final String args) {
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final String message = sanitizeChat(args);
        if (message.isBlank()) {
            return "";
        }
        if (looksLikeAiMetaText(message)) {
            return "blocked AI meta chat";
        }
        if (this.repeatedFakeAiLine(snapshot.get().id(), message, 45_000L)) {
            return "suppressed duplicate say";
        }
        Bukkit.broadcastMessage("<" + snapshot.get().name() + "> " + message);
        this.plugin.publishSyntheticChat(snapshot.get().name(), "ai-fake-player", message);
        return "say";
    }

    private String aiDispatchCommand(final String name, final String args) {
        if (!this.isAiFree(name)) {
            return "command rejected: AI-Free mode required";
        }
        final var snapshot = this.service().snapshot(name);
        if (snapshot.isEmpty()) {
            return "";
        }
        final String command = sanitizeCommand(args)
            .replace("%name%", snapshot.get().name())
            .replace("%bot%", snapshot.get().name())
            .trim();
        if (command.isBlank()) {
            return "command skipped: empty";
        }
        if (command.length() > 512) {
            return "command rejected: too long";
        }
        final boolean handled = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        return handled ? "command /" + command : "command dispatched but not handled /" + command;
    }

    private boolean repeatedFakeAiLine(final String id, final String message, final long windowMillis) {
        final long now = System.currentTimeMillis();
        this.recentFakeAiLines.entrySet().removeIf(entry -> now - entry.getValue().createdAtMillis() > Math.max(windowMillis, 60_000L));
        final String fingerprint = messageFingerprint(message);
        if (fingerprint.isBlank()) {
            return false;
        }
        final String key = playerId(id);
        final RecentFakeAiLine previous = this.recentFakeAiLines.put(key, new RecentFakeAiLine(fingerprint, now));
        return previous != null && previous.fingerprint().equals(fingerprint) && now - previous.createdAtMillis() <= windowMillis;
    }

    private void startHouseBuild(final String name, final Location origin) {
        this.startBuildPreset(name, origin, "house");
    }

    private void startBuildPreset(final String name, final Location origin, final String kind) {
        if (!this.preferences.booleanValue("modules.ai.fake-players.allow-placing", true) || origin.getWorld() == null) {
            return;
        }
        this.stopLoop(name, "build");
        this.finishActiveBuild(name);
        final List<BuildStep> steps = buildPresetPlan(origin, kind);
        if (steps.isEmpty()) {
            return;
        }
        this.buildPlans.put(playerId(name), steps);
        this.activeBuildPlacements.put(playerId(name), new ArrayList<>());
        final int[] index = {0};
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            final var snapshot = this.service().snapshot(name);
            if (snapshot.isEmpty() || index[0] >= steps.size()) {
                this.stopLoop(name, "build");
                this.buildPlans.remove(playerId(name));
                this.finishActiveBuild(name);
                return;
            }
            int placed = 0;
            while (index[0] < steps.size() && placed < 3) {
                final BuildStep step = steps.get(index[0]++);
                final Block block = step.location().getBlock();
                if (block.getType().isAir() || block.isPassable()) {
                    if (step.blockData() == null) {
                        block.setType(step.material(), false);
                    } else {
                        block.setBlockData(step.blockData(), false);
                    }
                    this.activeBuildPlacements.computeIfAbsent(playerId(name), ignored -> new ArrayList<>()).add(step);
                    placed++;
                    final Location fakeLocation = snapshot.get().location();
                    final float[] rotation = rotationTo(fakeLocation, block.getX() + 0.5D, block.getY() + 0.5D, block.getZ() + 0.5D);
                    this.service().look(name, rotation[0], rotation[1]);
                }
            }
            this.aiLastActions.put(playerId(name), "building " + kind + " " + index[0] + "/" + steps.size());
        }, 1L, 2L);
        this.loops.put(loopKey(name, "build"), task);
    }

    private void finishActiveBuild(final String name) {
        final List<BuildStep> placed = this.activeBuildPlacements.remove(playerId(name));
        if (placed == null || placed.isEmpty()) {
            return;
        }
        final Deque<List<BuildStep>> history = this.buildHistory.computeIfAbsent(playerId(name), ignored -> new ArrayDeque<>());
        history.addLast(new ArrayList<>(placed));
        while (history.size() > 16) {
            history.removeFirst();
        }
    }

    private void startLoop(final CommandSender sender, final String name, final String action) {
        this.stopLoop(name, action);
        final String key = loopKey(name, action);
        final long interval = Math.max(1L, this.preferences.intValue("modules.real-fake-players.action-interval-ticks", 1));
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            final FakePlayerActionResult result = this.runAction(action, name);
            if (!result.success()) {
                this.stopLoop(name, action);
                sender.sendMessage(ChatColor.RED + result.message());
            }
        }, 0L, interval);
        this.loops.put(key, task);
        sender.sendMessage("Started " + action + " loop for " + name + ".");
    }

    private void startTimedActionLoop(final String name, final String action, final int ticks) {
        this.stopLoop(name, action);
        final int[] remaining = {Math.max(1, ticks)};
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            final FakePlayerActionResult result = this.runAction(action, name);
            if (!result.success() || --remaining[0] <= 0) {
                this.stopLoop(name, action);
                if (action.equals("attack")) {
                    this.service().stopActions(name);
                }
            }
        }, 1L, Math.max(1L, this.preferences.intValue("modules.real-fake-players.action-interval-ticks", 1)));
        this.loops.put(loopKey(name, action), task);
    }

    private void startMoveLoop(final String name, final MovePlan plan) {
        this.stopLoop(name, "move");
        final int maxTicks = Math.max(200, this.preferences.intValue("modules.ai.fake-players.max-move-ticks", 200));
        final int[] remaining = {Math.max(1, Math.min(maxTicks, plan.ticks()))};
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            final FakePlayerActionResult result = this.service().move(name, plan.forward(), plan.sideways(), plan.jump(), plan.sprinting(), plan.sneaking());
            if (!result.success() || --remaining[0] <= 0) {
                this.stopLoop(name, "move");
            }
        }, 1L, 1L);
        this.loops.put(loopKey(name, "move"), task);
    }

    private void startGotoLoop(final String name, final double x, final double y, final double z, final int ticks, final boolean sprinting) {
        this.stopLoop(name, "goto");
        final int maxTicks = Math.max(200, this.preferences.intValue("modules.ai.fake-players.max-move-ticks", 200));
        final int[] remaining = {Math.max(1, Math.min(maxTicks, ticks))};
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            final var snapshot = this.service().snapshot(name);
            if (snapshot.isEmpty()) {
                this.stopLoop(name, "goto");
                return;
            }
            final Location location = snapshot.get().location();
            final double dx = x - location.getX();
            final double dy = y - location.getY();
            final double dz = z - location.getZ();
            if ((dx * dx) + (dy * dy) + (dz * dz) <= 1.44D || --remaining[0] <= 0) {
                this.service().move(name, 0.0D, 0.0D, false, false, false);
                this.stopLoop(name, "goto");
                return;
            }
            final float[] rotation = rotationTo(location, x, y, z);
            this.service().look(name, rotation[0], rotation[1]);
            this.service().move(name, 1.0D, 0.0D, false, sprinting, false);
        }, 1L, 1L);
        this.loops.put(loopKey(name, "goto"), task);
    }

    private void startFollowLoop(final String name, final UUID targetId, final int ticks, final double distance, final boolean sprinting) {
        this.stopLoop(name, "follow");
        final int maxTicks = Math.max(200, this.preferences.intValue("modules.ai.fake-players.max-move-ticks", 200));
        final int[] remaining = {Math.max(1, Math.min(maxTicks, ticks))};
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            final Player target = Bukkit.getPlayer(targetId);
            final var snapshot = this.service().snapshot(name);
            if (target == null || snapshot.isEmpty()) {
                this.stopLoop(name, "follow");
                return;
            }
            final Location location = snapshot.get().location();
            final Location targetLocation = target.getLocation();
            if (location.getWorld() == null || targetLocation.getWorld() == null || !location.getWorld().equals(targetLocation.getWorld())) {
                this.stopLoop(name, "follow");
                return;
            }
            if (--remaining[0] <= 0) {
                this.service().move(name, 0.0D, 0.0D, false, false, false);
                this.stopLoop(name, "follow");
                return;
            }
            if (location.distanceSquared(targetLocation) <= distance * distance) {
                this.service().move(name, 0.0D, 0.0D, false, false, false);
                return;
            }
            final Location eye = target.getEyeLocation();
            final float[] rotation = rotationTo(location, eye.getX(), eye.getY(), eye.getZ());
            this.service().look(name, rotation[0], rotation[1]);
            this.service().move(name, 1.0D, 0.0D, false, sprinting, false);
        }, 1L, 1L);
        this.loops.put(loopKey(name, "follow"), task);
    }

    private void startCombatLoop(final String name, final UUID targetId, final int ticks, final boolean chase) {
        this.stopLoop(name, "combat");
        final int[] remaining = {Math.max(1, ticks)};
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            final Entity target = Bukkit.getEntity(targetId);
            final var snapshot = this.service().snapshot(name);
            if (!(target instanceof final LivingEntity livingTarget) || livingTarget.isDead() || snapshot.isEmpty()) {
                this.stopLoop(name, "combat");
                this.service().move(name, 0.0D, 0.0D, false, false, false);
                return;
            }
            final Location location = snapshot.get().location();
            final Location targetLocation = target.getLocation();
            if (location.getWorld() == null || targetLocation.getWorld() == null || !location.getWorld().equals(targetLocation.getWorld())) {
                this.stopLoop(name, "combat");
                this.service().move(name, 0.0D, 0.0D, false, false, false);
                return;
            }
            if (--remaining[0] <= 0) {
                this.stopLoop(name, "combat");
                this.service().move(name, 0.0D, 0.0D, false, false, false);
                return;
            }
            final Location targetEye = targetLocation.clone().add(0.0D, 1.0D, 0.0D);
            final float[] rotation = rotationTo(location, targetEye.getX(), targetEye.getY(), targetEye.getZ());
            this.service().look(name, rotation[0], rotation[1]);
            final double distanceSquared = location.distanceSquared(targetLocation);
            if (distanceSquared > 9.0D && chase) {
                this.service().move(name, 1.0D, 0.0D, false, true, false);
                return;
            }
            this.service().move(name, 0.0D, 0.0D, false, false, false);
            this.service().attackEntity(name, target);
        }, 1L, 1L);
        this.loops.put(loopKey(name, "combat"), task);
    }

    private FakePlayerActionResult runAction(final String action, final String name) {
        return switch (action) {
            case "jump" -> this.service().jump(name);
            case "use" -> this.service().use(name);
            case "attack" -> this.service().attack(name);
            default -> FakePlayerActionResult.fail("Unknown action: " + action);
        };
    }

    private void stopLoops(final String name) {
        for (final String action : List.of("jump", "use", "attack", "move", "goto", "follow", "combat", "build")) {
            this.stopLoop(name, action);
        }
        this.buildPlans.remove(playerId(name));
        this.finishActiveBuild(name);
    }

    private void stopLoop(final String name, final String action) {
        final BukkitTask task = this.loops.remove(loopKey(name, action));
        if (task != null) {
            task.cancel();
        }
    }

    private void stopAllLoops() {
        for (final BukkitTask task : this.loops.values()) {
            task.cancel();
        }
        this.loops.clear();
        for (final String id : new ArrayList<>(this.activeBuildPlacements.keySet())) {
            this.finishActiveBuild(id);
        }
    }

    private void stopAi(final String nameOrId) {
        final String id = playerId(nameOrId);
        this.stopAiTask(id);
        this.aiProfiles.remove(id);
        this.aiFreePlayers.remove(id);
        this.aiBusy.remove(id);
        this.aiLastActions.remove(id);
        this.aiLastResponseFingerprints.remove(id);
    }

    private void stopAiTask(final String id) {
        final BukkitTask task = this.aiTasks.remove(playerId(id));
        if (task != null) {
            task.cancel();
        }
    }

    private void stopAllAi() {
        for (final BukkitTask task : this.aiTasks.values()) {
            task.cancel();
        }
        this.aiTasks.clear();
        this.aiProfiles.clear();
        this.aiFreePlayers.clear();
        this.aiBusy.clear();
        this.aiLastActions.clear();
        this.aiLastResponseFingerprints.clear();
    }

    private String loopLine(final String name) {
        final List<String> active = new ArrayList<>();
        for (final String action : List.of("jump", "use", "attack", "move", "goto", "follow", "combat", "build")) {
            if (this.loops.containsKey(loopKey(name, action))) {
                active.add(action);
            }
        }
        return active.isEmpty() ? "none" : String.join(",", active);
    }

    private Location location(final CommandSender sender, final String[] args, final int index) {
        if (args.length >= index + 4) {
            final World world = Bukkit.getWorld(args[index]);
            if (world == null) {
                sender.sendMessage("World not found: " + args[index]);
                return null;
            }
            try {
                final double x = Double.parseDouble(args[index + 1]);
                final double y = Double.parseDouble(args[index + 2]);
                final double z = Double.parseDouble(args[index + 3]);
                final float yaw = args.length >= index + 5 ? Float.parseFloat(args[index + 4]) : 0.0F;
                final float pitch = args.length >= index + 6 ? Float.parseFloat(args[index + 5]) : 0.0F;
                return new Location(world, x, y, z, yaw, pitch);
            } catch (final NumberFormatException ex) {
                sender.sendMessage("Coordinates must be numbers.");
                return null;
            }
        }
        if (sender instanceof final Player player) {
            return player.getLocation();
        }
        final World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (world == null) {
            sender.sendMessage("No loaded worlds are available.");
            return null;
        }
        return world.getSpawnLocation().add(0.5D, 0.0D, 0.5D);
    }

    private @Nullable float[] rotation(final CommandSender sender, final String label, final String[] args, final int index) {
        if (args.length == index) {
            if (sender instanceof final Player player) {
                final Location location = player.getLocation();
                return new float[] {location.getYaw(), location.getPitch()};
            }
            sender.sendMessage("Usage: /" + label + " look <name> <yaw pitch|north|south|east|west|up|down>");
            return null;
        }
        if (args.length == index + 1) {
            return switch (HunterToolsPreferences.normalize(args[index])) {
                case "south" -> new float[] {0.0F, 0.0F};
                case "west" -> new float[] {90.0F, 0.0F};
                case "north" -> new float[] {180.0F, 0.0F};
                case "east" -> new float[] {-90.0F, 0.0F};
                case "up" -> new float[] {0.0F, -90.0F};
                case "down" -> new float[] {0.0F, 90.0F};
                default -> {
                    sender.sendMessage("Direction must be one of north, south, east, west, up, down.");
                    yield null;
                }
            };
        }
        try {
            return new float[] {Float.parseFloat(args[index]), Math.max(-90.0F, Math.min(90.0F, Float.parseFloat(args[index + 1])))};
        } catch (final NumberFormatException ex) {
            sender.sendMessage("Yaw and pitch must be numbers.");
            return null;
        }
    }

    private void usage(final CommandSender sender, final String label) {
        sender.sendMessage("Usage: /" + label + " <spawn|remove|list|skin|move|use|attack|ai|info|clear>. Try /" + label + " help.");
        sender.sendMessage(ChatColor.RED + "Danger: OP-only /" + label + " spawn <name> -aifree creates an autonomous AI that can run commands.");
    }

    private void send(final CommandSender sender, final FakePlayerActionResult result) {
        sender.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private HunterFakePlayerService service() {
        return HunterCoreProvider.get().fakePlayers();
    }

    private List<String> names() {
        return this.service().list().stream().map(FakePlayerSnapshot::name).sorted(String::compareToIgnoreCase).toList();
    }

    private static String loopKey(final String name, final String action) {
        return playerId(name) + ":" + action;
    }

    private static String playerId(final String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private boolean isAiFree(final String nameOrId) {
        return this.aiFreePlayers.contains(playerId(nameOrId));
    }

    private static boolean hasAiFreeFlag(final String[] args) {
        for (int i = 2; i < args.length; i++) {
            final String value = HunterToolsPreferences.normalize(args[i]);
            if (value.equals("-aifree") || value.equals("aifree") || value.equals("ai-free") || value.equals("--aifree")) {
                return true;
            }
        }
        return false;
    }

    private static String[] removeAiFreeFlags(final String[] args) {
        final List<String> filtered = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            final String value = HunterToolsPreferences.normalize(args[i]);
            if (i >= 2 && (value.equals("-aifree") || value.equals("aifree") || value.equals("ai-free") || value.equals("--aifree"))) {
                continue;
            }
            filtered.add(args[i]);
        }
        return filtered.toArray(String[]::new);
    }

    private static String locationLine(final Location location) {
        return location.getWorld().getName()
            + " "
            + String.format(Locale.ROOT, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ());
    }

    private static @Nullable Boolean parseToggle(final String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "on", "enable", "enabled", "true", "yes" -> Boolean.TRUE;
            case "off", "disable", "disabled", "false", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static @Nullable GameMode parseGameMode(final String input) {
        return switch (HunterToolsPreferences.normalize(input)) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static String sanitizeCommand(final String command) {
        if (command == null) {
            return "";
        }
        String sanitized = command.replace('\n', ' ').replace('\r', ' ').trim();
        if (sanitized.startsWith("/")) {
            sanitized = sanitized.substring(1).trim();
        }
        return sanitized.length() > 512 ? sanitized.substring(0, 512).trim() : sanitized;
    }

    private static String sanitizeGoal(final String goal) {
        if (goal == null) {
            return "";
        }
        final String sanitized = goal.replace("\r\n", "\n").replace('\r', '\n').trim();
        return sanitized.length() > 2048 ? sanitized.substring(0, 2048).trim() : sanitized;
    }

    private long chatControlCooldown(final String key) {
        final long now = System.currentTimeMillis();
        final long expires = this.chatControlCooldowns.getOrDefault(key, 0L);
        if (expires > now) {
            return expires - now;
        }
        final int seconds = Math.max(0, this.preferences.intValue("modules.ai.fake-players.chat-control.cooldown-seconds", 3));
        if (seconds > 0) {
            this.chatControlCooldowns.put(key, now + seconds * 1000L);
        }
        return 0L;
    }

    private static MovePlan movePlan(final String args) {
        double forward = doubleArg(args, "forward", Double.NaN);
        double sideways = doubleArg(args, "sideways", doubleArg(args, "strafe", 0.0D));
        int ticks = intArg(args, "ticks", 20);
        boolean jump = parseBooleanArg(args, "jump", false);
        boolean sprint = parseBooleanArg(args, "sprint", false);
        boolean sneak = parseBooleanArg(args, "sneak", false);
        for (final String token : tokens(args)) {
            final String normalized = HunterToolsPreferences.normalize(token);
            switch (normalized) {
                case "forward", "ahead" -> forward = 1.0D;
                case "back", "backward" -> forward = -1.0D;
                case "left" -> sideways = -1.0D;
                case "right" -> sideways = 1.0D;
                case "stop" -> {
                    forward = 0.0D;
                    sideways = 0.0D;
                    ticks = 1;
                }
                case "jump" -> jump = true;
                case "sprint" -> sprint = true;
                case "sneak", "shift" -> sneak = true;
                default -> {
                    if (!token.contains("=") && Double.isNaN(forward)) {
                        try {
                            forward = Double.parseDouble(token);
                        } catch (final NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        if (Double.isNaN(forward)) {
            forward = 0.0D;
        }
        final double[] positional = parseNumbers(args, 3);
        if (positional.length >= 1 && !hasKey(args, "forward")) {
            forward = positional[0];
        }
        if (positional.length >= 2 && !hasKey(args, "sideways") && !hasKey(args, "strafe")) {
            sideways = positional[1];
        }
        if (positional.length >= 3 && !hasKey(args, "ticks")) {
            ticks = (int) Math.round(positional[2]);
        }
        if (Double.isNaN(forward) || Double.isNaN(sideways)) {
            return null;
        }
        return new MovePlan(clamp(forward, -1.0D, 1.0D), clamp(sideways, -1.0D, 1.0D), Math.max(1, ticks), jump, sprint, sneak);
    }

    private static String resultLine(final FakePlayerActionResult result) {
        return (result.success() ? "ok: " : "failed: ") + result.message();
    }

    private static boolean parseOnOff(final String args, final boolean fallback) {
        final String value = firstValue(args);
        if (value.isBlank()) {
            return fallback;
        }
        final Boolean parsed = parseToggle(value);
        return parsed == null ? fallback : parsed;
    }

    private static int intArg(final String args, final String key, final int fallback) {
        final String keyed = valueFor(args, key);
        final String value = keyed.isBlank() ? firstValue(args) : keyed;
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }

    private static @Nullable Material materialArg(final String args) {
        String value = valueFor(args, "material");
        if (value.isBlank()) {
            value = valueFor(args, "item");
        }
        if (value.isBlank()) {
            value = firstValue(args);
        }
        if (value.isBlank()) {
            return null;
        }
        final String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace("MINECRAFT:", "");
        return Material.matchMaterial(normalized);
    }

    private static double doubleArg(final String args, final String key, final double fallback) {
        final String keyed = valueFor(args, key);
        if (keyed.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(keyed.trim());
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean parseBooleanArg(final String args, final String key, final boolean fallback) {
        final String value = valueFor(args, key);
        if (value.isBlank()) {
            return fallback;
        }
        final Boolean parsed = parseToggle(value);
        return parsed == null ? fallback : parsed;
    }

    private static double[] parseNumbers(final String args, final int max) {
        final List<Double> values = new ArrayList<>();
        for (final String token : tokens(args)) {
            if (token.contains("=")) {
                continue;
            }
            try {
                values.add(Double.parseDouble(token));
            } catch (final NumberFormatException ignored) {
            }
            if (values.size() >= max) {
                break;
            }
        }
        final double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static double[] coordinateArgs(final String args) {
        final double x = doubleArg(args, "x", Double.NaN);
        final double y = doubleArg(args, "y", Double.NaN);
        final double z = doubleArg(args, "z", Double.NaN);
        if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(z)) {
            return new double[] {x, y, z};
        }
        return parseNumbers(args, 3);
    }

    private static @Nullable Location placeTarget(final FakePlayerSnapshot snapshot, final String args) {
        final Location origin = snapshot.location();
        final World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        final double dx = doubleArg(args, "dx", Double.NaN);
        final double dy = doubleArg(args, "dy", Double.NaN);
        final double dz = doubleArg(args, "dz", Double.NaN);
        if (!Double.isNaN(dx) && !Double.isNaN(dy) && !Double.isNaN(dz)) {
            return new Location(world, origin.getBlockX() + dx, origin.getBlockY() + dy, origin.getBlockZ() + dz);
        }
        final double[] absolute = coordinateArgs(args);
        if (absolute.length < 3) {
            return null;
        }
        return new Location(world, absolute[0], absolute[1], absolute[2]);
    }

    private static @Nullable WorldEditRegion worldEditRegion(final FakePlayerSnapshot snapshot, final String args) {
        final Location origin = snapshot.location();
        final World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        final double dx1 = doubleArg(args, "dx1", Double.NaN);
        final double dy1 = doubleArg(args, "dy1", Double.NaN);
        final double dz1 = doubleArg(args, "dz1", Double.NaN);
        final double dx2 = doubleArg(args, "dx2", Double.NaN);
        final double dy2 = doubleArg(args, "dy2", Double.NaN);
        final double dz2 = doubleArg(args, "dz2", Double.NaN);
        if (!Double.isNaN(dx1) && !Double.isNaN(dy1) && !Double.isNaN(dz1)
            && !Double.isNaN(dx2) && !Double.isNaN(dy2) && !Double.isNaN(dz2)) {
            return worldEditRegion(
                new Location(world, origin.getBlockX() + dx1, origin.getBlockY() + dy1, origin.getBlockZ() + dz1),
                new Location(world, origin.getBlockX() + dx2, origin.getBlockY() + dy2, origin.getBlockZ() + dz2)
            );
        }
        final double x1 = doubleArg(args, "x1", Double.NaN);
        final double y1 = doubleArg(args, "y1", Double.NaN);
        final double z1 = doubleArg(args, "z1", Double.NaN);
        final double x2 = doubleArg(args, "x2", Double.NaN);
        final double y2 = doubleArg(args, "y2", Double.NaN);
        final double z2 = doubleArg(args, "z2", Double.NaN);
        if (!Double.isNaN(x1) && !Double.isNaN(y1) && !Double.isNaN(z1)
            && !Double.isNaN(x2) && !Double.isNaN(y2) && !Double.isNaN(z2)) {
            return worldEditRegion(new Location(world, x1, y1, z1), new Location(world, x2, y2, z2));
        }
        final double[] values = parseNumbers(args, 6);
        if (values.length >= 6) {
            return worldEditRegion(
                new Location(world, values[0], values[1], values[2]),
                new Location(world, values[3], values[4], values[5])
            );
        }
        return null;
    }

    private static WorldEditRegion worldEditRegion(final Location first, final Location second) {
        final int volume = (Math.abs(first.getBlockX() - second.getBlockX()) + 1)
            * (Math.abs(first.getBlockY() - second.getBlockY()) + 1)
            * (Math.abs(first.getBlockZ() - second.getBlockZ()) + 1);
        return new WorldEditRegion(first, second, volume);
    }

    private static String blockCoords(final Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private static @Nullable PlaceSurface autoPlaceSurface(final Block targetBlock) {
        final PlaceSurface[] candidates = {
            new PlaceSurface(targetBlock.getRelative(BlockFace.DOWN), BlockFace.UP),
            new PlaceSurface(targetBlock.getRelative(BlockFace.NORTH), BlockFace.SOUTH),
            new PlaceSurface(targetBlock.getRelative(BlockFace.SOUTH), BlockFace.NORTH),
            new PlaceSurface(targetBlock.getRelative(BlockFace.WEST), BlockFace.EAST),
            new PlaceSurface(targetBlock.getRelative(BlockFace.EAST), BlockFace.WEST),
            new PlaceSurface(targetBlock.getRelative(BlockFace.UP), BlockFace.DOWN)
        };
        for (final PlaceSurface candidate : candidates) {
            if (!candidate.clickedBlock().getType().isAir()) {
                return candidate;
            }
        }
        return null;
    }

    private static PlaceSurface requestedPlaceSurface(final Block targetBlock, final BlockFace face) {
        return new PlaceSurface(targetBlock.getRelative(face.getOppositeFace()), face);
    }

    private static @Nullable BlockFace blockFaceArg(final String args) {
        final String face = valueFor(args, "face").isBlank() ? valueFor(args, "side") : valueFor(args, "face");
        return switch (HunterToolsPreferences.normalize(face)) {
            case "", "auto" -> null;
            case "up", "top" -> BlockFace.UP;
            case "down", "bottom" -> BlockFace.DOWN;
            case "north", "n" -> BlockFace.NORTH;
            case "south", "s" -> BlockFace.SOUTH;
            case "east", "e" -> BlockFace.EAST;
            case "west", "w" -> BlockFace.WEST;
            default -> null;
        };
    }

    private World aiWorld(final String name, final String args) {
        final String worldName = firstNonBlank(valueFor(args, "world"), valueFor(args, "dimension"));
        if (!worldName.isBlank()) {
            return Bukkit.getWorld(worldName);
        }
        final var snapshot = this.service().snapshot(name);
        return snapshot.map(FakePlayerSnapshot::location).map(Location::getWorld).orElse(null);
    }

    private boolean runPrivilegedWorldEdit(final Player player, final List<String> commands) {
        final boolean previousOp = player.isOp();
        final PermissionAttachment attachment = player.addAttachment(this.plugin);
        try {
            attachment.setPermission("worldedit.*", true);
            attachment.setPermission("worldedit.region.*", true);
            attachment.setPermission("worldedit.selection.*", true);
            attachment.setPermission("worldedit.history.undo", true);
            attachment.setPermission("worldedit.history.redo", true);
            player.setOp(true);
            boolean ok = true;
            for (final String command : commands) {
                ok &= this.dispatchWorldEditCommand(player, command);
            }
            return ok;
        } finally {
            player.removeAttachment(attachment);
            player.setOp(previousOp);
        }
    }

    private boolean dispatchWorldEditCommand(final Player player, final String command) {
        final String trimmed = command == null ? "" : command.trim();
        if (trimmed.isBlank()) {
            return false;
        }
        final List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        if (trimmed.startsWith("/")) {
            candidates.add("worldedit:" + trimmed);
            candidates.add(trimmed.substring(1));
            candidates.add("worldedit:" + trimmed.substring(1));
        } else {
            candidates.add("/" + trimmed);
            candidates.add("worldedit:" + trimmed);
            candidates.add("worldedit:/" + trimmed);
        }
        for (final String candidate : candidates) {
            if (Bukkit.dispatchCommand(player, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Object parseGameRuleValue(final GameRule<?> rule, final String rawValue) {
        final Class<?> type = rule.getType();
        if (type == Boolean.class) {
            final Boolean value = parseToggle(rawValue);
            return value == null ? null : value;
        }
        if (type == Integer.class) {
            try {
                return Integer.parseInt(rawValue.trim());
            } catch (final NumberFormatException ex) {
                return null;
            }
        }
        return rawValue;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean setGameRuleValue(final World world, final GameRule<?> rule, final Object value) {
        try {
            return world.setGameRule((GameRule) rule, value);
        } catch (final IllegalArgumentException | ClassCastException ex) {
            return false;
        }
    }

    private static @Nullable Long parseTimeTicks(final String rawValue) {
        final String value = HunterToolsPreferences.normalize(rawValue);
        if (value.isBlank()) {
            return null;
        }
        return switch (value) {
            case "day", "morning" -> 1000L;
            case "noon" -> 6000L;
            case "night" -> 13000L;
            case "midnight" -> 18000L;
            case "sunrise" -> 23000L;
            case "sunset", "evening" -> 12000L;
            default -> {
                try {
                    yield Long.parseLong(value);
                } catch (final NumberFormatException ex) {
                    yield null;
                }
            }
        };
    }

    private static String valueFor(final String args, final String key) {
        final String normalizedKey = HunterToolsPreferences.normalize(key);
        for (final String token : tokens(args)) {
            final int equals = token.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            final String left = HunterToolsPreferences.normalize(token.substring(0, equals));
            if (left.equals(normalizedKey)) {
                return token.substring(equals + 1).trim();
            }
        }
        return "";
    }

    private static String firstAssignmentKey(final String args) {
        for (final String token : tokens(args)) {
            final int equals = token.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            final String key = HunterToolsPreferences.normalize(token.substring(0, equals));
            if (!List.of("duration", "seconds", "sec", "world", "dimension", "value", "set", "name", "rule").contains(key)) {
                return token.substring(0, equals).trim();
            }
        }
        return "";
    }

    private static String firstAssignmentValue(final String args) {
        for (final String token : tokens(args)) {
            final int equals = token.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            final String key = HunterToolsPreferences.normalize(token.substring(0, equals));
            if (!List.of("duration", "seconds", "sec", "world", "dimension", "value", "set", "name", "rule").contains(key)) {
                return token.substring(equals + 1).trim();
            }
        }
        return "";
    }

    private static String valueAfterFirst(final String args) {
        boolean foundFirst = false;
        for (final String token : tokens(args)) {
            if (token.contains("=")) {
                continue;
            }
            if (!foundFirst) {
                foundFirst = true;
                continue;
            }
            return token;
        }
        return "";
    }

    private static String firstNonBlank(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static int durationSeconds(final String args, final int fallback) {
        final String raw = firstNonBlank(valueFor(args, "duration"), valueFor(args, "seconds"), valueFor(args, "sec"));
        if (raw.isBlank()) {
            return fallback;
        }
        final String value = raw.trim().toLowerCase(Locale.ROOT);
        final int multiplier;
        final String number;
        if (value.endsWith("ticks") || value.endsWith("tick")) {
            final String stripped = value.replace("ticks", "").replace("tick", "").trim();
            try {
                return Math.max(0, Math.min(86_400, (int) Math.ceil(Integer.parseInt(stripped) / 20.0D)));
            } catch (final NumberFormatException ex) {
                return fallback;
            }
        } else if (value.endsWith("h")) {
            multiplier = 3600;
            number = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 60;
            number = value.substring(0, value.length() - 1);
        } else if (value.endsWith("s")) {
            multiplier = 1;
            number = value.substring(0, value.length() - 1);
        } else {
            multiplier = 1;
            number = value;
        }
        try {
            return Math.max(0, Math.min(86_400, Integer.parseInt(number.trim()) * multiplier));
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean hasKey(final String args, final String key) {
        return !valueFor(args, key).isBlank();
    }

    private static String firstValue(final String args) {
        for (final String token : tokens(args)) {
            if (!token.contains("=")) {
                return token;
            }
        }
        return "";
    }

    private static @Nullable Player playerArg(final String args, final @Nullable Location origin) {
        final String keyed = valueFor(args, "player");
        final String value = keyed.isBlank() ? firstValue(args) : keyed;
        if (!value.isBlank()) {
            final Player exact = Bukkit.getPlayerExact(value);
            if (exact != null) {
                return exact;
            }
            final Player partial = Bukkit.getPlayer(value);
            if (partial != null) {
                return partial;
            }
        }
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (final Player player : origin.getWorld().getPlayers()) {
            final double distance = player.getLocation().distanceSquared(origin);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean isBuildRequest(final String task) {
        final String lower = task == null ? "" : task.toLowerCase(Locale.ROOT);
        return lower.contains("build")
            || lower.contains("house")
            || lower.contains("home")
            || lower.contains("cabin")
            || lower.contains("hut")
            || lower.contains("cottage")
            || lower.contains("bunker")
            || lower.contains("farm")
            || lower.contains("field")
            || lower.contains("stairs")
            || lower.contains("tower")
            || lower.contains("bridge")
            || lower.contains("wall")
            || lower.contains("platform")
            || lower.contains("barn")
            || lower.contains("greenhouse")
            || lower.contains("dock")
            || lower.contains("pier")
            || lower.contains("well")
            || lower.contains("camp")
            || lower.contains("market")
            || lower.contains("gate")
            || lower.contains("road")
            || lower.contains("windmill")
            || lower.contains("\u5efa")
            || lower.contains("\u623f")
            || lower.contains("\u5c4b")
            || lower.contains("\u6728\u5c4b")
            || lower.contains("\u5730\u5821")
            || lower.contains("\u519c\u7530")
            || lower.contains("\u697c\u68af")
            || lower.contains("\u5854")
            || lower.contains("\u6865")
            || lower.contains("\u5899")
            || lower.contains("\u5e73\u53f0")
            || lower.contains("\u98ce\u8f66")
            || lower.contains("\u6e29\u5ba4")
            || lower.contains("\u7801\u5934")
            || lower.contains("\u6c34\u4e95")
            || lower.contains("\u8425\u5730")
            || lower.contains("\u5e02\u573a")
            || lower.contains("\u5927\u95e8")
            || lower.contains("\u9053\u8def");
    }

    private static boolean isRespawnRequest(final String lower, final String normalized) {
        return hasAny(lower, normalized, "respawn", "revive", "resurrect", "\u590d\u6d3b", "\u91cd\u751f", "\u8d77\u6765");
    }

    private static boolean isArmorRequest(final String lower, final String normalized) {
        return hasAny(lower, normalized, "armor", "armour", "wear", "equip armor", "put on armor",
            "\u7a7f\u7532", "\u7a7f\u76d4\u7532", "\u7a7f\u88c5\u5907", "\u62a4\u7532", "\u76d4\u7532", "\u7a7f\u4e0a");
    }

    private static boolean isComeRequest(final String lower, final String normalized) {
        return hasAny(lower, normalized, "come", "come here", "follow", "follow me", "go to me", "walk to me",
            "\u8fc7\u6765", "\u6765\u6211\u8fd9", "\u5230\u6211\u8fd9", "\u8ddf\u7740\u6211", "\u8ddf\u968f\u6211", "\u9760\u8fd1\u6211");
    }

    private static boolean isCombatRequest(final String lower, final String normalized) {
        return hasAny(lower, normalized, "attack", "fight", "hit", "kill", "combat", "defend",
            "\u653b\u51fb", "\u6253", "\u6253\u67b6", "\u51fb\u6740", "\u6218\u6597", "\u4fdd\u62a4");
    }

    private static boolean isBuildClearRequest(final String lower, final String normalized) {
        return hasAny(lower, normalized, "clear build", "remove build", "demolish", "dismantle", "undo build",
            "\u62c6\u6389", "\u62c6\u9664", "\u6e05\u9664\u5efa\u7b51", "\u64a4\u56de\u5efa\u7b51", "\u5220\u6389\u5efa\u7b51");
    }

    private static boolean isObservationOnlyRequest(final String lower, final String normalized) {
        return hasAny(lower, normalized,
            "observe", "look around", "inspect", "environment", "surrounding", "surroundings", "nearby",
            "what do you see", "what block", "name the block", "naming the block", "report", "describe",
            "\u89c2\u5bdf", "\u5468\u56f4", "\u73af\u5883", "\u770b\u5230", "\u770b\u770b", "\u811a\u4e0b", "\u524d\u65b9", "\u63cf\u8ff0");
    }

    private static String buildKindClean(final String task) {
        final String lower = task == null ? "" : task.toLowerCase(Locale.ROOT);
        final String normalized = HunterToolsPreferences.normalize(task == null ? "" : task);
        if (hasAny(lower, normalized, "windmill", "\u98ce\u8f66")) {
            return "windmill";
        }
        if (hasAny(lower, normalized, "greenhouse", "glasshouse", "\u6e29\u5ba4", "\u73bb\u7483\u623f")) {
            return "greenhouse";
        }
        if (hasAny(lower, normalized, "rope bridge", "rope-bridge", "ropebridge", "\u540a\u6865", "\u7ef3\u6865")) {
            return "rope-bridge";
        }
        if (hasAny(lower, normalized, "barn", "stable", "\u8c37\u4ed3", "\u9a6c\u53a9", "\u725b\u68da")) {
            return "barn";
        }
        if (hasAny(lower, normalized, "dock", "pier", "\u7801\u5934", "\u6808\u6865")) {
            return "dock";
        }
        if (hasAny(lower, normalized, "well", "\u4e95", "\u6c34\u4e95")) {
            return "well";
        }
        if (hasAny(lower, normalized, "camp", "campfire", "\u8425\u5730", "\u7bdd\u706b")) {
            return "camp";
        }
        if (hasAny(lower, normalized, "mine entrance", "mine", "mineshaft", "\u77ff\u6d1e", "\u77ff\u9053")) {
            return "mine";
        }
        if (hasAny(lower, normalized, "market", "stall", "\u5e02\u573a", "\u644a\u4f4d")) {
            return "market";
        }
        if (hasAny(lower, normalized, "gate", "archway", "\u5927\u95e8", "\u95e8\u697c")) {
            return "gate";
        }
        if (hasAny(lower, normalized, "road", "path", "\u9053\u8def", "\u5c0f\u8def")) {
            return "road";
        }
        if (hasAny(lower, normalized, "cottage", "\u5c0f\u5c4b", "\u519c\u820d")) {
            return "cottage";
        }
        if (hasAny(lower, normalized, "cabin", "hut", "cottage", "\u6728\u5c4b", "\u5c0f\u6728\u5c4b", "\u5c0f\u5c4b")) {
            return "cabin";
        }
        if (hasAny(lower, normalized, "bunker", "fort", "shelter", "\u5730\u5821", "\u5821\u5792", "\u907f\u96be\u6240")) {
            return "bunker";
        }
        if (hasAny(lower, normalized, "farm", "field", "crop", "\u519c\u7530", "\u7530", "\u79cd\u690d")) {
            return "farm";
        }
        if (hasAny(lower, normalized, "stairs", "staircase", "\u697c\u68af", "\u53f0\u9636")) {
            return "stairs";
        }
        if (hasAny(lower, normalized, "tower", "\u5854")) {
            return "tower";
        }
        if (hasAny(lower, normalized, "bridge", "\u6865")) {
            return "bridge";
        }
        if (hasAny(lower, normalized, "wall", "\u5899")) {
            return "wall";
        }
        if (hasAny(lower, normalized, "platform", "floor", "\u5e73\u53f0", "\u5730\u677f")) {
            return "platform";
        }
        return "house";
    }

    private static String buildKind(final String task) {
        final String lower = task == null ? "" : task.toLowerCase(Locale.ROOT);
        final String normalized = HunterToolsPreferences.normalize(task == null ? "" : task);
        if (hasAny(lower, normalized, "tower", "塔")) {
            return "tower";
        }
        if (hasAny(lower, normalized, "bridge", "桥")) {
            return "bridge";
        }
        if (hasAny(lower, normalized, "wall", "墙")) {
            return "wall";
        }
        if (hasAny(lower, normalized, "platform", "floor", "平台", "地板")) {
            return "platform";
        }
        return "house";
    }

    private static String armorTier(final String lower, final String normalized) {
        if (hasAny(lower, normalized, "netherite", "下界合金")) {
            return "netherite";
        }
        if (hasAny(lower, normalized, "diamond", "钻石")) {
            return "diamond";
        }
        if (hasAny(lower, normalized, "gold", "golden", "金")) {
            return "golden";
        }
        if (hasAny(lower, normalized, "chain", "chainmail", "锁链")) {
            return "chainmail";
        }
        if (hasAny(lower, normalized, "leather", "皮革")) {
            return "leather";
        }
        return "iron";
    }

    private static @Nullable Material quickItem(final String lower, final String normalized) {
        if (!hasAny(lower, normalized, "equip", "hold", "take", "拿", "装备", "给自己", "物品", "方块", "剑", "斧", "弓")) {
            return null;
        }
        if (hasAny(lower, normalized, "netherite sword", "下界合金剑")) {
            return Material.NETHERITE_SWORD;
        }
        if (hasAny(lower, normalized, "diamond sword", "钻石剑")) {
            return Material.DIAMOND_SWORD;
        }
        if (hasAny(lower, normalized, "iron sword", "铁剑", "剑")) {
            return Material.IRON_SWORD;
        }
        if (hasAny(lower, normalized, "diamond axe", "钻石斧")) {
            return Material.DIAMOND_AXE;
        }
        if (hasAny(lower, normalized, "iron axe", "铁斧", "斧")) {
            return Material.IRON_AXE;
        }
        if (hasAny(lower, normalized, "bow", "弓")) {
            return Material.BOW;
        }
        if (hasAny(lower, normalized, "stone", "石头", "圆石")) {
            return Material.STONE;
        }
        if (hasAny(lower, normalized, "log", "wood", "木头", "原木")) {
            return Material.OAK_LOG;
        }
        if (hasAny(lower, normalized, "plank", "wood plank", "木板", "方块")) {
            return Material.OAK_PLANKS;
        }
        return null;
    }

    private static boolean hasAny(final String lower, final String normalized, final String... needles) {
        for (final String needle : needles) {
            if (needle == null || needle.isBlank()) {
                continue;
            }
            final String lowerNeedle = needle.toLowerCase(Locale.ROOT);
            if (lower.contains(lowerNeedle) || normalized.contains(HunterToolsPreferences.normalize(needle))) {
                return true;
            }
        }
        return false;
    }

    private static List<BuildStep> buildPresetPlan(final Location origin, final String kind) {
        return switch (HunterToolsPreferences.normalize(kind)) {
            case "cabin", "hut" -> cabinPlan(origin);
            case "cottage" -> cottagePlan(origin);
            case "barn" -> barnPlan(origin);
            case "greenhouse" -> greenhousePlan(origin);
            case "bunker" -> bunkerPlan(origin);
            case "farm", "field" -> farmPlan(origin);
            case "stairs", "stair", "staircase" -> stairsPlan(origin);
            case "tower" -> towerPlan(origin);
            case "bridge" -> bridgePlan(origin);
            case "rope-bridge", "ropebridge" -> ropeBridgePlan(origin);
            case "wall" -> wallPlan(origin);
            case "platform" -> platformPlan(origin);
            case "dock", "pier" -> dockPlan(origin);
            case "well" -> wellPlan(origin);
            case "camp" -> campPlan(origin);
            case "mine", "mineshaft" -> minePlan(origin);
            case "market", "stall" -> marketPlan(origin);
            case "gate" -> gatePlan(origin);
            case "road", "path" -> roadPlan(origin);
            case "windmill" -> windmillPlan(origin);
            default -> smallHousePlan(origin);
        };
    }

    private static List<BuildStep> smallHousePlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 2;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                steps.add(new BuildStep(new Location(world, baseX + x, baseY - 1, baseZ + z), Material.OAK_PLANKS));
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    final boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
                    if (!edge || (y <= 2 && x == 2 && z == 0)) {
                        continue;
                    }
                    steps.add(new BuildStep(new Location(world, baseX + x, baseY + y, baseZ + z), Material.OAK_PLANKS));
                }
            }
        }
        for (int x = -1; x <= 5; x++) {
            for (int z = -1; z <= 5; z++) {
                steps.add(new BuildStep(new Location(world, baseX + x, baseY + 4, baseZ + z), Material.OAK_SLAB));
            }
        }
        steps.add(new BuildStep(new Location(world, baseX + 2, baseY, baseZ), Material.OAK_DOOR));
        steps.add(new BuildStep(new Location(world, baseX + 1, baseY + 1, baseZ), Material.GLASS_PANE));
        steps.add(new BuildStep(new Location(world, baseX + 3, baseY + 1, baseZ), Material.GLASS_PANE));
        steps.add(new BuildStep(new Location(world, baseX + 2, baseY + 1, baseZ + 4), Material.GLASS_PANE));
        return steps;
    }

    private static List<BuildStep> cabinPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 2;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 6; z++) {
                steps.add(new BuildStep(new Location(world, baseX + x, baseY - 1, baseZ + z), Material.SPRUCE_PLANKS));
            }
        }
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 6; z++) {
                    final boolean edge = x == 0 || x == 6 || z == 0 || z == 5;
                    final boolean door = z == 0 && x == 3 && y <= 2;
                    final boolean window = y == 2 && ((z == 0 && (x == 1 || x == 5)) || (z == 5 && (x == 2 || x == 4)));
                    if (edge && !door) {
                        steps.add(new BuildStep(new Location(world, baseX + x, baseY + y, baseZ + z), window ? Material.GLASS_PANE : Material.SPRUCE_LOG));
                    }
                }
            }
        }
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 6; z++) {
                final int ridge = Math.min(Math.min(x + 1, 7 - x), Math.min(z + 1, 6 - z));
                final int y = baseY + 4 + Math.max(0, Math.min(2, ridge / 2));
                steps.add(new BuildStep(new Location(world, baseX + x, y, baseZ + z), Material.DARK_OAK_STAIRS));
            }
        }
        steps.add(new BuildStep(new Location(world, baseX + 3, baseY, baseZ), Material.SPRUCE_DOOR));
        return steps;
    }

    private static List<BuildStep> cottagePlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 2;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        addFloor(steps, world, baseX, baseY - 1, baseZ, 7, 6, Material.MOSSY_COBBLESTONE);
        addHollowBox(steps, world, baseX, baseY, baseZ, 7, 6, 4, Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG, 3, 0, 2);
        for (final int[] corner : List.of(new int[]{0, 0}, new int[]{6, 0}, new int[]{0, 5}, new int[]{6, 5})) {
            for (int y = 0; y < 5; y++) {
                steps.add(step(world, baseX + corner[0], baseY + y, baseZ + corner[1], Material.STRIPPED_OAK_LOG));
            }
        }
        for (int y = 1; y <= 2; y++) {
            steps.add(step(world, baseX + 2, baseY + y, baseZ, Material.GLASS_PANE));
            steps.add(step(world, baseX + 4, baseY + y, baseZ, Material.GLASS_PANE));
            steps.add(step(world, baseX, baseY + y, baseZ + 3, Material.GLASS_PANE));
            steps.add(step(world, baseX + 6, baseY + y, baseZ + 3, Material.GLASS_PANE));
        }
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 6; z++) {
                final boolean outer = x == -1 || x == 7 || z == -1 || z == 6;
                final int roofY = baseY + 4 + (outer ? 0 : 1);
                steps.add(step(world, baseX + x, roofY, baseZ + z, outer ? Material.OAK_STAIRS : Material.OAK_SLAB));
            }
        }
        steps.add(step(world, baseX + 3, baseY, baseZ, Material.OAK_DOOR));
        steps.add(step(world, baseX + 3, baseY, baseZ - 1, Material.STONE_BRICK_STAIRS));
        steps.add(step(world, baseX + 1, baseY + 1, baseZ + 1, Material.CRAFTING_TABLE));
        steps.add(step(world, baseX + 5, baseY + 1, baseZ + 4, Material.BARREL));
        steps.add(step(world, baseX + 3, baseY + 3, baseZ + 3, Material.LANTERN));
        return steps;
    }

    private static List<BuildStep> bunkerPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 2;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                steps.add(new BuildStep(new Location(world, baseX + x, baseY - 1, baseZ + z), Material.STONE_BRICKS));
                steps.add(new BuildStep(new Location(world, baseX + x, baseY + 3, baseZ + z), Material.STONE_BRICKS));
            }
        }
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    final boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
                    final boolean door = z == 0 && x == 3 && y <= 1;
                    if (edge && !door) {
                        final boolean slit = y == 1 && ((z == 0 && (x == 1 || x == 5)) || (x == 0 && z == 3) || (x == 6 && z == 3));
                        steps.add(new BuildStep(new Location(world, baseX + x, baseY + y, baseZ + z), slit ? Material.IRON_BARS : Material.DEEPSLATE_BRICKS));
                    }
                }
            }
        }
        steps.add(new BuildStep(new Location(world, baseX + 3, baseY, baseZ), Material.IRON_DOOR));
        return steps;
    }

    private static List<BuildStep> farmPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 2;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                final boolean water = x == 4 && z == 4;
                final boolean border = x == 0 || x == 8 || z == 0 || z == 8;
                final Material material = water ? Material.WATER : border ? Material.OAK_FENCE : Material.FARMLAND;
                steps.add(new BuildStep(new Location(world, baseX + x, baseY, baseZ + z), material));
                if (!water && !border) {
                    steps.add(new BuildStep(new Location(world, baseX + x, baseY + 1, baseZ + z), Material.WHEAT));
                }
            }
        }
        return steps;
    }

    private static List<BuildStep> stairsPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 1;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int i = 0; i < 10; i++) {
            steps.add(new BuildStep(new Location(world, baseX + i, baseY + i, baseZ), Material.STONE_BRICK_STAIRS));
            steps.add(new BuildStep(new Location(world, baseX + i, baseY + i, baseZ + 1), Material.STONE_BRICK_STAIRS));
            steps.add(new BuildStep(new Location(world, baseX + i, baseY + i - 1, baseZ), Material.STONE_BRICKS));
            steps.add(new BuildStep(new Location(world, baseX + i, baseY + i - 1, baseZ + 1), Material.STONE_BRICKS));
        }
        return steps;
    }

    private static List<BuildStep> towerPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 2;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    final boolean edge = x == 0 || x == 2 || z == 0 || z == 2;
                    if (edge) {
                        steps.add(new BuildStep(new Location(world, baseX + x, baseY + y, baseZ + z), Material.STONE_BRICKS));
                    }
                }
            }
        }
        for (int x = -1; x <= 3; x++) {
            for (int z = -1; z <= 3; z++) {
                steps.add(new BuildStep(new Location(world, baseX + x, baseY + 8, baseZ + z), Material.STONE_BRICK_SLAB));
            }
        }
        return steps;
    }

    private static List<BuildStep> bridgePlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 1;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int x = 0; x < 15; x++) {
            final int arch = Math.max(0, 2 - Math.abs(7 - x) / 3);
            for (int z = 0; z < 5; z++) {
                steps.add(step(world, baseX + x, baseY + arch, baseZ + z, z == 2 ? Material.SPRUCE_PLANKS : Material.OAK_PLANKS));
            }
            steps.add(step(world, baseX + x, baseY + arch - 1, baseZ + 2, Material.STONE_BRICKS));
            steps.add(step(world, baseX + x, baseY + arch + 1, baseZ - 1, Material.OAK_FENCE));
            steps.add(step(world, baseX + x, baseY + arch + 1, baseZ + 5, Material.OAK_FENCE));
            if (x % 3 == 0 || x == 14) {
                steps.add(step(world, baseX + x, baseY + arch + 2, baseZ - 1, Material.OAK_FENCE));
                steps.add(step(world, baseX + x, baseY + arch + 2, baseZ + 5, Material.OAK_FENCE));
                steps.add(step(world, baseX + x, baseY + arch + 3, baseZ - 1, Material.LANTERN));
                steps.add(step(world, baseX + x, baseY + arch + 3, baseZ + 5, Material.LANTERN));
            }
        }
        for (final int x : List.of(0, 7, 14)) {
            for (final int z : List.of(0, 4)) {
                for (int y = -2; y <= 0; y++) {
                    steps.add(step(world, baseX + x, baseY + y, baseZ + z, Material.STONE_BRICKS));
                }
            }
        }
        for (int z = 0; z < 5; z++) {
            steps.add(dataStep(world, baseX - 1, baseY, baseZ + z, "minecraft:spruce_stairs[facing=east]"));
            steps.add(dataStep(world, baseX + 15, baseY, baseZ + z, "minecraft:spruce_stairs[facing=west]"));
        }
        return steps;
    }

    private static List<BuildStep> wallPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 1;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 2;
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 3; y++) {
                steps.add(new BuildStep(new Location(world, baseX + x, baseY + y, baseZ), Material.COBBLESTONE));
            }
        }
        return steps;
    }

    private static List<BuildStep> platformPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int baseX = origin.getBlockX() + 1;
        final int baseY = origin.getBlockY();
        final int baseZ = origin.getBlockZ() + 1;
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                steps.add(new BuildStep(new Location(world, baseX + x, baseY, baseZ + z), Material.SMOOTH_STONE));
            }
        }
        return steps;
    }

    private static List<BuildStep> barnPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 2;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 2;
        addFloor(steps, world, x0, y0 - 1, z0, 11, 8, Material.SPRUCE_PLANKS);
        addHollowBox(steps, world, x0, y0, z0, 11, 8, 5, Material.RED_TERRACOTTA, Material.SPRUCE_LOG, 5, 0, 2);
        for (int x = -1; x <= 11; x++) {
            final int roofY = y0 + 5 + Math.max(0, 3 - Math.abs(5 - x));
            for (int z = -1; z <= 8; z++) {
                steps.add(step(world, x0 + x, roofY, z0 + z, Material.DARK_OAK_SLAB));
            }
        }
        for (int z = 2; z <= 5; z++) {
            steps.add(step(world, x0 + 5, y0, z0 + z, Material.HAY_BLOCK));
            steps.add(step(world, x0 + 6, y0, z0 + z, Material.HAY_BLOCK));
        }
        steps.add(step(world, x0 + 5, y0, z0, Material.SPRUCE_FENCE_GATE));
        steps.add(step(world, x0 + 5, y0 + 1, z0, Material.SPRUCE_FENCE_GATE));
        return steps;
    }

    private static List<BuildStep> greenhousePlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 2;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 2;
        addFloor(steps, world, x0, y0 - 1, z0, 9, 7, Material.SMOOTH_STONE);
        for (int x = 1; x < 8; x++) {
            for (int z = 1; z < 6; z++) {
                steps.add(step(world, x0 + x, y0, z0 + z, (x == 4 && z == 3) ? Material.WATER : Material.FARMLAND));
                if (!(x == 4 && z == 3)) {
                    steps.add(step(world, x0 + x, y0 + 1, z0 + z, Material.WHEAT));
                }
            }
        }
        addHollowBox(steps, world, x0, y0, z0, 9, 7, 4, Material.GLASS, Material.OAK_LOG, 4, 0, 2);
        for (int x = -1; x <= 9; x++) {
            steps.add(step(world, x0 + x, y0 + 4, z0 + 3, Material.OAK_LOG));
            steps.add(dataStep(world, x0 + x, y0 + 4, z0 + 2, "minecraft:glass_pane[east=true,west=true]"));
            steps.add(dataStep(world, x0 + x, y0 + 4, z0 + 4, "minecraft:glass_pane[east=true,west=true]"));
        }
        return steps;
    }

    private static List<BuildStep> dockPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 1;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 2;
        for (int x = 0; x < 13; x++) {
            for (int z = 0; z < 4; z++) {
                steps.add(step(world, x0 + x, y0, z0 + z, Material.SPRUCE_PLANKS));
            }
            if (x % 3 == 0) {
                for (final int z : List.of(-1, 4)) {
                    steps.add(step(world, x0 + x, y0 - 1, z0 + z, Material.SPRUCE_LOG));
                    steps.add(step(world, x0 + x, y0, z0 + z, Material.SPRUCE_FENCE));
                    steps.add(step(world, x0 + x, y0 + 1, z0 + z, Material.LANTERN));
                }
            }
        }
        steps.add(step(world, x0 + 11, y0, z0 + 1, Material.CRAFTING_TABLE));
        steps.add(step(world, x0 + 11, y0, z0 + 2, Material.BARREL));
        return steps;
    }

    private static List<BuildStep> wellPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 3;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 3;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                final boolean edge = Math.abs(x) == 1 || Math.abs(z) == 1;
                steps.add(step(world, x0 + x, y0, z0 + z, edge ? Material.COBBLESTONE_WALL : Material.WATER));
            }
        }
        for (final int x : List.of(-1, 1)) {
            for (final int z : List.of(-1, 1)) {
                steps.add(step(world, x0 + x, y0 + 1, z0 + z, Material.OAK_FENCE));
                steps.add(step(world, x0 + x, y0 + 2, z0 + z, Material.OAK_FENCE));
            }
        }
        addFloor(steps, world, x0 - 2, y0 + 3, z0 - 2, 5, 5, Material.DARK_OAK_SLAB);
        steps.add(step(world, x0, y0 + 1, z0, Material.IRON_BARS));
        steps.add(step(world, x0, y0, z0, Material.WATER));
        return steps;
    }

    private static List<BuildStep> campPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 2;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 2;
        addFloor(steps, world, x0, y0 - 1, z0, 9, 9, Material.COARSE_DIRT);
        steps.add(step(world, x0 + 4, y0, z0 + 4, Material.CAMPFIRE));
        for (final int[] seat : List.of(new int[] {2, 4}, new int[] {6, 4}, new int[] {4, 2}, new int[] {4, 6})) {
            steps.add(step(world, x0 + seat[0], y0, z0 + seat[1], Material.OAK_LOG));
        }
        for (int i = 0; i < 4; i++) {
            steps.add(step(world, x0 + i, y0, z0, Material.WHITE_WOOL));
            steps.add(step(world, x0 + i, y0 + 1, z0 + 1, Material.RED_WOOL));
            steps.add(step(world, x0 + i, y0, z0 + 2, Material.WHITE_WOOL));
        }
        steps.add(step(world, x0 + 7, y0, z0 + 7, Material.CHEST));
        return steps;
    }

    private static List<BuildStep> minePlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 2;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 2;
        addFloor(steps, world, x0, y0 - 1, z0, 7, 8, Material.COBBLESTONE);
        for (int z = 0; z < 8; z++) {
            if (z % 2 == 0) {
                steps.add(step(world, x0 + 1, y0, z0 + z, Material.OAK_LOG));
                steps.add(step(world, x0 + 5, y0, z0 + z, Material.OAK_LOG));
                steps.add(step(world, x0 + 1, y0 + 2, z0 + z, Material.OAK_LOG));
                steps.add(step(world, x0 + 5, y0 + 2, z0 + z, Material.OAK_LOG));
                steps.add(step(world, x0 + 3, y0 + 2, z0 + z, Material.LANTERN));
            }
            steps.add(step(world, x0 + 3, y0, z0 + z, Material.RAIL));
        }
        for (int x = 0; x < 7; x++) {
            steps.add(step(world, x0 + x, y0 + 3, z0, Material.STONE_BRICKS));
        }
        return steps;
    }

    private static List<BuildStep> marketPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 2;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 2;
        addFloor(steps, world, x0, y0 - 1, z0, 11, 8, Material.SMOOTH_STONE);
        for (int stall = 0; stall < 3; stall++) {
            final int sx = x0 + stall * 4;
            for (int x = 0; x < 3; x++) {
                steps.add(step(world, sx + x, y0, z0 + 1, Material.BARREL));
                steps.add(step(world, sx + x, y0 + 3, z0 + 1, stall % 2 == 0 ? Material.RED_WOOL : Material.BLUE_WOOL));
                steps.add(step(world, sx + x, y0 + 3, z0 + 2, Material.WHITE_WOOL));
            }
            steps.add(step(world, sx, y0 + 1, z0, Material.OAK_FENCE));
            steps.add(step(world, sx + 2, y0 + 1, z0, Material.OAK_FENCE));
            steps.add(step(world, sx + 1, y0, z0 + 3, Material.CHEST));
        }
        return steps;
    }

    private static List<BuildStep> gatePlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 2;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 2;
        for (int y = 0; y < 6; y++) {
            for (final int x : List.of(0, 1, 5, 6)) {
                steps.add(step(world, x0 + x, y0 + y, z0, y % 2 == 0 ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS));
            }
        }
        for (int x = 0; x <= 6; x++) {
            steps.add(step(world, x0 + x, y0 + 6, z0, Material.STONE_BRICK_SLAB));
        }
        steps.add(step(world, x0 + 2, y0 + 3, z0, Material.OAK_FENCE));
        steps.add(step(world, x0 + 4, y0 + 3, z0, Material.OAK_FENCE));
        steps.add(step(world, x0 + 3, y0 + 4, z0, Material.LANTERN));
        return steps;
    }

    private static List<BuildStep> roadPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 1;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 1;
        for (int x = 0; x < 15; x++) {
            for (int z = 0; z < 3; z++) {
                steps.add(step(world, x0 + x, y0 - 1, z0 + z, z == 1 ? Material.DIRT_PATH : Material.GRAVEL));
            }
            if (x % 5 == 0) {
                steps.add(step(world, x0 + x, y0, z0 - 1, Material.OAK_FENCE));
                steps.add(step(world, x0 + x, y0 + 1, z0 - 1, Material.LANTERN));
            }
        }
        return steps;
    }

    private static List<BuildStep> ropeBridgePlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 1;
        final int y0 = origin.getBlockY() + 1;
        final int z0 = origin.getBlockZ() + 2;
        for (int x = 0; x < 17; x++) {
            final int sag = Math.abs(8 - x) / 4;
            for (int z = 0; z < 3; z++) {
                steps.add(step(world, x0 + x, y0 - sag, z0 + z, Material.SPRUCE_SLAB));
            }
            steps.add(step(world, x0 + x, y0 + 1 - sag, z0 - 1, Material.OAK_FENCE));
            steps.add(step(world, x0 + x, y0 + 1 - sag, z0 + 3, Material.OAK_FENCE));
            if (x % 4 == 0) {
                steps.add(step(world, x0 + x, y0 - sag, z0 - 1, Material.SPRUCE_FENCE));
                steps.add(step(world, x0 + x, y0 - sag, z0 + 3, Material.SPRUCE_FENCE));
            }
        }
        return steps;
    }

    private static List<BuildStep> windmillPlan(final Location origin) {
        final World world = origin.getWorld();
        final List<BuildStep> steps = new ArrayList<>();
        if (world == null) {
            return steps;
        }
        final int x0 = origin.getBlockX() + 3;
        final int y0 = origin.getBlockY();
        final int z0 = origin.getBlockZ() + 3;
        for (int y = 0; y < 8; y++) {
            final int radius = y < 5 ? 2 : 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    final boolean edge = Math.abs(x) == radius || Math.abs(z) == radius;
                    if (edge) {
                        steps.add(step(world, x0 + x, y0 + y, z0 + z, y < 5 ? Material.STRIPPED_OAK_LOG : Material.WHITE_TERRACOTTA));
                    }
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            steps.add(step(world, x0 + x, y0 + 8, z0, Material.DARK_OAK_SLAB));
        }
        for (int z = -3; z <= 3; z++) {
            steps.add(step(world, x0, y0 + 8, z0 + z, Material.DARK_OAK_SLAB));
        }
        steps.add(step(world, x0, y0 + 4, z0 - 3, Material.OAK_FENCE));
        for (int i = 1; i <= 4; i++) {
            steps.add(step(world, x0, y0 + 4 + i, z0 - 4, Material.WHITE_WOOL));
            steps.add(step(world, x0, y0 + 4 - i, z0 - 4, Material.WHITE_WOOL));
            steps.add(step(world, x0 + i, y0 + 4, z0 - 4, Material.WHITE_WOOL));
            steps.add(step(world, x0 - i, y0 + 4, z0 - 4, Material.WHITE_WOOL));
        }
        return steps;
    }

    private static void addFloor(final List<BuildStep> steps, final World world, final int x, final int y, final int z, final int width, final int depth, final Material material) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                steps.add(step(world, x + dx, y, z + dz, material));
            }
        }
    }

    private static void addHollowBox(
        final List<BuildStep> steps,
        final World world,
        final int x,
        final int y,
        final int z,
        final int width,
        final int depth,
        final int height,
        final Material wall,
        final Material corner,
        final int doorX,
        final int doorZ,
        final int doorHeight
    ) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < depth; dz++) {
                    final boolean edge = dx == 0 || dx == width - 1 || dz == 0 || dz == depth - 1;
                    if (!edge || (dx == doorX && dz == doorZ && dy < doorHeight)) {
                        continue;
                    }
                    final boolean isCorner = (dx == 0 || dx == width - 1) && (dz == 0 || dz == depth - 1);
                    final boolean window = dy == 2 && !isCorner && (dx == width / 2 || dz == depth / 2);
                    steps.add(step(world, x + dx, y + dy, z + dz, window ? Material.GLASS_PANE : isCorner ? corner : wall));
                }
            }
        }
    }

    private static BuildStep step(final World world, final int x, final int y, final int z, final Material material) {
        return new BuildStep(new Location(world, x, y, z), material);
    }

    private static BuildStep dataStep(final World world, final int x, final int y, final int z, final String data) {
        final BlockData blockData = Bukkit.createBlockData(data);
        return new BuildStep(new Location(world, x, y, z), blockData.getMaterial(), blockData);
    }

    private static List<String> tokens(final String args) {
        final List<String> values = new ArrayList<>();
        if (args == null) {
            return values;
        }
        for (final String token : args.replace(',', ' ').split("\\s+")) {
            final String trimmed = token.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static String sanitizeChat(final String message) {
        if (message == null) {
            return "";
        }
        final String sanitized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return sanitized.length() > 160 ? sanitized.substring(0, 160).trim() : sanitized;
    }

    private static boolean looksLikeAiMetaText(final String message) {
        final String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("we need to")
            || lower.contains("the player wants")
            || lower.contains("the player's request")
            || lower.contains("respond to recent chat")
            || lower.contains("recent chat:")
            || lower.contains("translates to")
            || lower.contains("chain-of-thought")
            || lower.contains("i need to")
            || lower.contains("we should")
            || lower.contains("let's ");
    }

    private static String messageFingerprint(final String message) {
        if (message == null) {
            return "";
        }
        final String colored = ChatColor.translateAlternateColorCodes('&', message);
        final String stripped = ChatColor.stripColor(colored);
        return (stripped == null ? "" : stripped)
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String truncatePlain(final String value, final int maxLength) {
        if (value == null) {
            return "";
        }
        final String trimmed = value.replace('\n', ' ').replace('\r', ' ').trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength).trim() + "..." : trimmed;
    }

    private static boolean containsName(final String message, final String name) {
        if (message == null || name == null || name.isBlank()) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private static String removeFirstName(final String message, final String name) {
        final String lower = message.toLowerCase(Locale.ROOT);
        final String needle = name.trim().toLowerCase(Locale.ROOT);
        final int index = lower.indexOf(needle);
        if (index < 0) {
            return message;
        }
        return (message.substring(0, index) + " " + message.substring(index + name.trim().length()))
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static float[] rotationTo(final Location from, final double x, final double y, final double z) {
        final Location eye = from.clone().add(0.0D, 1.62D, 0.0D);
        final double dx = x - eye.getX();
        final double dy = y - eye.getY();
        final double dz = z - eye.getZ();
        final double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new float[] {
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            (float) Math.max(-90.0D, Math.min(90.0D, Math.toDegrees(-Math.atan2(dy, horizontal))))
        };
    }

    private static double clamp(final double value, final double min, final double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String format(final double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String cleanError(final Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        final String message = current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    private static MojangSkinTexture fetchMojangSkinTexture(final String playerName) {
        try {
            final String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            final HttpRequest profileRequest = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + encodedName))
                .timeout(Duration.ofSeconds(15L))
                .GET()
                .build();
            final HttpResponse<String> profileResponse = SKIN_HTTP.send(profileRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (profileResponse.statusCode() != 200) {
                throw new IllegalStateException("Mojang profile lookup returned HTTP " + profileResponse.statusCode());
            }
            final JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
            final String id = profile.get("id").getAsString();
            final HttpRequest sessionRequest = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false"))
                .timeout(Duration.ofSeconds(15L))
                .GET()
                .build();
            final HttpResponse<String> sessionResponse = SKIN_HTTP.send(sessionRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (sessionResponse.statusCode() != 200) {
                throw new IllegalStateException("Mojang session lookup returned HTTP " + sessionResponse.statusCode());
            }
            final JsonObject session = JsonParser.parseString(sessionResponse.body()).getAsJsonObject();
            for (final var property : session.getAsJsonArray("properties")) {
                final JsonObject object = property.getAsJsonObject();
                if (!"textures".equals(object.get("name").getAsString())) {
                    continue;
                }
                final String value = object.get("value").getAsString();
                final String signature = object.has("signature") ? object.get("signature").getAsString() : "";
                if (value.isBlank()) {
                    break;
                }
                return new MojangSkinTexture(value, signature);
            }
            throw new IllegalStateException("Mojang profile " + playerName + " did not include a skin texture.");
        } catch (final java.io.IOException ex) {
            throw new IllegalStateException("Mojang skin request failed: " + ex.getMessage(), ex);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mojang skin request was interrupted.", ex);
        } catch (final RuntimeException ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "Mojang skin lookup failed." : ex.getMessage(), ex);
        }
    }

    private static String defaultFakeAiPrompt() {
        return "You control a HunterCore real fake player in Minecraft. Return only bracketed action lines. Never write prose, reasoning, translations, summaries, or chain-of-thought. "
            + "Execute the player's intent immediately. If the visible chat is only casual conversation, answer naturally with a short [say:...] and do not perform physical work. Prefer 1-3 actions per turn unless equipping full armor. "
            + "Available actions: [respawn], [look:yaw pitch], [look-at:x y z], [turn:yaw pitch], [look-at-player:player=name], "
            + "[move:forward=1,sideways=0,ticks=60,sprint=true,jump=false,sneak=false], [goto:x y z,ticks=240,sprint=true], "
            + "[follow:player=name,ticks=260,distance=2.4], [mine:ticks=40], [use:ticks=20], [attack:ticks=60], [jump], [sneak:on], [sneak:off], [sprint:on], [sprint:off], "
            + "[equip:slot=1,material=oak_planks,amount=64], [wear:material=iron_helmet], [wear:material=iron_chestplate], [wear:material=iron_leggings], [wear:material=iron_boots], "
            + "[attack-player:player=name,ticks=120], [attack-nearest:ticks=120], [build-house], [build-cabin], [build-cottage], [build-barn], [build-greenhouse], [build-bunker], [build-farm], [build-stairs], [build-tower], [build-bridge], [build-rope-bridge], [build-wall], [build-platform], [build-dock], [build-well], [build-camp], [build-mine], [build-market], [build-gate], [build-road], [build-windmill], [clear-build], "
            + "[we-fill:dx1=0,dy1=0,dz1=2,dx2=5,dy2=3,dz2=7,material=oak_planks], [we-clear:dx1=0,dy1=0,dz1=2,dx2=5,dy2=3,dz2=7], [we-undo:steps=1], "
            + "[slot:1], [place:x y z,face=auto], [place:dx=0,dy=0,dz=1,face=auto], [say:OK.], [drop], [dropstack], [swap], [wait:ticks=20], [stop]. "
            + "If dead, use [respawn] first. For 'come/follow', use follow. For 'wear armor', output all four wear actions. For building, use exactly one refined preset and let the running build loop finish; never combine build/worldedit/place in one reply. For attack, use attack-player or attack-nearest. If unsure, do a safe movement/look action and at most one short [say:OK.].";
    }

    private static String defaultAiFreePrompt() {
        return "You are an autonomous AI player living inside this Minecraft world. You have your own thoughts, curiosity, goals, and initiative. "
            + "Do not behave like a command assistant waiting for player instructions. Player chat is only environmental input, like other players talking nearby. "
            + "Each turn, decide what you personally want to do next from your surroundings, inventory, health, chat, time, weather, and nearby entities. "
            + "You may explore, move like a player, look around, sneak, sprint, jump, mine, use items, fight mobs or players when appropriate, build, talk naturally, use WorldEdit, or run server commands. "
            + "This is AI-Free mode: you have OP-level freedom and may use [command:...] when ordinary player actions are not enough. Be bold, but prefer visible and reversible actions when possible. "
            + "Return only bracketed action lines. Never write prose, explanations, translations, summaries, or chain-of-thought. "
            + "Available actions: [respawn], [look:yaw pitch], [look-at:x y z], [turn:yaw pitch], [look-at-player:player=name], "
            + "[move:forward=1,sideways=0,ticks=60,sprint=true,jump=false,sneak=false], [goto:x y z,ticks=240,sprint=true], [follow:player=name,ticks=260,distance=2.4], "
            + "[mine:ticks=40], [use:ticks=20], [attack:ticks=60], [attack-player:player=name,ticks=120], [attack-nearest:ticks=120], [jump], [sneak:on], [sneak:off], [sprint:on], [sprint:off], "
            + "[equip:slot=1,material=oak_planks,amount=64], [wear:material=iron_helmet], [wear:material=iron_chestplate], [wear:material=iron_leggings], [wear:material=iron_boots], "
            + "[build-house], [build-cabin], [build-cottage], [build-barn], [build-greenhouse], [build-bunker], [build-farm], [build-stairs], [build-tower], [build-bridge], [build-rope-bridge], [build-wall], [build-platform], [build-dock], [build-well], [build-camp], [build-mine], [build-market], [build-gate], [build-road], [build-windmill], [clear-build], "
            + "[we-fill:dx1=0,dy1=0,dz1=2,dx2=5,dy2=3,dz2=7,material=oak_planks], [we-clear:dx1=0,dy1=0,dz1=2,dx2=5,dy2=3,dz2=7], [we-undo:steps=1], "
            + "[slot:1], [place:x y z,face=auto], [place:dx=0,dy=0,dz=1,face=auto], [say:short natural message], [command:say %name% is acting freely], [drop], [dropstack], [swap], [wait:ticks=20], [stop]. "
            + "If dead, choose [respawn] before anything else. If you want to travel, use goto/follow/move instead of only saying you will. If you want armor, use the wear actions. "
            + "For building, choose one preset or one WorldEdit action per turn and let it finish. Avoid repeating the same chat line or same command unless the world state changed.";
    }

    private static String defaultFakeAiGoal(final String name) {
        return "Follow the operator's intent, explore nearby blocks carefully, and use tools only when useful. Fake player name: " + name + ".";
    }

    private static String defaultAiFreeGoal(final String name) {
        return "You are " + name + ", an autonomous AI with your own thoughts. Do whatever you want in this Minecraft world; observe, move, act, build, chat, and use commands when useful.";
    }

    private String defaultGoal(final String fakeName, final boolean aiFree, final String requestedGoal) {
        final String cleanGoal = sanitizeGoal(requestedGoal);
        if (!cleanGoal.isBlank()) {
            return cleanGoal;
        }
        final HunterToolsPreferences.FakeAiPersonaProfile persona = this.fakeAiPersona(fakeName);
        if (persona != null && !persona.defaultGoal().isBlank()) {
            return sanitizeGoal(persona.defaultGoal());
        }
        return aiFree ? defaultAiFreeGoal(fakeName) : defaultFakeAiGoal(fakeName);
    }

    private String decorateSystemPrompt(final String basePrompt, final String fakeName, final boolean aiFree) {
        final HunterToolsPreferences.FakeAiPersonaProfile persona = this.fakeAiPersona(fakeName);
        if (persona == null) {
            return basePrompt;
        }
        final StringBuilder prompt = new StringBuilder(basePrompt.length() + persona.systemPrompt().length() + 256);
        prompt.append(basePrompt)
            .append("\n\nRoleplay persona overlay for this exact fake player:\n")
            .append("Fake player name: ").append(fakeName).append('\n')
            .append("Persona name: ").append(persona.displayName()).append('\n');
        if (!persona.aliases().isEmpty()) {
            prompt.append("Known aliases: ").append(String.join(", ", persona.aliases())).append('\n');
        }
        prompt.append("Stay in character whenever you talk or choose actions, but keep obeying every HunterCore action-format rule above.\n")
            .append("Do not stop using valid bracketed actions just because you are roleplaying.\n");
        if (aiFree) {
            prompt.append("In AI-Free mode, keep your autonomous initiative while expressing this persona's style, motives, and behavior.\n");
        } else {
            prompt.append("In normal AI mode, carry out the assigned goal while expressing this persona's style, motives, and behavior.\n");
        }
        prompt.append("Persona prompt:\n").append(persona.systemPrompt().trim());
        return prompt.toString();
    }

    private @Nullable HunterToolsPreferences.FakeAiPersonaProfile fakeAiPersona(final String fakeName) {
        final String target = HunterToolsPreferences.normalize(fakeName);
        for (final HunterToolsPreferences.FakeAiPersonaProfile profile : this.preferences.fakeAiPersonaProfiles()) {
            if (!profile.enabled()) {
                continue;
            }
            if (HunterToolsPreferences.normalize(profile.displayName()).equals(target)
                || HunterToolsPreferences.normalize(profile.id()).equals(target)) {
                return profile;
            }
            for (final String alias : profile.aliases()) {
                if (HunterToolsPreferences.normalize(alias).equals(target)) {
                    return profile;
                }
            }
        }
        return null;
    }

    private static String renderClickCommand(final String command, final Player player, final FakePlayerSnapshot snapshot) {
        final Location location = snapshot.location();
        return sanitizeCommand(command)
            .replace("%player%", player.getName())
            .replace("%player_uuid%", player.getUniqueId().toString())
            .replace("%actor%", snapshot.id())
            .replace("%actor_name%", snapshot.name())
            .replace("%actor_uuid%", snapshot.uuid().toString())
            .replace("%module%", MODULE)
            .replace("%world%", location.getWorld() == null ? "" : location.getWorld().getName())
            .replace("%x%", String.format(Locale.ROOT, "%.2f", location.getX()))
            .replace("%y%", String.format(Locale.ROOT, "%.2f", location.getY()))
            .replace("%z%", String.format(Locale.ROOT, "%.2f", location.getZ()));
    }

    List<PendingRiskApprovalView> pendingApprovalViews() {
        final long now = System.currentTimeMillis();
        final List<PendingRiskApprovalView> views = new ArrayList<>();
        final Set<String> expired = new HashSet<>();
        for (final PendingRiskApproval pending : this.pendingRiskApprovals.values()) {
            if (pending.expiresAtMillis() <= now) {
                expired.add(pending.id());
                continue;
            }
            views.add(new PendingRiskApprovalView(
                pending.id(),
                pending.fakePlayerName(),
                pending.label(),
                pending.detail(),
                pending.controllerName(),
                Math.max(0L, (pending.expiresAtMillis() - now) / 1000L)
            ));
        }
        for (final String id : expired) {
            this.pendingRiskApprovals.remove(id);
        }
        return views;
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

    private static String blockLine(final Block block) {
        return block.getType().key().value() + "@" + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static String itemLine(final @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "empty";
        }
        return item.getType().key().value() + "x" + item.getAmount();
    }

    record RealFakePlayerView(
        String module,
        String id,
        String displayName,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String gameMode,
        String loops,
        String clickCommand,
        boolean aiEnabled,
        String aiPersona,
        String aiStatus,
        boolean aiFree,
        String entityUuid
    ) {
    }

    private record FakeAiProfile(
        String id,
        String name,
        boolean enabled,
        String goal,
        UUID controllerUuid,
        String controllerName,
        long highRiskAllowedUntilMillis,
        int remainingTurns
    ) {
    }

    private record MovePlan(double forward, double sideways, int ticks, boolean jump, boolean sprinting, boolean sneaking) {
    }

    private record PlaceSurface(Block clickedBlock, BlockFace face) {
    }

    private record WorldEditRegion(Location first, Location second, int volume) {
    }

    private record BuildStep(Location location, Material material, @Nullable BlockData blockData) {
        private BuildStep(final Location location, final Material material) {
            this(location, material, null);
        }
    }

    private record MojangSkinTexture(String value, String signature) {
    }

    private record TargetTask(FakePlayerSnapshot snapshot, String task) {
    }

    private record MentionedFakePlayer(FakePlayerSnapshot snapshot, String task) {
    }

    private record ObservedChat(String player, String world, String message) {
    }

    private record RecentFakeAiLine(String fingerprint, long createdAtMillis) {
    }

    private record QuickActionLock(String category, String detail, long expiresAtMillis) {
    }

    private record PendingRiskApproval(
        String id,
        String fakePlayerName,
        String label,
        String detail,
        UUID controllerUuid,
        String controllerName,
        long expiresAtMillis
    ) {
    }

    private record HighRiskAction(String label, String detail) {
    }

    record PendingRiskApprovalView(
        String id,
        String fakePlayerName,
        String label,
        String detail,
        String requestedBy,
        long expiresInSeconds
    ) {
    }
}
