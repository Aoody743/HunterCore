package org.huntercore.plugins.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

final class HunterTitleManager {
    private static final String MODULE = "titles";
    private static final String REGISTRY = "modules.titles.registry";
    private static final String PLAYERS = "modules.titles.players";

    private final HunterToolsPlugin plugin;
    private final HunterToolsPreferences preferences;

    HunterTitleManager(final HunterToolsPlugin plugin, final HunterToolsPreferences preferences) {
        this.plugin = plugin;
        this.preferences = preferences;
    }

    boolean enabled() {
        return this.preferences.moduleEnabled(MODULE);
    }

    boolean displayChat() {
        return this.preferences.booleanValue("modules.titles.display.chat", true);
    }

    boolean displayNametag() {
        return this.preferences.booleanValue("modules.titles.display.nametag", true);
    }

    boolean displayTab() {
        return this.preferences.booleanValue("modules.titles.display.tab", true);
    }

    List<TitleDefinition> definitions() {
        final Set<TitleDefinition> titles = new TreeSet<>(Comparator
            .comparingInt(TitleDefinition::priority).reversed()
            .thenComparing(TitleDefinition::id));
        final ConfigurationSection section = this.preferences.section(REGISTRY);
        if (section != null) {
            for (final String rawId : section.getKeys(false)) {
                final String id = HunterToolsPreferences.normalize(rawId);
                final String path = REGISTRY + "." + id;
                titles.add(new TitleDefinition(
                    id,
                    this.preferences.stringValue(path + ".display-name", id),
                    this.preferences.stringValue(path + ".prefix", "&7[" + id + "] "),
                    this.preferences.stringValue(path + ".description", ""),
                    this.preferences.intValue(path + ".priority", 0),
                    this.preferences.booleanValue(path + ".enabled", true),
                    this.preferences.stringValue(path + ".permission", "")
                ));
            }
        }
        return new ArrayList<>(titles);
    }

    @Nullable TitleDefinition definition(final String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (final TitleDefinition definition : this.definitions()) {
            if (definition.id().equalsIgnoreCase(HunterToolsPreferences.normalize(id))) {
                return definition;
            }
        }
        return null;
    }

    void saveDefinition(final TitleDefinition definition) {
        final String id = HunterToolsPreferences.normalize(definition.id());
        final String path = REGISTRY + "." + id;
        this.preferences.setValue(path + ".display-name", definition.displayName());
        this.preferences.setValue(path + ".prefix", definition.prefix());
        this.preferences.setValue(path + ".description", definition.description());
        this.preferences.setValue(path + ".priority", definition.priority());
        this.preferences.setValue(path + ".enabled", definition.enabled());
        this.preferences.setValue(path + ".permission", definition.permission());
    }

    boolean removeDefinition(final String id) {
        final String normalized = HunterToolsPreferences.normalize(id);
        if (this.definition(normalized) == null) {
            return false;
        }
        this.preferences.setValue(REGISTRY + "." + normalized, null);
        final ConfigurationSection players = this.preferences.section(PLAYERS);
        if (players != null) {
            for (final String key : players.getKeys(false)) {
                final UUID uuid = parseUuid(key);
                if (uuid == null) {
                    continue;
                }
                final PlayerTitles titles = this.playerTitles(uuid);
                if (titles.owned().remove(normalized)) {
                    this.savePlayerTitles(uuid, titles.withSanitizedActive());
                }
            }
        }
        return true;
    }

    PlayerTitles playerTitles(final UUID playerId) {
        final String path = PLAYERS + "." + playerId;
        final List<String> owned = new ArrayList<>();
        for (final String title : this.preferences.stringList(path + ".owned", List.of())) {
            final String normalized = HunterToolsPreferences.normalize(title);
            if (!normalized.isBlank()) {
                owned.add(normalized);
            }
        }
        final String active = HunterToolsPreferences.normalize(this.preferences.stringValue(path + ".active", ""));
        final boolean visible = this.preferences.booleanValue(path + ".visible", true);
        return new PlayerTitles(playerId, owned, active, visible).withSanitizedActive();
    }

