package org.huntercore.plugins.tools;

import static org.huntercore.plugins.tools.HunterCommandSyntax.matching;

import java.util.List;
import java.util.Locale;

final class HunterAdminCompletions {
    private static final String ESSENTIALS = "essentials";
    private static final String MANAGEMENT = "management";
    private static final String FAKE_PLAYERS = "fake-players";
    private static final String REAL_FAKE_PLAYERS = "real-fake-players";
    private static final String NPCS = "npcs";

    private HunterAdminCompletions() {
    }

    static List<String> complete(
        final String[] args,
        final List<String> modules,
        final List<String> essentialsCommands,
        final List<String> managementCommands,
        final List<String> actorCommands,
        final List<String> realFakePlayerCommands,
        final List<String> helpTopics
    ) {
        if (args.length == 1) {
            return matching(args[0], List.of("help", "reload", "modules", "module", "command", "plugins", "memory", "gc", "threads", "optimize", "ncr", "nochatreports", "chatreports", "motd", "web", "ai"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return matching(args[1], helpTopics);
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
            return matching(args[1], modules);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("command")) {
            return matching(args[1], List.of(ESSENTIALS, MANAGEMENT, FAKE_PLAYERS, REAL_FAKE_PLAYERS, NPCS));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("command")) {
            final String module = args[1].toLowerCase(Locale.ROOT);
            if (module.equals(ESSENTIALS)) {
                return matching(args[2], essentialsCommands);
            }
            if (module.equals(MANAGEMENT)) {
                return matching(args[2], managementCommands);
            }
            if (module.equals(FAKE_PLAYERS) || module.equals(NPCS)) {
                return matching(args[2], actorCommands);
            }
            if (module.equals(REAL_FAKE_PLAYERS)) {
                return matching(args[2], realFakePlayerCommands);
            }
        }
        if ((args.length == 3 && args[0].equalsIgnoreCase("module")) || (args.length == 4 && args[0].equalsIgnoreCase("command"))) {
            return matching(args[args.length - 1], List.of("on", "off"));
        }
        return List.of();
    }
}
