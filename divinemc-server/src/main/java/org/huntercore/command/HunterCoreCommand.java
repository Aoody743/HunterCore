package org.huntercore.command;

import io.papermc.paper.command.CommandUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.huntercore.api.HunterCommandExtension;
import org.huntercore.api.HunterHelp;
import org.huntercore.api.HunterLanguage;
import org.huntercore.bootstrap.HunterCoreBootstrap;
import org.huntercore.bootstrap.HunterCoreRuntime;
import org.huntercore.config.HunterPreferences;
import org.huntercore.plugin.HunterBundledPluginInstaller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HunterCoreCommand extends Command {
    public static final String COMMAND_LABEL = "hc";
    public static final String BASE_PERMISSION = "huntercore.command";

    private final Map<String, HunterCommandExtension> builtIns = new LinkedHashMap<>();

    public HunterCoreCommand() {
        super(COMMAND_LABEL, "HunterCore command hub", "/hc <help|language|about|system|plugins|preferences|reload|admin>", List.of("huntercore"));
        HunterCoreBootstrap.init();
        this.setPermission(BASE_PERMISSION);
        this.registerBuiltIn(new AboutExtension());
        this.registerBuiltIn(new SystemExtension());
        this.registerBuiltIn(new PluginsExtension());
        this.registerBuiltIn(new PreferencesExtension());
        this.registerBuiltIn(new LanguageExtension());
        this.registerBuiltIn(new ReloadExtension());
        this.registerPermissions();
    }

    @Override
    public boolean execute(@NotNull final CommandSender sender, @NotNull final String commandLabel, @NotNull final String[] args) {
        if (!sender.hasPermission(BASE_PERMISSION)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return true;
        }

        if (args.length == 0) {
            this.sendHelp(sender, new String[0]);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            this.sendHelp(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }

        final HunterCommandExtension extension = this.resolve(args[0]);
        if (extension == null) {
            this.sendHelp(sender, new String[] {args[0]});
            return true;
        }

        final String permission = extension.permission();
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage(Bukkit.permissionMessage());
            return true;
        }

        return extension.execute(sender, args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NotNull List<String> tabComplete(
        @NotNull final CommandSender sender,
        @NotNull final String alias,
        @NotNull final String[] args,
        @Nullable final Location location
    ) {
        if (args.length <= 1) {
            final List<String> labels = new ArrayList<>();
            labels.add("help");
            for (final HunterCommandExtension extension : this.availableExtensions(sender)) {
                labels.add(extension.name());
                labels.addAll(extension.aliases());
            }
            return CommandUtil.getListMatchingLast(sender, args, labels);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return CommandUtil.getListMatchingLast(sender, args, HunterHelp.topics());
        }

        final HunterCommandExtension extension = this.resolve(args[0]);
        if (extension == null || !this.canUse(sender, extension)) {
            return List.of();
        }
        return extension.tabComplete(sender, args[0].toLowerCase(Locale.ROOT), Arrays.copyOfRange(args, 1, args.length));
    }

    private void sendHelp(final CommandSender sender, final String[] args) {
        HunterHelp.send(sender, language(), args);
    }

    private void registerBuiltIn(final HunterCommandExtension extension) {
        this.builtIns.put(extension.name().toLowerCase(Locale.ROOT), extension);
    }

    private HunterCommandExtension resolve(final String label) {
        final String normalized = label.toLowerCase(Locale.ROOT);
        for (final HunterCommandExtension extension : this.allExtensions()) {
            if (extension.name().equalsIgnoreCase(normalized)) {
                return extension;
            }
            for (final String alias : extension.aliases()) {
                if (alias.equalsIgnoreCase(normalized)) {
                    return extension;
                }
            }
        }
        return null;
    }

    private Collection<HunterCommandExtension> allExtensions() {
        final List<HunterCommandExtension> extensions = new ArrayList<>(this.builtIns.values());
        extensions.addAll(HunterCoreRuntime.get().commandExtensions());
        return extensions;
    }

    private Collection<HunterCommandExtension> availableExtensions(final CommandSender sender) {
        final List<HunterCommandExtension> extensions = new ArrayList<>();
        for (final HunterCommandExtension extension : this.allExtensions()) {
            if (this.canUse(sender, extension)) {
                extensions.add(extension);
            }
        }
        return extensions;
    }

    private boolean canUse(final CommandSender sender, final HunterCommandExtension extension) {
        final String permission = extension.permission();
        return permission == null || sender.hasPermission(permission);
    }

    private void registerPermissions() {
        final PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        this.addPermission(pluginManager, new Permission(BASE_PERMISSION, PermissionDefault.TRUE));
        for (final HunterCommandExtension extension : this.builtIns.values()) {
            final String permission = extension.permission();
            if (permission != null) {
                this.addPermission(pluginManager, new Permission(
                    permission,
                    permission.equals(BASE_PERMISSION + ".language") ? PermissionDefault.OP : PermissionDefault.TRUE
                ));
            }
        }
    }

    private void addPermission(final PluginManager pluginManager, final Permission permission) {
        if (pluginManager.getPermission(permission.getName()) == null) {
            pluginManager.addPermission(permission);
        }
    }

    private static String language() {
        return HunterCoreRuntime.get().language();
    }

    private static String text(final String zhCn, final String enUs) {
        return HunterLanguage.choose(language(), zhCn, enUs);
    }

    private static final class AboutExtension implements HunterCommandExtension {
        @Override
        public @NotNull String name() {
            return "about";
        }

        @Override
        public @NotNull Collection<String> aliases() {
            return List.of("info");
        }

        @Override
        public @NotNull String description() {
            return "show server about text";
        }

        @Override
        public @Nullable String permission() {
            return BASE_PERMISSION + ".about";
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            sender.sendMessage(HunterMessages.about(language()));
            return true;
        }
    }

    private static final class SystemExtension implements HunterCommandExtension {
        @Override
        public @NotNull String name() {
            return "system";
        }

        @Override
        public @NotNull Collection<String> aliases() {
            return List.of("sys");
        }

        @Override
        public @NotNull String description() {
            return "show system and runtime status";
        }

        @Override
        public @Nullable String permission() {
            return BASE_PERMISSION + ".system";
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            sender.sendMessage(HunterMessages.systemInfo(language()));
            return true;
        }
    }

    private static final class PluginsExtension implements HunterCommandExtension {
        @Override
        public @NotNull String name() {
            return "plugins";
        }

        @Override
        public @Nullable String permission() {
            return BASE_PERMISSION + ".plugins";
        }

        @Override
        public @NotNull String description() {
            return "show bundled plugin status";
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            final HunterBundledPluginInstaller.InstallReport report = HunterCoreRuntime.get().lastInstallReport();
            sender.sendMessage(text("HunterCore 内置插件 (" + report.plugins().size() + "):", "HunterCore bundled plugins (" + report.plugins().size() + "):"));
            if (report.results().isEmpty()) {
                for (final var plugin : HunterCoreRuntime.get().bundledPlugins()) {
                    sender.sendMessage("- " + plugin.name() + " " + plugin.version() + " -> " + plugin.fileName());
                }
                return true;
            }
            for (final HunterBundledPluginInstaller.InstallResult result : report.results()) {
                sender.sendMessage("- " + result.plugin().name() + " " + result.plugin().version() + ": " + result.state().name().toLowerCase(Locale.ROOT) + " (" + result.message() + ")");
            }
            return true;
        }
    }

    private static final class ReloadExtension implements HunterCommandExtension {
        @Override
        public @NotNull String name() {
            return "reload";
        }

        @Override
        public @Nullable String permission() {
            return BASE_PERMISSION + ".reload";
        }

        @Override
        public @NotNull String description() {
            return "reload HunterCore bundled plugin preferences";
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            HunterBundledPluginInstaller.install(Bukkit.getPluginsFolder().toPath());
            sender.sendMessage(text(
                "HunterCore 偏好已重载。要卸载已禁用的内置插件 jar，请重启服务器。",
                "HunterCore preferences reloaded. Restart the server to unload disabled bundled plugin jars."
            ));
            return true;
        }
    }

    private static final class LanguageExtension implements HunterCommandExtension {
        @Override
        public @NotNull String name() {
            return "language";
        }

        @Override
        public @NotNull Collection<String> aliases() {
            return List.of("lang");
        }

        @Override
        public @Nullable String permission() {
            return BASE_PERMISSION + ".language";
        }

        @Override
        public @NotNull String description() {
            return "change HunterCore command language";
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            final HunterPreferences preferences = preferences();
            if (preferences == null) {
                sender.sendMessage(text("HunterCore 偏好尚未加载。", "HunterCore preferences are not loaded yet."));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(text(
                    "当前语言：" + preferences.language() + "。用法：/hc language <zh_cn|en_us>",
                    "Current language: " + preferences.language() + ". Usage: /hc language <zh_cn|en_us>"
                ));
                return true;
            }
            final String normalized = HunterLanguage.normalizeOrNull(args[0]);
            if (normalized == null) {
                sender.sendMessage(text(
                    "未知语言。可用语言：zh_cn, en_us",
                    "Unknown language. Available languages: zh_cn, en_us"
                ));
                return true;
            }
            try {
                preferences.setLanguage(normalized);
                sender.sendMessage(HunterLanguage.choose(
                    normalized,
                    "HunterCore 语言已切换为 zh_cn。/hc help 会显示中文说明。",
                    "HunterCore language set to en_us. /hc help will show English descriptions."
                ));
            } catch (final IOException ex) {
                sender.sendMessage(HunterLanguage.choose(
                    normalized,
                    "保存语言失败：" + ex.getMessage(),
                    "Failed to save language: " + ex.getMessage()
                ));
            }
            return true;
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String alias, @NotNull final String[] args) {
            return args.length == 1 ? CommandUtil.getListMatchingLast(sender, args, HunterLanguage.supportedLanguages()) : List.of();
        }
    }

    private static final class PreferencesExtension implements HunterCommandExtension {
        private static final List<String> MODULES = List.of("tps-display", "sidebar", "motd", "essentials", "management", "fake-players", "real-fake-players", "npcs");
        private static final List<String> ESSENTIALS_COMMANDS = List.of("heal", "feed", "fly", "gm", "day", "night", "sun", "rain", "thunder", "broadcast", "clearchat", "speed", "spawn", "setspawn", "back", "hat", "craft", "enderchest", "trash");
        private static final List<String> MANAGEMENT_COMMANDS = List.of("reload", "modules", "plugins", "memory", "gc", "threads", "command", "module", "optimize", "motd");
        private static final List<String> ACTOR_COMMANDS = List.of("spawn", "remove", "list", "tp", "tphere", "look", "pose", "click", "info", "clear");
        private static final List<String> REAL_FAKE_PLAYER_COMMANDS = List.of(
            "spawn", "remove", "kill", "list", "tp", "tphere", "look", "sneak", "sprint", "jump", "use", "attack",
            "stop", "click", "drop", "dropstack", "swap", "gm", "gamemode", "slot", "info", "clear"
        );

        @Override
        public @NotNull String name() {
            return "preferences";
        }

        @Override
        public @NotNull Collection<String> aliases() {
            return List.of("prefs", "pref");
        }

        @Override
        public @NotNull String description() {
            return "view and edit core preferences";
        }

        @Override
        public @Nullable String permission() {
            return BASE_PERMISSION + ".preferences";
        }

        @Override
        public boolean execute(@NotNull final CommandSender sender, @NotNull final String label, @NotNull final String[] args) {
            final HunterPreferences preferences = preferences();
            if (preferences == null) {
                sender.sendMessage(text("HunterCore 偏好尚未加载。", "HunterCore preferences are not loaded yet."));
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                this.list(sender, preferences);
                return true;
            }
            final String sub = args[0].toLowerCase(Locale.ROOT);
            try {
                switch (sub) {
                    case "reload" -> {
                        HunterBundledPluginInstaller.install(Bukkit.getPluginsFolder().toPath());
                        sender.sendMessage(text("HunterCore 偏好已从磁盘重载。", "HunterCore preferences reloaded from disk."));
                    }
                    case "bundled" -> this.toggleBundled(sender, preferences, args);
                    case "module" -> this.toggleModule(sender, preferences, args);
                    case "command" -> this.toggleCommand(sender, preferences, args);
                    default -> HunterHelp.send(sender, language(), new String[] {"hc preferences"});
                }
            } catch (final IOException ex) {
                sender.sendMessage(text("保存 HunterCore 偏好失败：", "Failed to save HunterCore preferences: ") + ex.getMessage());
            }
            return true;
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull final CommandSender sender, @NotNull final String alias, @NotNull final String[] args) {
            if (args.length == 1) {
                return CommandUtil.getListMatchingLast(sender, args, List.of("list", "reload", "bundled", "module", "command"));
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("bundled")) {
                final HunterPreferences preferences = preferences();
                if (preferences == null) {
                    return List.of();
                }
                return CommandUtil.getListMatchingLast(sender, args, HunterCoreRuntime.get().bundledPlugins().stream().map(plugin -> plugin.id()).toList());
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("module")) {
                return CommandUtil.getListMatchingLast(sender, args, MODULES);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("command")) {
                return CommandUtil.getListMatchingLast(sender, args, List.of("essentials", "management", "fake-players", "real-fake-players", "npcs"));
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("command")) {
                final String module = args[1].toLowerCase(Locale.ROOT);
                if (module.equals("essentials")) {
                    return CommandUtil.getListMatchingLast(sender, args, ESSENTIALS_COMMANDS);
                }
                if (module.equals("management")) {
                    return CommandUtil.getListMatchingLast(sender, args, MANAGEMENT_COMMANDS);
                }
                if (module.equals("fake-players") || module.equals("npcs")) {
                    return CommandUtil.getListMatchingLast(sender, args, ACTOR_COMMANDS);
                }
                if (module.equals("real-fake-players")) {
                    return CommandUtil.getListMatchingLast(sender, args, REAL_FAKE_PLAYER_COMMANDS);
                }
            }
            if ((args.length == 3 && (args[0].equalsIgnoreCase("bundled") || args[0].equalsIgnoreCase("module")))
                || (args.length == 4 && args[0].equalsIgnoreCase("command"))) {
                return CommandUtil.getListMatchingLast(sender, args, List.of("on", "off"));
            }
            return List.of();
        }

        private void list(final CommandSender sender, final HunterPreferences preferences) {
            sender.sendMessage(text("HunterCore 偏好：", "HunterCore preferences: ") + preferences.path());
            sender.sendMessage(text("语言：", "Language: ") + preferences.language());
            sender.sendMessage(text("内置插件安装器：", "Bundled plugin installer: ") + (preferences.bundledPluginsEnabled() ? text("启用", "enabled") : text("停用", "disabled"))
                + ", update-existing=" + preferences.updateExistingBundledPlugins()
                + ", parallel-install=" + preferences.parallelBundledPluginInstall()
                + " (" + preferences.bundledPluginInstallWorkers() + " workers)");
            sender.sendMessage("CPU mode: " + preferences.stringValue("optimizations.cpu.mode", "single-thread")
                + ", experimental-region-ticking=" + preferences.booleanValue("optimizations.cpu.allow-experimental-region-ticking", false));
            sender.sendMessage(text("内置插件：", "Bundled plugins:"));
            for (final var plugin : HunterCoreRuntime.get().bundledPlugins()) {
                sender.sendMessage("- " + plugin.id() + ": " + (preferences.bundledPluginEnabled(plugin.id()) ? text("启用", "enabled") : text("停用", "disabled")));
            }
            sender.sendMessage(text("模块：", "Modules:"));
            for (final String module : MODULES) {
                sender.sendMessage("- " + module + ": " + (preferences.moduleEnabled(module) ? text("启用", "enabled") : text("停用", "disabled")));
            }
        }

        private void toggleBundled(final CommandSender sender, final HunterPreferences preferences, final String[] args) throws IOException {
            if (args.length != 3) {
                HunterHelp.send(sender, language(), new String[] {"hc preferences bundled"});
                return;
            }
            final Boolean enabled = parseToggle(args[2]);
            if (enabled == null) {
                sender.sendMessage(text("请使用 on/off。", "Use on/off."));
                return;
            }
            preferences.setBundledPluginEnabled(args[1], enabled);
            HunterBundledPluginInstaller.install(Bukkit.getPluginsFolder().toPath());
            sender.sendMessage(text("内置插件 ", "Bundled plugin ") + HunterPreferences.normalize(args[1]) + text(" 已设置为 ", " set to ") + enabled + text("。已加载 jar 需要重启后卸载。", ". Restart to unload already loaded jars."));
        }

        private void toggleModule(final CommandSender sender, final HunterPreferences preferences, final String[] args) throws IOException {
            if (args.length != 3) {
                HunterHelp.send(sender, language(), new String[] {"hc preferences module"});
                return;
            }
            final String module = HunterPreferences.normalize(args[1]);
            if (!MODULES.contains(module)) {
                sender.sendMessage(text("未知模块。可用：", "Unknown module. Available: ") + String.join(", ", MODULES));
                return;
            }
            final Boolean enabled = parseToggle(args[2]);
            if (enabled == null) {
                sender.sendMessage(text("请使用 on/off。", "Use on/off."));
                return;
            }
            preferences.setModuleEnabled(module, enabled);
            sender.sendMessage(text("模块 ", "Module ") + module + text(" 已设置为 ", " set to ") + enabled + text("。如果 HunterTools 已加载，请运行 /hc admin reload。", ". Run /hc admin reload if HunterTools is already loaded."));
        }

        private void toggleCommand(final CommandSender sender, final HunterPreferences preferences, final String[] args) throws IOException {
            if (args.length != 4) {
                HunterHelp.send(sender, language(), new String[] {"hc preferences command"});
                return;
            }
            final String module = HunterPreferences.normalize(args[1]);
            if (!module.equals("essentials") && !module.equals("management") && !module.equals("fake-players") && !module.equals("real-fake-players") && !module.equals("npcs")) {
                sender.sendMessage(text("可切换指令的模块：essentials, management, fake-players, real-fake-players, npcs。", "Command toggles are available for essentials, management, fake-players, real-fake-players, and npcs."));
                return;
            }
            final Boolean enabled = parseToggle(args[3]);
            if (enabled == null) {
                sender.sendMessage(text("请使用 on/off。", "Use on/off."));
                return;
            }
            preferences.setCommandEnabled(module, args[2], enabled);
            sender.sendMessage(text("指令 ", "Command ") + module + "." + HunterPreferences.normalize(args[2]) + text(" 已设置为 ", " set to ") + enabled + text("。需要时运行 /hc admin reload。", ". Run /hc admin reload if needed."));
        }

        private static Boolean parseToggle(final String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "on", "enable", "enabled", "true", "yes" -> Boolean.TRUE;
                case "off", "disable", "disabled", "false", "no" -> Boolean.FALSE;
                default -> null;
            };
        }
    }

    private static HunterPreferences preferences() {
        HunterPreferences preferences = HunterCoreRuntime.get().preferences();
        if (preferences == null) {
            HunterBundledPluginInstaller.install(Bukkit.getPluginsFolder().toPath());
            preferences = HunterCoreRuntime.get().preferences();
        }
        return preferences;
    }
}