    void savePlayerTitles(final UUID playerId, final PlayerTitles titles) {
        final PlayerTitles sanitized = titles.withSanitizedActive();
        final String path = PLAYERS + "." + playerId;
        this.preferences.setValue(path + ".owned", sanitized.owned());
        this.preferences.setValue(path + ".active", sanitized.activeId());
        this.preferences.setValue(path + ".visible", sanitized.visible());
    }

    boolean grantTitle(final UUID playerId, final String id) {
        final String normalized = HunterToolsPreferences.normalize(id);
        if (this.definition(normalized) == null) {
            return false;
        }
        final PlayerTitles titles = this.playerTitles(playerId);
        if (!titles.owned().contains(normalized)) {
            titles.owned().add(normalized);
        }
        if (titles.activeId().isBlank()) {
            titles.activeId(normalized);
        }
        this.savePlayerTitles(playerId, titles);
        return true;
    }

    boolean revokeTitle(final UUID playerId, final String id) {
        final String normalized = HunterToolsPreferences.normalize(id);
        final PlayerTitles titles = this.playerTitles(playerId);
        if (!titles.owned().remove(normalized)) {
            return false;
        }
        this.savePlayerTitles(playerId, titles.withSanitizedActive());
        return true;
    }

    boolean activateTitle(final UUID playerId, final String id) {
        final PlayerTitles titles = this.playerTitles(playerId);
        if (id == null || id.isBlank() || id.equalsIgnoreCase("none")) {
            titles.activeId("");
            this.savePlayerTitles(playerId, titles);
            return true;
        }
        final String normalized = HunterToolsPreferences.normalize(id);
        if (!titles.owned().contains(normalized)) {
            return false;
        }
        titles.activeId(normalized);
        this.savePlayerTitles(playerId, titles);
        return true;
    }

    void setVisible(final UUID playerId, final boolean visible) {
        final PlayerTitles titles = this.playerTitles(playerId);
        titles.visible(visible);
        this.savePlayerTitles(playerId, titles);
    }

