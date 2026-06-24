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

    private static final List<CommandEntry> LEGACY_ENTRIES = List.of(
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

    private static final List<CommandEntry> ENTRIES = List.of(
        entry(CORE, "hc help", "/hc help [topic|all]", "查看 HunterCore 帮助主题。", "Shows HunterCore help topics.", "huntercore.command"),
        entry(CORE, "hc language", "/hc language [zh_cn|en_us]", "切换 HunterCore 指令语言。", "Changes the HunterCore command language.", "huntercore.command.language"),
        entry(CORE, "hc about", "/hc about", "显示 HunterCore 版本和内置插件信息。", "Shows HunterCore version and bundled plugin information.", "huntercore.command.about"),
        entry(CORE, "about", "/about", "显示可在网页面板自定义的服务器介绍。", "Shows server about text customized from the web panel.", "huntercore.command.about"),
        entry(CORE, "plugins", "/plugins | /pl", "显示可在网页面板自定义的插件说明。", "Shows plugin text customized from the web panel.", null),
        entry(CORE, "version", "/version | /ver", "显示可在网页面板自定义的版本说明。", "Shows version text customized from the web panel.", null),
        entry(CORE, "rules", "/rules", "显示可在网页面板自定义的服务器规则。", "Shows server rules customized from the web panel.", null),
        entry(CORE, "discord", "/discord", "显示可在网页面板自定义的 Discord 信息。", "Shows Discord text customized from the web panel.", null),
        entry(CORE, "website", "/website | /site", "显示可在网页面板自定义的网站信息。", "Shows website text customized from the web panel.", null),
        entry(CORE, "motd command", "/motd", "显示可在网页面板自定义的服务器 MOTD 文案。", "Shows MOTD text customized from the web panel.", null),
        entry(CORE, "info", "/info | /server", "显示可在网页面板自定义的服务器信息。", "Shows server info text customized from the web panel.", null),
        entry(CORE, "links", "/links", "显示可在网页面板自定义的服务器链接。", "Shows server links customized from the web panel.", null),
        entry(CORE, "qq", "/qq | /group", "显示可在网页面板自定义的 QQ 群或社群信息。", "Shows QQ/group text customized from the web panel.", null),
        entry(CORE, "hc system", "/hc system", "显示 JVM、系统、内存和玩家数量。", "Shows JVM, system, memory, and player count.", "huntercore.command.system"),
        entry(CORE, "hc plugins", "/hc plugins", "显示 HunterCore 内置插件安装状态。", "Shows bundled plugin install status.", "huntercore.command.plugins"),
        entry(CORE, "hc preferences", "/hc preferences [list]", "查看核心偏好配置和模块状态。", "Lists core preferences and module states.", "huntercore.command.preferences"),
        entry(CORE, "hc reload", "/hc reload", "刷新 HunterCore 核心偏好配置。", "Reloads HunterCore preferences.", "huntercore.command.reload"),

        entry(ADMIN, "hc admin", "/hc admin <reload|modules|module|command|plugins|memory|gc|threads|optimize|motd|web|ai>", "管理模块、网页面板、AI、MOTD 和运行状态。", "Manages modules, web panel, AI, MOTD, and runtime state.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin reload", "/hc admin reload", "重载 HunterTools 配置并重启网页面板。", "Reloads HunterTools preferences and restarts the web panel.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin modules", "/hc admin modules", "列出 HunterTools 模块开关状态。", "Lists HunterTools module states.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin module", "/hc admin module <module> <on|off>", "开启或关闭指定模块。", "Enables or disables a module.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin command", "/hc admin command <module> <command> <on|off>", "开启或关闭模块内某条命令。", "Enables or disables a command inside a module.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin plugins", "/hc admin plugins", "列出当前加载插件。", "Lists loaded plugins.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin memory", "/hc admin memory", "显示内存占用。", "Shows memory usage.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin gc", "/hc admin gc", "请求一次后台垃圾回收并显示内存变化。", "Requests background garbage collection and reports memory change.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin threads", "/hc admin threads", "显示线程和 HunterTools 工作线程状态。", "Shows thread and HunterTools worker status.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin optimize", "/hc admin optimize <status|single-thread|high-clock|high-core|multi-thread>", "查看或保存 CPU 优化模式，重启后完整生效。", "Shows or saves CPU optimization mode; restart to fully apply.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin motd", "/hc admin motd <status|line1|line2|max>", "查看或修改服务器列表 MOTD。", "Views or edits the server-list MOTD.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web", "/hc admin web <status|restart|bind|port|map|public-map|users|user|remove|allow|execution>", "管理网页面板监听、地图、用户和网页命令权限。", "Manages web panel bind, map, users, and web command permissions.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web status", "/hc admin web status", "查看网页面板监听地址、端口和地图配置。", "Shows web panel bind, port, and map settings.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web restart", "/hc admin web restart", "重启网页面板 HTTP 服务。", "Restarts the web panel HTTP service.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web bind", "/hc admin web bind <address>", "设置网页面板监听地址，局域网访问通常用 0.0.0.0。", "Sets the web panel bind address; use 0.0.0.0 for LAN access.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web port", "/hc admin web port <port>", "设置网页面板端口并重启面板。", "Sets the web panel port and restarts the panel.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web map", "/hc admin web map <url>", "设置首页地图 URL，支持 %host% 占位符。", "Sets the homepage map URL; supports the %host% placeholder.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web public-map", "/hc admin web public-map <on|off>", "设置未登录用户是否可以看到地图。", "Controls whether guests can see the map.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web users", "/hc admin web users", "列出网页面板用户。", "Lists web panel users.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web user", "/hc admin web user <name> <admin|player> <password>", "新增或更新网页用户。", "Creates or updates a web user.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web remove", "/hc admin web remove <name>", "删除网页面板用户。", "Removes a web panel user.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web allow", "/hc admin web allow <name> <inherit|none|command...>", "设置网页用户可执行命令列表。", "Sets allowed web commands for a user.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin web execution", "/hc admin web execution <name> <on|off>", "开启或关闭指定网页用户的命令执行能力。", "Enables or disables command execution for a web user.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai", "/hc admin ai <status|enable|disable|model|base-url|key|clear-key|env|prefix|chat|npc|temperature|max-tokens|test>", "配置 OpenAI 兼容 AI 和聊天/NPC 开关。", "Configures OpenAI-compatible AI and chat/NPC toggles.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai status", "/hc admin ai status", "查看 AI 模块、模型、密钥和聊天/NPC 状态。", "Shows AI module, model, key, chat, and NPC status.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai enable", "/hc admin ai enable | /hc admin ai disable", "开启或关闭 AI 模块。", "Enables or disables the AI module.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai model", "/hc admin ai model <model>", "设置 AI 模型名称。", "Sets the AI model name.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai base-url", "/hc admin ai base-url <url>", "设置 OpenAI 兼容接口地址。", "Sets the OpenAI-compatible base URL.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai key", "/hc admin ai key <key> | /hc admin ai clear-key", "保存或清除 AI API Key。", "Saves or clears the AI API key.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai env", "/hc admin ai env <ENV_NAME>", "设置没有保存密钥时使用的环境变量名。", "Sets the API key environment variable fallback.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai prefix", "/hc admin ai prefix <prefix>", "设置聊天 AI 的触发前缀。", "Sets the chat AI trigger prefix.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai chat", "/hc admin ai chat <on|off> | /hc admin ai npc <on|off>", "开启或关闭聊天 AI 和 NPC AI。", "Enables or disables chat AI and NPC AI.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai temperature", "/hc admin ai temperature <0.0-2.0>", "设置 AI 输出随机性。", "Sets AI response randomness.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai max-tokens", "/hc admin ai max-tokens <16-4096>", "设置 AI 单次回复最大长度。", "Sets the maximum AI response token count.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin ai test", "/hc admin ai test <prompt>", "发送一次测试请求给 AI。", "Sends a test prompt to the AI provider.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin motd status", "/hc admin motd status", "查看服务器列表 MOTD 当前配置。", "Shows current server-list MOTD settings.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin motd line", "/hc admin motd line1 <text> | /hc admin motd line2 <text>", "修改服务器列表 MOTD 第一行或第二行。", "Edits the first or second server-list MOTD line.", "huntertools.command.admin"),
        entry(ADMIN, "hc admin motd max", "/hc admin motd max <number|default>", "设置服务器列表显示的最大人数。", "Sets the server-list max-player display.", "huntertools.command.admin"),

        entry(SHORTCUTS, "tps", "/tps | /htps", "显示 TPS、MSPT 和内存。", "Shows TPS, MSPT, and memory.", "huntertools.command.tps"),
        entry(SHORTCUTS, "heal", "/heal [player]", "治疗自己或指定玩家。", "Heals yourself or a target player.", "huntertools.command.heal"),
        entry(SHORTCUTS, "feed", "/feed [player]", "补满自己或指定玩家的饥饿值。", "Feeds yourself or a target player.", "huntertools.command.feed"),
        entry(SHORTCUTS, "fly", "/fly [player] [on|off]", "切换飞行状态。", "Toggles flight.", "huntertools.command.fly"),
        entry(SHORTCUTS, "gm", "/gm <survival|creative|adventure|spectator> [player]", "切换游戏模式。", "Changes game mode.", "huntertools.command.gamemode"),
        entry(SHORTCUTS, "gm aliases", "/gms | /gmc | /gma | /gmsp [player]", "快速切换生存、创造、冒险或旁观模式。", "Quickly switches survival, creative, adventure, or spectator mode.", "huntertools.command.gamemode"),
        entry(SHORTCUTS, "time", "/day [world] | /night [world]", "把世界时间切换为白天或夜晚。", "Changes world time to day or night.", "huntertools.command.time"),
        entry(SHORTCUTS, "weather", "/sun [world] | /rain [world] | /thunder [world]", "切换晴天、下雨或雷暴。", "Changes weather to clear, rain, or thunder.", "huntertools.command.weather"),
        entry(SHORTCUTS, "broadcast", "/broadcast <message> | /bc <message>", "向全服广播消息。", "Broadcasts a message.", "huntertools.command.broadcast"),
        entry(SHORTCUTS, "clearchat", "/clearchat | /cc", "清空在线玩家聊天窗口。", "Clears chat for online players.", "huntertools.command.clearchat"),
        entry(SHORTCUTS, "speed", "/speed <1-10> [player] [walk|fly]", "调整行走或飞行速度。", "Changes walk or fly speed.", "huntertools.command.speed"),
        entry(SHORTCUTS, "spawn", "/spawn [player]", "传送到服务器出生点。", "Teleports to server spawn.", "huntertools.command.spawn"),
        entry(SHORTCUTS, "setspawn", "/setspawn", "把当前位置设为服务器出生点。", "Sets the server spawn.", "huntertools.command.setspawn"),
        entry(SHORTCUTS, "back", "/back", "返回上一次记录的位置。", "Returns to the previous saved location.", "huntertools.command.back"),
        entry(SHORTCUTS, "hat", "/hat", "交换主手物品和头盔栏。", "Swaps the main-hand item and helmet slot.", "huntertools.command.hat"),
        entry(SHORTCUTS, "craft", "/craft | /workbench | /wb", "打开工作台。", "Opens a crafting table.", "huntertools.command.craft"),
        entry(SHORTCUTS, "enderchest", "/enderchest [player] | /ec [player]", "打开末影箱。", "Opens an ender chest.", "huntertools.command.enderchest"),
        entry(SHORTCUTS, "trash", "/trash | /disposal", "打开一次性垃圾箱。", "Opens a disposable trash inventory.", "huntertools.command.trash"),

        entry(TPA, "tpa", "/tpa <player>", "请求传送到玩家身边。", "Requests teleport to a player.", "huntertpa.command.tpa"),
        entry(TPA, "tpahere", "/tpahere <player>", "请求玩家传送到你身边。", "Requests a player to teleport to you.", "huntertpa.command.tpahere"),
        entry(TPA, "tpaccept", "/tpaccept [player]", "同意传送请求。", "Accepts a teleport request.", "huntertpa.command.tpaccept"),
        entry(TPA, "tpdeny", "/tpdeny [player]", "拒绝传送请求。", "Denies a teleport request.", "huntertpa.command.tpdeny"),
        entry(TPA, "tpcancel", "/tpcancel", "取消自己发出的传送请求。", "Cancels your outgoing teleport request.", "huntertpa.command.tpcancel"),
        entry(TPA, "sethome", "/sethome [name]", "设置一个家。", "Sets a named home.", "huntertpa.command.sethome"),
        entry(TPA, "home", "/home [name]", "传送到一个家。", "Teleports to a named home.", "huntertpa.command.home"),
        entry(TPA, "delhome", "/delhome [name]", "删除一个家。", "Deletes a named home.", "huntertpa.command.delhome"),
        entry(TPA, "homes", "/homes", "列出自己的家。", "Lists your homes.", "huntertpa.command.homes"),

        entry(AUTH, "register", "/register <password> <password> | /reg <password> <password>", "注册 HunterAuth 密码。", "Registers a HunterAuth password.", "hunterauth.command.register"),
        entry(AUTH, "login", "/login <password> | /l <password>", "登录 HunterAuth。", "Logs in with HunterAuth.", "hunterauth.command.login"),
        entry(AUTH, "logout", "/logout", "锁定当前登录会话。", "Locks the current auth session.", "hunterauth.command.logout"),
        entry(AUTH, "changepassword", "/changepassword <old> <new> | /changepw <old> <new>", "修改 HunterAuth 密码。", "Changes the HunterAuth password.", "hunterauth.command.changepassword"),

        entry(PLAYER, "player", "/player <help|spawn|respawn|remove|list|inv|skin|tp|tphere|look|move|sneak|sprint|jump|use|attack|stop|drop|dropstack|swap|gm|slot|ai|info|clear>", "管理真实 ServerPlayer 假人。", "Manages real ServerPlayer bots.", "huntertools.command.hplayer"),
        entry(PLAYER, "player spawn", "/player spawn <name> [world x y z [yaw pitch]]", "生成真实玩家 Bot。", "Spawns a real player bot.", "huntertools.command.hplayer"),
        entry(PLAYER, "player respawn", "/player respawn <name>", "让死亡的真实玩家 Bot 复活。", "Respawns a dead real player bot.", "huntertools.command.hplayer"),
        entry(PLAYER, "player remove", "/player remove <name>", "移除真实玩家 Bot。", "Removes a real player bot.", "huntertools.command.hplayer"),
        entry(PLAYER, "player list", "/player list", "列出真实玩家 Bot。", "Lists real player bots.", "huntertools.command.hplayer"),
        entry(PLAYER, "player inv", "/player inv <name>", "打开真实玩家 Bot 背包并直接编辑。", "Opens a real player bot inventory for editing.", "huntertools.command.hplayer"),
        entry(PLAYER, "player skin", "/player skin <name> <minecraftName|clear>", "从正版玩家名加载 Bot 皮肤，或清除皮肤。", "Loads a bot skin from an official Minecraft name, or clears it.", "huntertools.command.hplayer"),
        entry(PLAYER, "player tp", "/player tp <name> <world x y z|player>", "把 Bot 传送到坐标或玩家。", "Teleports a bot to coordinates or a player.", "huntertools.command.hplayer"),
        entry(PLAYER, "player tphere", "/player tphere <name>", "把 Bot 传送到自己身边。", "Teleports a bot to you.", "huntertools.command.hplayer"),
        entry(PLAYER, "player look", "/player look <name> <yaw> <pitch>", "设置 Bot 视角。", "Sets bot view direction.", "huntertools.command.hplayer"),
        entry(PLAYER, "player move", "/player move <name> <forward|back|left|right|stop|value> [sideways] [ticks]", "让 Bot 移动一小段。", "Moves a bot for a short duration.", "huntertools.command.hplayer"),
        entry(PLAYER, "player action", "/player sneak|sprint|jump|use|attack|stop <name> [args]", "控制 Bot 潜行、疾跑、跳跃、使用、攻击和停止。", "Controls sneaking, sprinting, jumping, using, attacking, and stopping.", "huntertools.command.hplayer"),
        entry(PLAYER, "player items", "/player drop|dropstack|swap|slot <name> [slot]", "控制 Bot 丢物品、丢整组、交换手持物或切换快捷栏。", "Controls dropping, swapping hands, and hotbar slot selection.", "huntertools.command.hplayer"),
        entry(PLAYER, "player gm", "/player gm <name> <survival|creative|adventure|spectator>", "切换 Bot 游戏模式。", "Changes bot game mode.", "huntertools.command.hplayer"),
        entry(PLAYER, "player click", "/player click <name> [command|clear]", "设置玩家点击 Bot 时执行的命令。", "Sets command run when a player clicks the bot.", "huntertools.command.hplayer"),
        entry(PLAYER, "player ai", "/player ai <name> <status|on|off|goal|once|approve|deny> [text]", "管理 Bot AI、目标和高风险动作审批。", "Controls bot AI, goals, and high-risk approvals.", "huntertools.command.hplayer"),
        entry(PLAYER, "player info", "/player info [name]", "查看 Bot 状态。", "Shows bot status.", "huntertools.command.hplayer"),
        entry(PLAYER, "player clear", "/player clear", "移除全部真实玩家 Bot。", "Removes all real player bots.", "huntertools.command.hplayer"),

        entry(NPC, "npc", "/npc <help|spawn|remove|list|skin|tp|tphere|look|pose|click|info|clear>", "管理展示 NPC；mannequin 支持玩家皮肤。", "Manages display NPCs; mannequins support player skins.", "huntertools.command.npc"),
        entry(NPC, "npc spawn", "/npc spawn <name> [villager|mannequin] [world x y z [yaw pitch]]", "生成 NPC；villager 是功能 NPC，mannequin 是可换皮肤展示 NPC。", "Spawns an NPC. Villagers are functional; mannequins are player-like displays.", "huntertools.command.npc"),
        entry(NPC, "npc remove", "/npc remove <name>", "移除 NPC。", "Removes an NPC.", "huntertools.command.npc"),
        entry(NPC, "npc list", "/npc list", "列出 NPC。", "Lists NPCs.", "huntertools.command.npc"),
        entry(NPC, "npc skin", "/npc skin <name> <minecraftName|clear>", "给 mannequin 加载正版玩家皮肤；villager 不支持玩家皮肤。", "Loads an official player skin for mannequins; villagers do not support player skins.", "huntertools.command.npc"),
        entry(NPC, "npc tp", "/npc tp <name> <world x y z|player>", "把 NPC 传送到坐标或玩家。", "Teleports an NPC to coordinates or a player.", "huntertools.command.npc"),
        entry(NPC, "npc tphere", "/npc tphere <name>", "把 NPC 传送到自己身边。", "Teleports an NPC to you.", "huntertools.command.npc"),
        entry(NPC, "npc look", "/npc look <name> <yaw> <pitch>", "设置 NPC 朝向。", "Sets NPC view direction.", "huntertools.command.npc"),
        entry(NPC, "npc pose", "/npc pose <name> <standing|sneaking|swimming|fall-flying|sleeping>", "设置 mannequin 姿势。", "Sets a mannequin pose.", "huntertools.command.npc"),
        entry(NPC, "npc click", "/npc click <name> [command|clear]", "设置点击 NPC 时执行的命令。", "Sets command run when a player clicks the NPC.", "huntertools.command.npc"),
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

    public static @NotNull List<String> coreTopics() {
        final List<String> topics = new ArrayList<>(List.of("all", CORE, ADMIN, SHORTCUTS, TPA, "teleport", "homes", AUTH, "login"));
        for (final CommandEntry entry : ENTRIES) {
            if (isPlayerOrNpc(entry.category())) {
                continue;
            }
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

    public static void sendCore(@NotNull final CommandSender sender, @Nullable final String language, @NotNull final String[] args) {
        sendCore(sender, language, args, permission -> permission == null || sender.hasPermission(permission));
    }

    public static void sendCore(
        @NotNull final CommandSender sender,
        @Nullable final String language,
        @NotNull final String[] args,
        @NotNull final Predicate<@Nullable String> permissionFilter
    ) {
        final String query = args.length == 0 ? "" : normalize(String.join(" ", args));
        if (query.isBlank()) {
            sendCoreOverview(sender, language);
            return;
        }
        if (query.equals("all")) {
            sendEntries(sender, language, ENTRIES.stream().filter(entry -> !isPlayerOrNpc(entry.category())).toList(), permissionFilter);
            return;
        }
        final String category = category(query);
        if (category != null) {
            if (isPlayerOrNpc(category)) {
                sendNoCoreTopic(sender, language, query);
                return;
            }
            sendEntries(sender, language, ENTRIES.stream().filter(entry -> entry.category().equals(category)).toList(), permissionFilter);
            return;
        }
        final List<CommandEntry> matches = ENTRIES.stream()
            .filter(entry -> !isPlayerOrNpc(entry.category()))
            .filter(entry -> entry.matches(query))
            .toList();
        if (matches.isEmpty()) {
            sendNoCoreTopic(sender, language, query);
            return;
        }
        sendEntries(sender, language, matches, permissionFilter);
    }

    private static void legacySend(
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

    public static void send(
        @NotNull final CommandSender sender,
        @Nullable final String language,
        @NotNull final String[] args,
        @NotNull final Predicate<@Nullable String> permissionFilter
    ) {
        final String query = args.length == 0 ? "" : normalize(String.join(" ", args));
        if (query.isBlank()) {
            sendFullOverview(sender, language);
            return;
        }
        if (query.equals("all")) {
            sendCleanEntries(sender, language, ENTRIES, permissionFilter);
            return;
        }
        final String category = category(query);
        if (category != null) {
            sendCleanEntries(sender, language, ENTRIES.stream().filter(entry -> entry.category().equals(category)).toList(), permissionFilter);
            return;
        }
        final List<CommandEntry> matches = ENTRIES.stream()
            .filter(entry -> entry.matches(query))
            .toList();
        if (matches.isEmpty()) {
            sendNoTopic(sender, language, query);
            return;
        }
        sendCleanEntries(sender, language, matches, permissionFilter);
    }

    private static void sendFullOverview(@NotNull final CommandSender sender, @Nullable final String language) {
        sender.sendMessage(color("&6HunterCore " + HunterLanguage.choose(language, "帮助", "Help") + " &8| &7" + HunterLanguage.normalize(language)));
        sender.sendMessage(color("&e/hc help <topic> &7- " + HunterLanguage.choose(language, "查看某类指令。", "Show command help by topic.")));
        sender.sendMessage(color("&e/player help &7- " + HunterLanguage.choose(language, "管理真实玩家 Bot。", "Manage real player bots.")));
        sender.sendMessage(color("&e/npc help &7- " + HunterLanguage.choose(language, "管理展示 NPC。", "Manage display NPCs.")));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "主题：", "Topics: ") + "core, admin, shortcuts, tpa, auth, player, npc, all"));
    }

    private static void sendNoTopic(@NotNull final CommandSender sender, @Nullable final String language, @NotNull final String query) {
        sender.sendMessage(color("&c" + HunterLanguage.choose(language, "没有找到帮助主题：", "No help topic found: ") + query));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "可用主题：", "Available topics: ")
            + "core, admin, shortcuts, tpa, auth, player, npc, all"));
    }

    private static void sendCleanEntries(
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

    private static void sendOverview(@NotNull final CommandSender sender, @Nullable final String language) {
        sender.sendMessage(color("&6HunterCore " + HunterLanguage.choose(language, "帮助", "Help") + " &8| &7" + HunterLanguage.normalize(language)));
        sender.sendMessage(color("&e/hc help <topic> &7- " + HunterLanguage.choose(language, "查看某类指令。", "Show command help by topic.")));
        sender.sendMessage(color("&e/player help &7- " + HunterLanguage.choose(language, "管理真实玩家 Bot。", "Manage real player bots.")));
        sender.sendMessage(color("&e/npc help &7- " + HunterLanguage.choose(language, "管理展示 NPC。", "Manage display NPCs.")));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "主题：", "Topics: ") + "core, admin, shortcuts, tpa, auth, player, npc, all"));
    }

    private static void sendCoreOverview(@NotNull final CommandSender sender, @Nullable final String language) {
        sender.sendMessage(color("&6HunterCore " + HunterLanguage.choose(language, "帮助", "Help") + " &8| &7" + HunterLanguage.normalize(language)));
        sender.sendMessage(color("&e/hc help <topic> &7- " + HunterLanguage.choose(language, "查看核心、后台、网页、AI、Auth 等管理指令。", "Show core, admin, web, AI, and Auth commands.")));
        sender.sendMessage(color("&e/player help &7- " + HunterLanguage.choose(language, "管理真实玩家 Bot。", "Manage real player bots.")));
        sender.sendMessage(color("&e/npc help &7- " + HunterLanguage.choose(language, "管理展示 NPC。", "Manage display NPCs.")));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "核心主题：", "Core topics: ") + "core, admin, shortcuts, tpa, auth, all"));
    }

    private static void sendNoCoreTopic(@NotNull final CommandSender sender, @Nullable final String language, @NotNull final String query) {
        sender.sendMessage(color("&c" + HunterLanguage.choose(language, "没有这个 /hc 子命令或帮助主题：", "No /hc subcommand or help topic: ") + query));
        sender.sendMessage(color("&7" + HunterLanguage.choose(language, "玩家 Bot 请使用 /player help；NPC 请使用 /npc help。", "Use /player help for player bots and /npc help for NPCs.")));
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

    private static boolean isPlayerOrNpc(@NotNull final String category) {
        return category.equals(PLAYER) || category.equals(NPC);
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
