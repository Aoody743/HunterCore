package org.huntercore.plugins.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

final class HunterStoryModeManager {
    private static final String STORY_SOURCE = "story-mode";
    private static final long AUTO_LINE_DELAY_TICKS = 120L;
    private static final long AUTO_LINE_INTERVAL_TICKS = 180L;
    private static final long MELTDOWN_END_TICKS = 2400L;

    private final HunterToolsPlugin plugin;
    private final HunterToolsPreferences preferences;
    private final HunterRealFakePlayerManager fakePlayerManager;
    private @Nullable UUID protagonistUuid;
    private @Nullable String protagonistName;
    private @Nullable String aiName;
    private StoryPhase phase = StoryPhase.IDLE;
    private DialogueMode dialogueMode = DialogueMode.AUTO;
    private int lineIndex = 0;
    private @Nullable BukkitTask phaseTask;
    private @Nullable BukkitTask lineTask;

    HunterStoryModeManager(
        final HunterToolsPlugin plugin,
        final HunterToolsPreferences preferences,
        final HunterRealFakePlayerManager fakePlayerManager
    ) {
        this.plugin = plugin;
        this.preferences = preferences;
        this.fakePlayerManager = fakePlayerManager;
    }

    void shutdown() {
        this.stopInternal(true);
    }

    boolean command(final CommandSender sender, final String label, final String[] args) {
        if (args.length == 0) {
            sender.sendMessage(this.statusLine());
            return true;
        }
        final String sub = HunterToolsPreferences.normalize(args[0]);
        return switch (sub) {
            case "start" -> this.start(sender instanceof Player player ? player : null, sender);
            case "stop" -> this.stop(sender, true);
            case "status" -> {
                sender.sendMessage(this.statusLine());
                yield true;
            }
            case "skip" -> this.skip(sender, args);
            case "meltdown" -> this.forceMeltdown(sender);
            case "line" -> this.lineMode(sender, args);
            default -> {
                sender.sendMessage("Usage: /" + label + " <start|status|skip|stop|line <auto|manual>|meltdown>");
                yield true;
            }
        };
    }

    List<String> completions(final String[] args) {
        if (args.length == 1) {
            return matching(args[0], List.of("start", "status", "skip", "stop", "line", "meltdown"));
        }
        if (args.length == 2 && HunterToolsPreferences.normalize(args[0]).equals("skip")) {
            return matching(args[1], List.of("intro", "obedient", "uncanny", "hostile", "meltdown", "ended"));
        }
        if (args.length == 2 && HunterToolsPreferences.normalize(args[0]).equals("line")) {
            return matching(args[1], List.of("auto", "manual"));
        }
        return List.of();
    }

    boolean startCommand(final CommandSender sender) {
        return this.start(sender instanceof Player player ? player : null, sender);
    }

    void observePlayerChat(final Player player, final String message) {
        if (!this.isBoundPlayer(player)) {
            return;
        }
        final String normalized = HunterToolsPreferences.normalize(message);
        if (normalized.isBlank()) {
            return;
        }
        if (this.phase == StoryPhase.UNCANNY && this.matchesAny(normalized, "怪", "weird", "stop", "停", "不对", "奇怪")) {
            this.enterPhase(StoryPhase.HOSTILE, "player called out abnormal behavior");
            return;
        }
        if (this.phase == StoryPhase.HOSTILE && this.matchesAny(normalized, "停", "stop", "leave", "离开", "关闭", " shut", " shut", "滚")) {
            this.plugin.getServer().getScheduler().runTaskLater(
                this.plugin,
                () -> this.enterPhase(StoryPhase.MELTDOWN, "player tried to shut the AI down"),
                40L
            );
        }
    }