    @Nullable TitleDefinition activeDefinition(final Player player) {
        if (!this.enabled()) {
            return null;
        }
        final PlayerTitles titles = this.playerTitles(player.getUniqueId());
        if (!titles.visible()) {
            return null;
        }
        final String activeId = titles.activeId();
        if (activeId == null || activeId.isBlank()) {
            return null;
        }
        final TitleDefinition definition = this.definition(activeId);
        if (definition == null || !definition.enabled()) {
            return null;
        }
        final String permission = definition.permission();
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            return null;
        }
        return definition;
    }

    String formatDisplayName(final Player player) {
        final TitleDefinition definition = this.activeDefinition(player);
        if (definition == null) {
            return player.getName();
        }
        return color(definition.prefix()) + ChatColor.RESET + player.getName();
    }

    String formatChatName(final Player player) {
        final TitleDefinition definition = this.activeDefinition(player);
        if (definition == null) {
            return player.getName();
        }
        return color(definition.prefix()) + ChatColor.RESET + player.getName();
    }

    void refreshPlayer(final Player player) {
        if (!this.enabled()) {
            player.setDisplayName(player.getName());
            player.setPlayerListName(player.getName());
            return;
        }
        final String formatted = this.formatDisplayName(player);
        player.setDisplayName(formatted);
        if (this.displayTab()) {
            player.setPlayerListName(formatted);
        } else {
            player.setPlayerListName(player.getName());
        }
    }

    void refreshAllPlayers() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.refreshPlayer(player);
        }
    }

    void syncScoreboard(final Scoreboard scoreboard) {
        if (scoreboard == null) {
            return;
        }
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final String teamName = "hct-" + player.getUniqueId().toString().replace("-", "").substring(0, 12);
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
            if (this.enabled() && this.displayNametag()) {
                final TitleDefinition definition = this.activeDefinition(player);
                team.setPrefix(definition == null ? "" : safePrefix(color(definition.prefix())));
            } else {
                team.setPrefix("");
            }
        }
    }

    String titlesJson() {
        final StringBuilder json = new StringBuilder(2048);
        json.append('{');
        bool(json, "enabled", this.enabled()).append(',');
        bool(json, "displayChat", this.displayChat()).append(',');
        bool(json, "displayNametag", this.displayNametag()).append(',');
        bool(json, "displayTab", this.displayTab()).append(',');
        json.append("\"definitions\":[");
        boolean first = true;
        for (final TitleDefinition definition : this.definitions()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{');
            field(json, "id", definition.id()).append(',');
            field(json, "displayName", definition.displayName()).append(',');
            field(json, "prefix", definition.prefix()).append(',');
            field(json, "description", definition.description()).append(',');
            num(json, "priority", definition.priority()).append(',');
            bool(json, "enabled", definition.enabled()).append(',');
            field(json, "permission", definition.permission());
            json.append('}');
        }
        json.append("],\"players\":[");
        first = true;
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            final PlayerTitles titles = this.playerTitles(player.getUniqueId());
            json.append('{');
            field(json, "uuid", player.getUniqueId().toString()).append(',');
            field(json, "name", player.getName()).append(',');
            json.append("\"owned\":[");
            for (int i = 0; i < titles.owned().size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                appendJson(json, titles.owned().get(i));
            }
            json.append("],");
            field(json, "active", titles.activeId()).append(',');
            bool(json, "visible", titles.visible());
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    static @Nullable UUID resolvePlayerId(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final Player online = Bukkit.getPlayerExact(value);
        if (online != null) {
            return online.getUniqueId();
        }
        final UUID uuid = parseUuid(value);
        if (uuid != null) {
            return uuid;
        }
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(value);
        return offline == null ? null : offline.getUniqueId();
    }

    private static @Nullable UUID parseUuid(final String value) {
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }

    private static String safePrefix(final String value) {
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static String color(final String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private static void appendJson(final StringBuilder json, final String value) {
        json.append('"').append(escapeJson(value)).append('"');
    }

    private static StringBuilder field(final StringBuilder json, final String key, final String value) {
        json.append('"').append(key).append("\":");
        appendJson(json, value == null ? "" : value);
        return json;
    }

    private static StringBuilder bool(final StringBuilder json, final String key, final boolean value) {
        json.append('"').append(key).append("\":").append(value);
        return json;
    }

    private static StringBuilder num(final StringBuilder json, final String key, final int value) {
        json.append('"').append(key).append("\":").append(value);
        return json;
    }

    private static String escapeJson(final String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    record TitleDefinition(
        String id,
        String displayName,
        String prefix,
        String description,
        int priority,
        boolean enabled,
        String permission
    ) {
    }

    static final class PlayerTitles {
        private final UUID playerId;
        private final List<String> owned;
        private String activeId;
        private boolean visible;

        private PlayerTitles(final UUID playerId, final List<String> owned, final String activeId, final boolean visible) {
            this.playerId = playerId;
            this.owned = new ArrayList<>(new LinkedHashSet<>(owned));
            this.activeId = activeId == null ? "" : activeId;
            this.visible = visible;
        }

        UUID playerId() {
            return this.playerId;
        }

        List<String> owned() {
            return this.owned;
        }

        String activeId() {
            return this.activeId;
        }

        void activeId(final String activeId) {
            this.activeId = activeId == null ? "" : HunterToolsPreferences.normalize(activeId);
        }

        boolean visible() {
            return this.visible;
        }

        void visible(final boolean visible) {
            this.visible = visible;
        }

        PlayerTitles withSanitizedActive() {
            if (this.activeId != null && !this.activeId.isBlank() && !this.owned.contains(this.activeId)) {
                this.activeId = this.owned.isEmpty() ? "" : this.owned.get(0);
            }
            return this;
        }
    }
}
