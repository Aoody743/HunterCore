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
    public static final String HPLAYER = "hplayer";
    public static final String HNPC = "hnpc";
    public static final String FAKEPLAYER = "fakeplayer";

    private static final List<CommandEntry> ENTRIES = List.of(
        entry(CORE, "hc help", "/hc help [topic|all]", "查看 HunterCore 指令帮助；topic 可以是 core、admin、shortcuts、hplayer、hnpc、fakeplayer。", "Shows HunterCore command help; topic can be core, admin, shortcuts, hplayer, hnpc, or fakeplayer.", "huntercore.command"),
        entry(CORE, "hc language", "/hc language [zh_cn|en_us]", "查看或切换全服 HunterCore 指令提示语言。", "Shows or changes the server-wide HunterCore command language.", "huntercore.command.language"),
        entry(CORE, "hc about", "/hc about", "显示 HunterCore 版本、服务端和内置插件摘要。", "Shows HunterCore version, server, and bundled plugin summary.", "huntercore.command.about"),
        entry(CORE, "hc system", "/hc system", "显示 JVM、系统、内存、在线人数和插件目录信息。", "Shows JVM, system, memory, player count, and plugin directory information.", "huntercore.command.system"),
        entry(CORE, "hc plugins", "/hc plugins", "显示 HunterCore 内置插件安装状态。", "Shows HunterCore bundled plugin install status.", "huntercore.command.plugins"),
        entry(CORE, "hc preferences", "/hc preferences [list]", "查看核心偏好配置、内置插件和模块开关状态。", "Lists core preferences, bundled plugin state, and module toggles.", "huntercore.command.preferences"),
        entry(CORE, "hc preferences bundled", "/hc preferences bundled <plugin-id> <on|off>", "启用或停用内置插件安装；已加载插件需要重启后卸载。", "Enables or disables bundled plugin installation; already loaded plugins require a restart to unload.", "huntercore.command.preferences"),
        entry(CORE, "hc preferences module", "/hc preferences module <module> <on|off>", "启用或停用 HunterCore 模块默认状态。", "Enables or disables a HunterCore module default state.", "huntercore.command.preferences"),
        entry(CORE, "hc preferences command", "/hc preferences command <module> <command> <on|off>", "启用或停用模块内的某个指令。", "Enables or disables a command inside a module.", "huntercore.command.preferences"),
        entry(CORE, "hc reload", "/hc reload", "重新安装/刷新内置插件偏好；卸载仍需要重启。", "Refreshes bundled plugin preferences; unloading still requires a restart.", "huntercore.command.reload"),

        entry(ADMIN, "hc admin", "/hc admin <subcommand>", "管理模块、网页面板、AI、MOTD、性能和运行时状态。", "Manages modules, web panel, AI, MOTD, performance, and runtime state.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin reload", "/hc admin reload", "重载 HunterTools 配置、假人/NPC、AI 和网页面板。", "Reloads HunterTools config, actors, AI, and the web panel.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin modules", "/hc admin modules", "列出 HunterTools 模块启用状态。", "Lists HunterTools module enabled states.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin module", "/hc admin module <module> <on|off>", "启用或停用某个 HunterTools 模块。", "Enables or disables a HunterTools module.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin command", "/hc admin command <module> <command> <on|off>", "启用或停用某个模块指令。", "Enables or disables one module command.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin plugins", "/hc admin plugins", "列出当前加载插件。", "Lists currently loaded plugins.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin memory", "/hc admin memory", "显示内存使用情况。", "Shows memory usage.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin gc", "/hc admin gc", "异步请求 JVM GC 并显示前后内存。", "Requests JVM GC asynchronously and shows memory before/after.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin threads", "/hc admin threads", "显示线程、假人、NPC 和工作线程状态。", "Shows threads, fake players, NPCs, and worker status.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin optimize", "/hc admin optimize <single-thread|high-clock|high-core|multi-thread|status>", "查看或保存 CPU/异步优化模式。", "Views or saves CPU/async optimization mode.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin motd", "/hc admin motd <status|line1 <text>|line2 <text>|max <number|default>>", "查看或修改服务器列表 MOTD。", "Views or edits the server list MOTD.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web status", "/hc admin web status", "显示网页面板监听地址、端口和地图地址。", "Shows web panel bind address, port, and map URL.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web restart", "/hc admin web restart", "重启网页面板 HTTP 服务。", "Restarts the web panel HTTP service.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web bind", "/hc admin web bind <address>", "设置网页面板监听地址，例如 0.0.0.0。", "Sets the web panel bind address, for example 0.0.0.0.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web port", "/hc admin web port <1-65535>", "设置网页面板端口。", "Sets the web panel port.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web map", "/hc admin web map <url>", "设置网页面板显示的地图 URL。", "Sets the map URL shown in the web panel.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web public-map", "/hc admin web public-map <on|off>", "控制访客是否能看到地图链接。", "Controls whether guests can see the map link.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web users", "/hc admin web users", "列出网页面板用户。", "Lists web panel users.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web user", "/hc admin web user <name> <admin|player> <password>", "创建或更新网页面板用户。", "Creates or updates a web panel user.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web allow", "/hc admin web allow <name> <inherit|none|*|command...>", "设置网页用户可执行的命令。", "Sets commands a web user may execute.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web execution", "/hc admin web execution <name> <on|off>", "启用或停用某网页用户的命令执行。", "Enables or disables command execution for one web user.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web remove", "/hc admin web remove <name>", "删除网页面板用户。", "Removes a web panel user.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai", "/hc admin ai <status|enable|disable|model|base-url|key|clear-key|env|prefix|chat|npc|temperature|max-tokens|test>", "配置 OpenAI 兼容 AI 接入。", "Configures OpenAI-compatible AI integration.", "huntertools.command.admin"),

        entry(SHORTCUTS, "htps", "/htps | /hc tps", "显示 TPS、MSPT 和内存。", "Shows TPS, MSPT, and memory.", "huntertools.command.tps"),
        entry(SHORTCUTS, "heal", "/heal [player] | /hc heal [player]", "治疗自己或指定玩家。", "Heals yourself or a target player.", "huntertools.command.heal"),
        entry(SHORTCUTS, "feed", "/feed [player] | /hc feed [player]", "补满自己或指定玩家饥饿值。", "Feeds yourself or a target player.", "huntertools.command.feed"),
        entry(SHORTCUTS, "fly", "/fly [player] [on|off] | /hc fly [player] [on|off]", "切换飞行权限。", "Toggles flight permission.", "huntertools.command.fly"),
        entry(SHORTCUTS, "gm", "/gm <survival|creative|adventure|spectator> [player] | /hc gm <mode> [player]", "切换游戏模式；也可用 /gms、/gmc、/gma、/gmsp。", "Changes game mode; /gms, /gmc, /gma, and /gmsp are shortcuts.", "huntertools.command.gamemode"),
        entry(SHORTCUTS, "day", "/day [world] | /hc day [world]", "把世界时间设为白天。", "Sets a world's time to day.", "huntertools.command.time"),
        entry(SHORTCUTS, "night", "/night [world] | /hc night [world]", "把世界时间设为夜晚。", "Sets a world's time to night.", "huntertools.command.time"),
        entry(SHORTCUTS, "sun", "/sun [world] | /hc sun [world]", "清除天气。", "Clears weather.", "huntertools.command.weather"),
        entry(SHORTCUTS, "rain", "/rain [world] | /hc rain [world]", "开始下雨。", "Starts rain.", "huntertools.command.weather"),
        entry(SHORTCUTS, "thunder", "/thunder [world] | /hc thunder [world]", "开始雷暴。", "Starts thunder.", "huntertools.command.weather"),
        entry(SHORTCUTS, "broadcast", "/broadcast <message> | /hc broadcast <message>", "向全服广播消息；别名 /bc。", "Broadcasts a message; alias /bc.", "huntertools.command.broadcast"),
        entry(SHORTCUTS, "clearchat", "/clearchat | /hc clearchat", "清空在线玩家聊天窗口；别名 /cc。", "Clears online players' chat window; alias /cc.", "huntertools.command.clearchat"),
        entry(SHORTCUTS, "speed", "/speed <1-10> [player] [walk|fly] | /hc speed <1-10> [player] [walk|fly]", "设置行走或飞行速度。", "Sets walk or fly speed.", "huntertools.command.speed"),
        entry(SHORTCUTS, "spawn", "/spawn [player] | /hc spawn [player]", "传送到服务器出生点。", "Teleports to the configured server spawn.", "huntertools.command.spawn"),
        entry(SHORTCUTS, "setspawn", "/setspawn | /hc setspawn", "把当前位置设为服务器出生点。", "Sets the server spawn to your current location.", "huntertools.command.setspawn"),
        entry(SHORTCUTS, "back", "/back | /hc back", "返回上一次记录的位置。", "Returns to your previous saved location.", "huntertools.command.back"),
        entry(SHORTCUTS, "hat", "/hat | /hc hat", "把手中物品戴到头上，和头盔互换。", "Swaps your main-hand item with your helmet slot.", "huntertools.command.hat"),
        entry(SHORTCUTS, "craft", "/craft | /hc craft", "打开工作台；别名 /workbench、/wb。", "Opens a crafting table; aliases /workbench and /wb.", "huntertools.command.craft"),
        entry(SHORTCUTS, "enderchest", "/enderchest [player] | /hc enderchest [player]", "打开末影箱；有权限时可打开他人末影箱。", "Opens an ender chest; with permission can open another player's chest.", "huntertools.command.enderchest"),
        entry(SHORTCUTS, "trash", "/trash | /hc trash", "打开丢弃箱，关闭后里面物品会被丢弃。", "Opens a disposal inventory; items left inside are discarded.", "huntertools.command.trash"),

        entry(TPA, "tpa", "/tpa <player>", "请求传送到某个玩家身边，目标玩家可点击同意/拒绝。", "Requests to teleport to another player; the target can click accept/deny.", "huntertpa.command.tpa"),
        entry(TPA, "tpahere", "/tpahere <player>", "请求某个玩家传送到你身边，目标玩家可点击同意/拒绝。", "Requests another player to teleport to you; the target can click accept/deny.", "huntertpa.command.tpahere"),
        entry(TPA, "tpaccept", "/tpaccept [player]", "同意待处理传送请求。", "Accepts a pending teleport request.", "huntertpa.command.tpaccept"),
        entry(TPA, "tpdeny", "/tpdeny [player]", "拒绝待处理传送请求。", "Denies a pending teleport request.", "huntertpa.command.tpdeny"),
        entry(TPA, "tpcancel", "/tpcancel", "取消你发出的传送请求。", "Cancels your outgoing teleport request.", "huntertpa.command.tpcancel"),
        entry(TPA, "sethome", "/sethome [name]", "把当前位置保存为家。", "Saves your current location as a home.", "huntertpa.command.sethome"),
        entry(TPA, "home", "/home [name]", "传送到已保存的家。", "Teleports to a saved home.", "huntertpa.command.home"),
        entry(TPA, "delhome", "/delhome [name]", "删除已保存的家。", "Deletes a saved home.", "huntertpa.command.delhome"),
        entry(TPA, "homes", "/homes", "列出你的家。", "Lists your homes.", "huntertpa.command.homes"),

        entry(AUTH, "register", "/register <password> <password>", "注册 HunterAuth 密码；如果网页面板关闭强制注册，则不需要注册。", "Registers a HunterAuth password; not required when registration is disabled in the web panel.", "hunterauth.command.register"),
        entry(AUTH, "login", "/login <password>", "使用 HunterAuth 密码登录；也可以通过登录 GUI 输入。", "Logs in with your HunterAuth password; the login GUI can also collect it.", "hunterauth.command.login"),
        entry(AUTH, "logout", "/logout", "锁定当前登录会话。", "Locks your current auth session.", "hunterauth.command.logout"),
        entry(AUTH, "changepassword", "/changepassword <old> <new>", "修改 HunterAuth 密码。", "Changes your HunterAuth password.", "hunterauth.command.changepassword"),

        entry(HPLAYER, "hplayer", "/hplayer <spawn|remove|kill|list|tp|tphere|look|move|sneak|sprint|jump|use|attack|stop|click|drop|dropstack|swap|gm|slot|ai|info|clear>", "管理真实 ServerPlayer 假人；别名 /playerbot。", "Manages real ServerPlayer fake players; alias /playerbot.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer spawn", "/hplayer spawn <name> [world x y z [yaw pitch]]", "生成真实假人。", "Spawns a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer remove", "/hplayer remove <name>", "移除真实假人。", "Removes a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer kill", "/hplayer kill <name>", "杀死真实假人实体。", "Kills the real fake player entity.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer list", "/hplayer list", "列出真实假人。", "Lists real fake players.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer tp", "/hplayer tp <name> [world x y z [yaw pitch]]", "传送真实假人。", "Teleports a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer tphere", "/hplayer tphere <name>", "把真实假人传送到你身边。", "Teleports a real fake player to you.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer look", "/hplayer look <name> <yaw pitch|north|south|east|west|up|down>", "调整真实假人视角。", "Changes a real fake player's look direction.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer move", "/hplayer move <name> <forward> <sideways> [ticks] [sprint] [jump] [sneak]", "让真实假人移动一段时间。", "Moves a real fake player for a short duration.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer sneak", "/hplayer sneak <name> <on|off>", "切换真实假人潜行。", "Toggles sneaking for a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer sprint", "/hplayer sprint <name> <on|off>", "切换真实假人疾跑。", "Toggles sprinting for a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer jump", "/hplayer jump <name> [once|continuous|stop]", "让真实假人跳跃一次或持续跳跃。", "Makes a real fake player jump once or continuously.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer use", "/hplayer use <name> [once|continuous|stop]", "让真实假人使用主手物品。", "Makes a real fake player use the main-hand item.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer attack", "/hplayer attack <name> [once|continuous|stop]", "让真实假人攻击。", "Makes a real fake player attack.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer stop", "/hplayer stop <name>", "停止真实假人的持续动作。", "Stops continuous actions for a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer click", "/hplayer click <name> [command|clear]", "设置玩家点击真实假人时执行的命令。", "Sets the command run when a player clicks the fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer drop", "/hplayer drop <name>", "丢出主手单个物品。", "Drops one selected item.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer dropstack", "/hplayer dropstack <name>", "丢出主手整组物品。", "Drops the selected stack.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer swap", "/hplayer swap <name>", "交换主副手。", "Swaps main hand and offhand.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer gm", "/hplayer gm <name> <survival|creative|adventure|spectator>", "设置真实假人游戏模式。", "Sets game mode for a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer slot", "/hplayer slot <name> <1-9>", "切换真实假人快捷栏槽位。", "Changes selected hotbar slot for a real fake player.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer ai", "/hplayer ai <name> <on|off|run|goal|approve-risk> [text]", "启用真实假人 AI、设置目标或批准高风险任务。", "Controls real fake player AI, goals, and high-risk approvals.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer info", "/hplayer info [name]", "查看真实假人状态。", "Shows real fake player status.", "huntertools.command.hplayer"),
        entry(HPLAYER, "hplayer clear", "/hplayer clear", "移除全部真实假人。", "Removes all real fake players.", "huntertools.command.hplayer"),

        entry(HNPC, "hnpc", "/hnpc <spawn|remove|list|tp|tphere|look|pose|click|info|clear>", "管理 HunterCore NPC；/npc 是兼容别名。", "Manages HunterCore NPCs; /npc is a compatibility alias.", "huntertools.command.npc"),
        entry(HNPC, "hnpc spawn", "/hnpc spawn <name> [villager|mannequin] [world x y z [yaw pitch]]", "生成 NPC。", "Spawns an NPC.", "huntertools.command.npc"),
        entry(HNPC, "hnpc remove", "/hnpc remove <name>", "移除 NPC。", "Removes an NPC.", "huntertools.command.npc"),
        entry(HNPC, "hnpc list", "/hnpc list", "列出 NPC。", "Lists NPCs.", "huntertools.command.npc"),
        entry(HNPC, "hnpc tp", "/hnpc tp <name> [world x y z [yaw pitch]]", "传送 NPC。", "Teleports an NPC.", "huntertools.command.npc"),
        entry(HNPC, "hnpc tphere", "/hnpc tphere <name>", "把 NPC 传送到你身边。", "Teleports an NPC to you.", "huntertools.command.npc"),
        entry(HNPC, "hnpc look", "/hnpc look <name> [yaw pitch|north|south|east|west|up|down]", "调整 NPC 朝向。", "Changes an NPC's look direction.", "huntertools.command.npc"),
        entry(HNPC, "hnpc pose", "/hnpc pose <name> <standing|sneaking|swimming|fall-flying|sleeping>", "设置 NPC 姿势。", "Sets an NPC pose.", "huntertools.command.npc"),
        entry(HNPC, "hnpc click", "/hnpc click <name> [command|clear]", "设置点击 NPC 时执行的命令。", "Sets the command run when a player clicks the NPC.", "huntertools.command.npc"),
        entry(HNPC, "hnpc info", "/hnpc info [name]", "查看 NPC 状态。", "Shows NPC status.", "huntertools.command.npc"),
        entry(HNPC, "hnpc clear", "/hnpc clear", "移除全部 NPC。", "Removes all NPCs.", "huntertools.command.npc"),

        entry(FAKEPLAYER, "hc fakeplayer", "/hc fakeplayer <spawn|remove|list|tp|tphere|look|pose|click|info|clear>", "管理轻量 Mannequin 假人，用于展示、占位和简单交互。", "Manages lightweight Mannequin fake players for display, placeholders, and simple interaction.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer spawn", "/hc fakeplayer spawn <name> [world x y z [yaw pitch]]", "生成轻量假人。", "Spawns a lightweight fake player.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer remove", "/hc fakeplayer remove <name>", "移除轻量假人。", "Removes a lightweight fake player.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer list", "/hc fakeplayer list", "列出轻量假人。", "Lists lightweight fake players.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer tp", "/hc fakeplayer tp <name> [world x y z [yaw pitch]]", "传送轻量假人。", "Teleports a lightweight fake player.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer tphere", "/hc fakeplayer tphere <name>", "把轻量假人传送到你身边。", "Teleports a lightweight fake player to you.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer look", "/hc fakeplayer look <name> [yaw pitch|north|south|east|west|up|down]", "调整轻量假人朝向。", "Changes a lightweight fake player's look direction.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer pose", "/hc fakeplayer pose <name> <standing|sneaking|swimming|fall-flying|sleeping>", "设置轻量假人姿势。", "Sets a lightweight fake player pose.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer click", "/hc fakeplayer click <name> [command|clear]", "设置点击轻量假人时执行的命令。", "Sets the command run when a player clicks the lightweight fake player.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer info", "/hc fakeplayer info [name]", "查看轻量假人状态。", "Shows lightweight fake player status.", "huntertools.command.fakeplayer"),
        entry(FAKEPLAYER, "hc fakeplayer clear", "/hc fakeplayer clear", "移除全部轻量假人。", "Removes all lightweight fake players.", "huntertools.command.fakeplayer")
    );

    private HunterHelp() {
    }

    public static @NotNull List<CommandEntry> entries() {
        return ENTRIES;
    }

    public static @NotNull List<String> topics() {
        final List<String> topics = new ArrayList<>(List.of("all", CORE, ADMIN, SHORTCUTS, TPA, "teleport", "homes", AUTH, "login", HPLAYER, "playerbot", HNPC, "npc", FAKEPLAYER));
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
                + "core, admin, shortcuts, tpa, auth, hplayer, hnpc, fakeplayer, all"));
            return;
        }
        sendEntries(sender, language, matches, permissionFilter);
    }

    private static void sendOverview(@NotNull final CommandSender sender, @Nullable final String language) {
        sender.sendMessage(color("&6HunterCore " + HunterLanguage.choose(language, "帮助", "Help") + " &8| &7" + HunterLanguage.normalize(language)));
        sender.sendMessage(color("&e/hc help <topic> &7- " + HunterLanguage.choose(language, "查看某类命令说明。", "Show command help by topic.")));
        sender.sendMessage(color("&e/hc language <zh_cn|en_us> &7- " + HunterLanguage.choose(language, "切换全服 HunterCore 提示语言。", "Change server-wide HunterCore command language.")));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "主题：", "Topics: ") + "core, admin, shortcuts, tpa, auth, hplayer/playerbot, hnpc/npc, fakeplayer, all"));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "示例：", "Examples: ") + "/hc help fly, /hc help hplayer, /help tpahere, /help login"));
    }

    private static void sendEntries(
        @NotNull final CommandSender sender,
        @Nullable final String language,
        @NotNull final Collection<CommandEntry> entries,
        @NotNull final Predicate<@Nullable String> permissionFilter
    ) {
        sender.sendMessage(color("&6HunterCore " + HunterLanguage.choose(language, "命令说明", "Command Help")));
        int shown = 0;
        for (final CommandEntry entry : entries) {
            if (!permissionFilter.test(entry.permission())) {
                continue;
            }
            sender.sendMessage(color("&e" + entry.usage() + " &7- " + entry.description(language)));
            shown++;
        }
        if (shown == 0) {
            sender.sendMessage(color("&c" + HunterLanguage.choose(language, "你没有权限查看这个主题下的命令。", "You do not have permission to view commands in this topic.")));
        }
    }

    private static @Nullable String category(@NotNull final String query) {
        return switch (query) {
            case CORE -> CORE;
            case ADMIN, "management", "manage" -> ADMIN;
            case SHORTCUTS, "shortcut", "essentials", "essential" -> SHORTCUTS;
            case TPA, "teleport", "teleportation", "homes", "home" -> TPA;
            case AUTH, "login", "register", "password" -> AUTH;
            case HPLAYER, "playerbot", "real-fake-player", "real-fake-players", "realfakeplayer" -> HPLAYER;
            case HNPC, "npc", "npcs" -> HNPC;
            case FAKEPLAYER, "fake-player", "fake-players" -> FAKEPLAYER;
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