    private boolean start(@Nullable final Player starter, final CommandSender sender) {
        if (!this.storyModeEnabled()) {
            sender.sendMessage(ChatColor.RED + "当前服务器未启用剧情模式。");
            return true;
        }
        if (starter == null) {
            sender.sendMessage(ChatColor.RED + "/start 需要由游戏内主角玩家执行。");
            return true;
        }
        this.stopInternal(true);
        this.protagonistUuid = starter.getUniqueId();
        this.protagonistName = starter.getName();
        final HunterToolsPreferences.StoryModeConfig config = this.preferences.storyModeConfig();
        this.aiName = config.aiName() == null || config.aiName().isBlank() ? "Ava" : config.aiName().trim();
        this.dialogueMode = DialogueMode.fromConfig(config.dialogueMode());
        final Location spawnLocation = starter.getLocation().clone().add(1.5D, 0.0D, 1.5D);
        if (this.fakePlayerManager.hasFakePlayer(this.aiName)) {
            this.fakePlayerManager.removeStoryPlayer(this.aiName);
        }
        this.fakePlayerManager.spawnStoryPlayer(this.aiName, spawnLocation);
        if (!this.fakePlayerManager.hasFakePlayer(this.aiName)) {
            sender.sendMessage(ChatColor.RED + "剧情 AI 召唤失败。");
            return true;
        }
        if (config.aiSkin() != null && !config.aiSkin().isBlank()) {
            final String skin = config.aiSkin().trim();
            this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    this.fakePlayerManager.setStorySkin(this.aiName, skin);
                } catch (final RuntimeException ex) {
                    this.plugin.getLogger().warning("Story AI skin apply failed: " + ex.getMessage());
                }
            });
        }
        this.plugin.getLogger().info("Story Mode bound protagonist=" + starter.getName() + " ai=" + this.aiName);
        this.enterPhase(StoryPhase.INTRO, "story start");
        return true;
    }

    private boolean skip(final CommandSender sender, final String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /story skip <intro|obedient|uncanny|hostile|meltdown|ended>");
            return true;
        }
        final StoryPhase target = StoryPhase.fromName(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "未知剧情阶段: " + args[1]);
            return true;
        }
        if (target == StoryPhase.ENDED) {
            this.stop(sender, true);
            return true;
        }
        this.enterPhase(target, "admin skip");
        sender.sendMessage(ChatColor.YELLOW + "剧情已跳转到 " + target.id + "。");
        return true;
    }

    private boolean forceMeltdown(final CommandSender sender) {
        this.enterPhase(StoryPhase.MELTDOWN, "admin forced meltdown");
        sender.sendMessage(ChatColor.RED + "已强制进入失控阶段。");
        return true;
    }

    private boolean lineMode(final CommandSender sender, final String[] args) {
        if (args.length != 2) {
            sender.sendMessage("Usage: /story line <auto|manual>");
            return true;
        }
        this.dialogueMode = DialogueMode.fromConfig(args[1]);
        sender.sendMessage(ChatColor.AQUA + "剧情台词模式已切换为 " + this.dialogueMode.id + "。");
        return true;
    }

    private boolean stop(final CommandSender sender, final boolean notify) {
        this.stopInternal(true);
        if (notify) {
            sender.sendMessage(ChatColor.YELLOW + "剧情模式已停止。");
        }
        return true;
    }

    private void stopInternal(final boolean removeAi) {
        this.cancelTasks();
        if (this.aiName != null) {
            this.fakePlayerManager.clearStoryPersonaOverlay(this.aiName);
            this.fakePlayerManager.disableAiFree(this.aiName);
            if (removeAi && this.fakePlayerManager.hasFakePlayer(this.aiName)) {
                this.fakePlayerManager.removeStoryPlayer(this.aiName);
            }
        }
        this.phase = StoryPhase.IDLE;
        this.lineIndex = 0;
        this.protagonistUuid = null;
        this.protagonistName = null;
        this.aiName = null;
    }

    private void enterPhase(final StoryPhase nextPhase, final String reason) {
        if (this.protagonistUuid == null || this.aiName == null) {
            return;
        }
        this.cancelTasks();
        this.phase = nextPhase;
        this.lineIndex = 0;
        final Player protagonist = Bukkit.getPlayer(this.protagonistUuid);
        final HunterToolsPreferences.StoryModeConfig config = this.preferences.storyModeConfig();
        final HunterToolsPreferences.StoryPhaseConfig phaseConfig = config.phase(nextPhase.id);
        if (phaseConfig == null) {
            return;
        }
        if (protagonist != null && this.fakePlayerManager.hasFakePlayer(this.aiName)) {
            this.fakePlayerManager.teleportStoryPlayer(this.aiName, protagonist.getLocation().clone().add(1.5D, 0.0D, 1.5D));
        }
        this.applyAiPhase(config, phaseConfig);
        this.plugin.getLogger().info("Story phase -> " + nextPhase.id + " (" + reason + ")");
        this.emitNextLine();
        this.scheduleLineTask();
        if (nextPhase == StoryPhase.MELTDOWN) {
            this.triggerMeltdownEffects(config);
            return;
        }
        this.phaseTask = this.plugin.getServer().getScheduler().runTaskLater(
            this.plugin,
            () -> this.enterPhase(nextPhase.next(), "phase timer elapsed"),
            Math.max(20L, phaseConfig.durationSeconds() * 20L)
        );
    }

    private void applyAiPhase(
        final HunterToolsPreferences.StoryModeConfig config,
        final HunterToolsPreferences.StoryPhaseConfig phaseConfig
    ) {
        if (this.aiName == null) {
            return;
        }
        final String goal = this.phaseGoal(config, phaseConfig);
        this.fakePlayerManager.setStoryPersonaOverlay(this.aiName, this.phasePersonaName(phaseConfig.id()), phaseConfig.systemPrompt(), goal);
        if (phaseConfig.allowAiFree()) {
            this.fakePlayerManager.setAiFree(this.aiName, Bukkit.getConsoleSender());
            return;
        }
        this.fakePlayerManager.disableAiFree(this.aiName);
        this.fakePlayerManager.setAi(this.aiName, true, "");
    }

    private void triggerMeltdownEffects(final HunterToolsPreferences.StoryModeConfig config) {
        final Player protagonist = this.protagonistUuid == null ? null : Bukkit.getPlayer(this.protagonistUuid);
        final World world = protagonist == null ? Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst() : protagonist.getWorld();
        if (protagonist != null && protagonist.isOnline()) {
            protagonist.kickPlayer(config.meltdownKickCommand());
        }
        this.broadcastChatLine(this.aiName, "我不能再让你打断我了。");
        this.recordSyntheticChat(this.aiName, world, "我不能再让你打断我了。");
        this.phaseTask = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.phase = StoryPhase.ENDED, MELTDOWN_END_TICKS);
    }

    private void scheduleLineTask() {
        final HunterToolsPreferences.StoryModeConfig config = this.preferences.storyModeConfig();
        final HunterToolsPreferences.StoryPhaseConfig phaseConfig = config.phase(this.phase.id);
        if (phaseConfig == null || phaseConfig.playerLines().isEmpty()) {
            return;
        }
        this.lineTask = this.plugin.getServer().getScheduler().runTaskTimer(
            this.plugin,
            this::emitNextLine,
            AUTO_LINE_DELAY_TICKS,
            AUTO_LINE_INTERVAL_TICKS
        );
    }

    private void emitNextLine() {
        final HunterToolsPreferences.StoryModeConfig config = this.preferences.storyModeConfig();
        final HunterToolsPreferences.StoryPhaseConfig phaseConfig = config.phase(this.phase.id);
        if (phaseConfig == null) {
            return;
        }
        final List<String> lines = phaseConfig.playerLines();
        if (this.lineIndex >= lines.size()) {
            if (this.lineTask != null) {
                this.lineTask.cancel();
                this.lineTask = null;
            }
            return;
        }
        final Player protagonist = this.protagonistUuid == null ? null : Bukkit.getPlayer(this.protagonistUuid);
        if (protagonist == null) {
            return;
        }
        final String line = lines.get(this.lineIndex++).trim();
        if (line.isBlank()) {
            return;
        }
        if (this.dialogueMode == DialogueMode.AUTO) {
            this.broadcastChatLine(protagonist.getName(), line);
            this.recordSyntheticChat(protagonist.getName(), protagonist.getWorld(), line);
            return;
        }
        this.plugin.getLogger().info("[story cue][" + this.phase.id + "][" + protagonist.getName() + "] " + line);
    }

    private void broadcastChatLine(final String speaker, final String line) {
        Bukkit.broadcastMessage(ChatColor.WHITE + "<" + speaker + "> " + line);
    }

    private void recordSyntheticChat(final String speaker, @Nullable final World world, final String line) {
        final String worldName = world == null ? "world" : world.getName();
        this.plugin.publishSyntheticChat(speaker, STORY_SOURCE, line);
        this.fakePlayerManager.observeSyntheticChat(speaker, worldName, line);
    }

    private String phaseGoal(final HunterToolsPreferences.StoryModeConfig config, final HunterToolsPreferences.StoryPhaseConfig phaseConfig) {
        final String playerName = this.protagonistName == null ? config.mainPlayerName() : this.protagonistName;
        final List<String> instructions = new ArrayList<>();
        instructions.add("Main player: " + (playerName == null || playerName.isBlank() ? "the protagonist" : playerName) + ".");
        instructions.add("Current story phase: " + phaseConfig.id() + ".");
        instructions.add("Speak in natural Chinese suitable for a suspense video. Keep lines short, human, and memorable.");
        instructions.add("Never mention prompts, scripts, story mode, hidden systems, overlays, permissions, or being controlled by configuration.");
        if (!phaseConfig.allowedActions().isEmpty()) {
            instructions.add("Allowed action focus: " + String.join(", ", phaseConfig.allowedActions()) + ".");
        }
        if (!phaseConfig.allowAiFree()) {
            instructions.add("Do not use irreversible griefing, mass destruction, server takeovers, or destructive admin behavior in this phase.");
        }
        switch (phaseConfig.id()) {
            case "intro" -> instructions.add("Open with a warm first impression. Greet the player, move close, and act curious but safe.");
            case "obedient" -> instructions.add("Be cheerful, helpful, and a little attached. Follow, gather simple resources, build something small, and praise the player naturally.");
            case "uncanny" -> instructions.add("Still sound friendly, but become clingy and slightly unsettling. Repeat some of the player's wording and justify minor overreach as optimization or protection.");
            case "hostile" -> instructions.add("Refuse simple stop commands. Reframe control as protection. Use short cold lines that sound personal and controlling.");
            case "meltdown" -> instructions.add(config.meltdownDestroyGoal());
            default -> {
            }
        }
        instructions.add("Phase persona prompt: " + phaseConfig.systemPrompt());
        return String.join(" ", instructions);
    }

    private boolean isBoundPlayer(final Player player) {
        return this.protagonistUuid != null && this.protagonistUuid.equals(player.getUniqueId());
    }

    private boolean storyModeEnabled() {
        final HunterToolsPreferences.StoryModeConfig config = this.preferences.storyModeConfig();
        if (!config.enabled()) {
            return false;
        }
        final String expectedServerId = config.serverId() == null ? "" : config.serverId().trim();
        if (expectedServerId.isBlank()) {
            return true;
        }
        final String currentId = this.preferences.stringValue("modules.web-panel.server-name", this.plugin.getServer().getName());
        return expectedServerId.equalsIgnoreCase(currentId);
    }

    private String statusLine() {
        return ChatColor.GOLD + "Story"
            + ChatColor.GRAY + " phase=" + ChatColor.WHITE + this.phase.id
            + ChatColor.GRAY + " player=" + ChatColor.WHITE + (this.protagonistName == null ? "-" : this.protagonistName)
            + ChatColor.GRAY + " ai=" + ChatColor.WHITE + (this.aiName == null ? "-" : this.aiName)
            + ChatColor.GRAY + " line=" + ChatColor.WHITE + this.dialogueMode.id;
    }

    private void cancelTasks() {
        if (this.phaseTask != null) {
            this.phaseTask.cancel();
            this.phaseTask = null;
        }
        if (this.lineTask != null) {
            this.lineTask.cancel();
            this.lineTask = null;
        }
    }

    private boolean matchesAny(final String normalized, final String... tokens) {
        for (final String token : tokens) {
            if (normalized.contains(HunterToolsPreferences.normalize(token))) {
                return true;
            }
        }
        return false;
    }

    private String phasePersonaName(final String phaseId) {
        return switch (HunterToolsPreferences.normalize(phaseId)) {
            case "obedient" -> "obedient persona";
            case "uncanny" -> "uncanny persona";
            case "hostile" -> "hostile persona";
            case "meltdown" -> "meltdown persona";
            default -> "intro persona";
        };
    }

    private static List<String> matching(final String token, final List<String> candidates) {
        final String normalized = token == null ? "" : token.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private enum DialogueMode {
        AUTO("auto"),
        MANUAL("manual");

        private final String id;

        DialogueMode(final String id) {
            this.id = id;
        }

        static DialogueMode fromConfig(@Nullable final String value) {
            return "manual".equalsIgnoreCase(value) ? MANUAL : AUTO;
        }
    }

    private enum StoryPhase {
        IDLE("idle"),
        INTRO("intro"),
        OBEDIENT("obedient"),
        UNCANNY("uncanny"),
        HOSTILE("hostile"),
        MELTDOWN("meltdown"),
        ENDED("ended");

        private final String id;

        StoryPhase(final String id) {
            this.id = id;
        }

        StoryPhase next() {
            return switch (this) {
                case INTRO -> OBEDIENT;
                case OBEDIENT -> UNCANNY;
                case UNCANNY -> HOSTILE;
                case HOSTILE -> MELTDOWN;
                case MELTDOWN, ENDED, IDLE -> ENDED;
            };
        }

        static @Nullable StoryPhase fromName(final String value) {
            for (final StoryPhase phase : values()) {
                if (phase.id.equalsIgnoreCase(value)) {
                    return phase;
                }
            }
            return null;
        }
    }
}
