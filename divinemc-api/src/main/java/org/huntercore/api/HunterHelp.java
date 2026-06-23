package org.huntercore.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HunterHelp {
    public static final String CORE = "core";
    public static final String ADMIN = "admin";
    public static final String SHORTCUTS = "shortcuts";
    public static final String TPA = "tpa";
    public static final String AUTH = "auth";
    public static final String PLAYER = "player";
    public static final String NPC = "npc";

    private static final List<CommandEntry> ENTRIES = List.of(
        entry(CORE, "hc help", "/hc help [topic|all]", "查看 HunterCore 帮助主题。", "Shows HunterCore help topics.", "huntercore.command"),
        entry(CORE, "hc language", "/hc language [zh_cn|en_us]", "切换 HunterCore 指令语言。", "Changes the HunterCore command language.", "huntercore.command.language"),
        entry(CORE, "hc about", "/hc about", "显示 HunterCore 版本和内置插件信息。", "Shows HunterCore version and bundled plugin information.", "huntercore.command.about"),
        entry(CORE, "hc system", "/hc system", "显示 JVM、系统、内存和玩家数量。", "Shows JVM, system, memory, and player count.", "huntercore.command.system"),
        entry(CORE, "hc plugins", "/hc plugins", "显示内置插件安装状态。", "Shows bundled plugin install status.", "huntercore.command.plugins"),
        entry(CORE, "hc preferences", "/hc preferences [list]", "查看核心偏好配置和模块状态。", "Lists core preferences and module states.", "huntercore.command.preferences"),
        entry(CORE, "hc reload", "/hc reload", "刷新 HunterCore 偏好配置。", "Reloads HunterCore preferences.", "huntercore.command.reload"),

        entry(ADMIN, "hc admin", "/hc admin <reload|modules|web|ai|motd|plugins|memory|threads>", "管理后台、模块、AI、网页面板和运行状态。", "Manages modules, AI, web panel, MOTD, and runtime state.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web", "/hc admin web <status|restart|bind|port|map|users>", "管理网页面板监听、地图和用户。", "Manages web panel bind, map, and users.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai", "/hc admin ai <status|enable|disable|model|base-url|key|chat|npc|test>", "配置 OpenAI 兼容 AI。", "Configures OpenAI-compatible AI.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin module", "/hc admin module <module> <on|off>", "开启或关闭 HunterTools 模块。", "Enables or disables a HunterTools module.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin command", "/hc admin command <module> <command> <on|off>", "开启或关闭模块内某条命令。", "Enables or disables a module command.", "huntertools.command.admin"),

        entry(SHORTCUTS, "tps", "/tps", "显示 TPS、MSPT 和内存。", "Shows TPS, MSPT, and memory.", "huntertools.command.tps"),
        entry(SHORTCUTS, "heal", "/heal [player]", "治疗自己或指定玩家。", "Heals yourself or a target player.", "huntertools.command.heal"),
        entry(SHORTCUTS, "feed", "/feed [player]", "补满自己或指定玩家的饥饿值。", "Feeds yourself or a target player.", "huntertools.command.feed"),
        entry(SHORTCUTS, "fly", "/fly [player] [on|off]", "切换飞行。", "Toggles flight.", "huntertools.command.fly"),
        entry(SHORTCUTS, "gm", "/gm <survival|creative|adventure|spectator> [player]", "切换游戏模式。", "Changes game mode.", "huntertools.command.gamemode"),
        entry(SHORTCUTS, "time", "/day | /night", "切换白天或夜晚。", "Changes time to day or night.", "huntertools.command.time"),
        entry(SHORTCUTS, "weather", "/sun | /rain | /thunder", "切换天气。", "Changes weather.", "huntertools.command.weather"),
        entry(SHORTCUTS, "spawn", "/spawn [player]", "传送到服务器出生点。", "Teleports to server spawn.", "huntertools.command.spawn"),
        entry(SHORTCUTS, "back", "/back", "返回上一次记录位置。", "Returns to the previous saved location.", "huntertools.command.back"),
        entry(SHORTCUTS, "enderchest", "/enderchest [player]", "打开末影箱。", "Opens an ender chest.", "huntertools.command.enderchest"),

        entry(TPA, "tpa", "/tpa <player>", "请求传送到玩家身边。", "Requests teleport to a player.", "huntertpa.command.tpa"),
        entry(TPA, "tpahere", "/tpahere <player>", "请求玩家传送到你身边。", "Requests a player to teleport to you.", "huntertpa.command.tpahere"),
        entry(TPA, "tpaccept", "/tpaccept [player]", "同意传送请求。", "Accepts a teleport request.", "huntertpa.command.tpaccept"),
        entry(TPA, "tpdeny", "/tpdeny [player]", "拒绝传送请求。", "Denies a teleport request.", "huntertpa.command.tpdeny"),
        entry(TPA, "home", "/sethome [name] | /home [name] | /delhome [name] | /homes", "管理个人传送点。", "Manages personal homes.", "huntertpa.command.home"),

        entry(AUTH, "register", "/register <password> <password>", "注册 HunterAuth 密码。", "Registers a HunterAuth password.", "hunterauth.command.register"),
        entry(AUTH, "login", "/login <password>", "登录 HunterAuth。", "Logs in with HunterAuth.", "hunterauth.command.login"),
        entry(AUTH, "logout", "/logout", "锁定当前登录会话。", "Locks the current auth session.", "hunterauth.command.logout"),
        entry(AUTH, "changepassword", "/changepassword <old> <new>", "修改 HunterAuth 密码。", "Changes the HunterAuth password.", "hunterauth.command.changepassword"),

        entry(PLAYER, "player", "/player <help|spawn|remove|list|inv|skin|move|use|attack|ai|info>", "管理真实玩家 Bot。", "Manages real player bots.", "huntertools.command.hplayer"),
        entry(PLAYER, "player spawn", "/player spawn <name> [world x y z [yaw pitch]]", "生成真实玩家 Bot。", "Spawns a real player bot.", "huntertools.command.hplayer"),
        entry(PLAYER, "player remove", "/player remove <name>", "移除真实玩家 Bot。", "Removes a real player bot.", "huntertools.command.hplayer"),
        entry(PLAYER, "player list", "/player list", "列出真实玩家 Bot。", "Lists real player bots.", "huntertools.command.hplayer"),
        entry(PLAYER, "player inv", "/player inv <name>", "打开真实玩家 Bot 的背包并直接编辑。", "Opens a real player bot inventory for editing.", "huntertools.command.hplayer"),
        entry(PLAYER, "player skin", "/player skin <name> <minecraftName|clear>", "从正版玩家名加载 Bot 皮肤，或用 clear 清除。", "Loads a bot skin from an official Minecraft name, or clears it.", "huntertools.command.hplayer"),
        entry(PLAYER, "player move", "/player move <name> <forward|back|left|right|stop|value> [sideways] [ticks]", "让 Bot 移动一小段。", "Moves a bot for a short duration.", "huntertools.command.hplayer"),
        entry(PLAYER, "player use", "/player use <name> [once|continuous|stop]", "让 Bot 使用主手物品。", "Makes a bot use the main-hand item.", "huntertools.command.hplayer"),
        entry(PLAYER, "player attack", "/player attack <name> [once|continuous|stop]", "让 Bot 攻击或挖掘视线目标。", "Makes a bot attack or mine the target.", "huntertools.command.hplayer"),
        entry(PLAYER, "player ai", "/player ai <name> <status|on|off|goal|once|approve|deny> [text]", "管理 Bot AI、目标和高风险动作审批。", "Controls bot AI, goals, and high-risk approvals.", "huntertools.command.hplayer"),
        entry(PLAYER, "player stop", "/player stop <name>", "停止 Bot 当前动作。", "Stops a bot's current actions.", "huntertools.command.hplayer"),
        entry(PLAYER, "player info", "/player info [name]", "查看 Bot 状态。", "Shows bot status.", "huntertools.command.hplayer"),
        entry(PLAYER, "player clear", "/player clear", "移除全部真实玩家 Bot。", "Removes all real player bots.", "huntertools.command.hplayer"),

        entry(NPC, "npc", "/npc <help|spawn|remove|list|skin|pose|click|info>", "管理展示 NPC；mannequin 支持玩家皮肤。", "Manages display NPCs; mannequins support player skins.", "huntertools.command.npc"),
        entry(NPC, "npc spawn", "/npc spawn <name> [villager|mannequin] [world x y z [yaw pitch]]", "生成 NPC。villager 适合功能 NPC，mannequin 适合玩家外观展示。", "Spawns an NPC. Villagers are functional; mannequins are player-like displays.", "huntertools.command.npc"),
        entry(NPC, "npc remove", "/npc remove <name>", "移除 NPC。", "Removes an NPC.", "huntertools.command.npc"),
        entry(NPC, "npc list", "/npc list", "列出 NPC。", "Lists NPCs.", "huntertools.command.npc"),
        entry(NPC, "npc skin", "/npc skin <name> <minecraftName|clear>", "给 mannequin NPC 加载正版玩家皮肤；villager 不支持玩家皮肤。", "Loads an official player skin for mannequin NPCs; villagers do not support player skins.", "huntertools.command.npc"),
        entry(NPC, "npc pose", "/npc pose <name> <standing|sneaking|swimming|fall-flying|sleeping>", "设置 mannequin 姿势。", "Sets a mannequin pose.", "huntertools.command.npc"),
        entry(NPC, "npc click", "/npc click <name> [command|clear]", "设置点击 NPC 时执行的命令。", "Sets the command run when a player clicks the NPC.", "huntertools.command.npc"),
        entry(NPC, "npc info", "/npc info [name]", "查看 NPC 状态。", "Shows NPC status.", "huntertools.command.npc"),
        entry(NPC, "npc clear", "/npc clear", "移除全部 NPC。", "Removes all NPCs.", "huntertools.command.npc")
    );

    private HunterHelp() {
    }

    public static @NotNull List<CommandEntry> entries() {
        return ENTRIES;
    }

    public static @NotNull List<String> topics() {
        final List<String> topics = new ArrayList<>(List.of("all", CORE, ADMIN, SHORTCUTS, TPA, "teleport", "homes", AUTH, "login", PLAYER, NPC));
        for (final CommandEntry entry : ENTRIES) {
            topics.add(entry.topic());
            topics.add(entry.topic().replace("hc ", ""));
        }
        return topics.stream().distinct().toList();
    }

    public static @NotNull List<String> matchingTopics(@NotNull final String prefix) {
        final String normalized = normalize(prefix);
        final List<String> matches = new ArrayList<>();
        for (final String topic : topics()) {
            if (topic.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(topic);
            }
        }
        return matches;
    }

    public static void send(@NotNull final CommandSender sender, @Nullable final String language, @NotNull final String[] args) {
        send(sender, language, args, permission -> permission == null || sender.hasPermission(permission));
    }

    public static void send(
        @NotNull final CommandSender sender,
        @Nullable final String language,
        @NotNull final String[] args,
        @NotNull final Predicate<@Nullable String> permissionFilter
    ) {
        final String query = args.length == 0 ? "" : normalize(String.join(" ", args));
        if (query.isBlank()) {
            sendOverview(sender, language);
            return;
        }
        if (query.equals("all")) {
            sendEntries(sender, language, ENTRIES, permissionFilter);
            return;
        }
        final String category = category(query);
        if (category != null) {
            sendEntries(sender, language, ENTRIES.stream().filter(entry -> entry.category().equals(category)).toList(), permissionFilter);
            return;
        }
        final List<CommandEntry> matches = ENTRIES.stream()
            .filter(entry -> entry.matches(query))
            .toList();
        if (matches.isEmpty()) {
            sender.sendMessage(color("&c" + HunterLanguage.choose(language, "没有找到帮助主题：", "No help topic found: ") + query));
            sender.sendMessage(color("&7" + HunterLanguage.choose(language, "可用主题：", "Available topics: ")
                + "core, admin, shortcuts, tpa, auth, player, npc, all"));
            return;
        }
        sendEntries(sender, language, matches, permissionFilter);
    }

    private static void sendOverview(@NotNull final CommandSender sender, @Nullable final String language) {
        sender.sendMessage(color("&6HunterCore " + HunterLanguage.choose(language, "帮助", "Help") + " &8| &7" + HunterLanguage.normalize(language)));
        sender.sendMessage(color("&e/hc help <topic> &7- " + HunterLanguage.choose(language, "查看某类指令。", "Show command help by topic.")));
        sender.sendMessage(color("&e/player help &7- " + HunterLanguage.choose(language, "管理真实玩家 Bot。", "Manage real player bots.")));
        sender.sendMessage(color("&e/npc help &7- " + HunterLanguage.choose(language, "管理展示 NPC。", "Manage display NPCs.")));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "主题：", "Topics: ") + "core, admin, shortcuts, tpa, auth, player, npc, all"));
    }

    private static void sendEntries(
        @NotNull final CommandSender sender,
        @Nullable final String language,
        @NotNull final Collection<CommandEntry> entries,
        @NotNull final Predicate<@Nullable String> permissionFilter
    ) {
        sender.sendMessage(color("&6HunterCore " + HunterLanguage.choose(language, "指令帮助", "Command Help")));
        int shown = 0;
        for (final CommandEntry entry : entries) {
            if (!permissionFilter.test(entry.permission())) {
                continue;
            }
            sender.sendMessage(color("&e" + entry.usage() + " &7- " + entry.description(language)));
            shown++;
        }
        if (shown == 0) {
            sender.sendMessage(color("&c" + HunterLanguage.choose(language, "你没有权限查看这个主题。", "You do not have permission to view this topic.")));
        }
    }

    private static @Nullable String category(@NotNull final String query) {
        return switch (query) {
            case CORE -> CORE;
            case ADMIN, "management", "manage" -> ADMIN;
            case SHORTCUTS, "shortcut", "essentials", "essential" -> SHORTCUTS;
            case TPA, "teleport", "teleportation", "homes", "home" -> TPA;
            case AUTH, "login", "register", "password" -> AUTH;
            case PLAYER, "bot", "bots", "real-fake-player", "real-fake-players" -> PLAYER;
            case NPC, "npcs", "mannequin", "villager" -> NPC;
            default -> null;
        };
    }

    private static CommandEntry entry(
        final String category,
        final String topic,
        final String usage,
        final String zhCn,
        final String enUs,
        final String permission
    ) {
        return new CommandEntry(category, normalize(topic), usage, zhCn, enUs, permission);
    }

    private static @NotNull String normalize(@Nullable final String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toLowerCase(Locale.ROOT).replace('/', ' ').replaceAll("\\s+", " ").trim();
    }

    private static @NotNull String color(@NotNull final String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public record CommandEntry(
        @NotNull String category,
        @NotNull String topic,
        @NotNull String usage,
        @NotNull String zhCn,
        @NotNull String enUs,
        @Nullable String permission
    ) {
        public @NotNull String description(@Nullable final String language) {
            return HunterLanguage.choose(language, this.zhCn, this.enUs);
        }

        private boolean matches(@NotNull final String query) {
            final String normalizedUsage = normalize(this.usage);
            return this.topic.equals(query)
                || this.topic.endsWith(" " + query)
                || normalizedUsage.contains(query)
                || query.contains(this.topic);
        }
    }
}
