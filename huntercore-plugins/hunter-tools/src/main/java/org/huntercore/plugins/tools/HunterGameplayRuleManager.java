package org.huntercore.plugins.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class HunterGameplayRuleManager implements Listener {
    private static final List<String> TRIGGERS = List.of("sneak", "join", "chat", "interact", "break", "place");
    private static final List<String> ACTIONS = List.of("give", "drop", "take", "setblock", "message");

    private final JavaPlugin plugin;
    private final Map<String, TemporaryRule> rules = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private int nextId = 1;

    HunterGameplayRuleManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void shutdown() {
        for (final TemporaryRule rule : new ArrayList<>(this.rules.values())) {
            this.remove(rule.id(), false);
        }
        this.rules.clear();
        this.cooldowns.clear();
    }

    String summary() {
        if (this.rules.isEmpty()) {
            return "none";
        }
        final List<String> lines = new ArrayList<>();
        for (final TemporaryRule rule : this.rules.values()) {
            lines.add(rule.type() + ":" + rule.id() + "(" + rule.label() + ")");
            if (lines.size() >= 8) {
                break;
            }
        }
        return String.join(", ", lines);
    }

    String applyAiRule(final String args) {
        final Map<String, String> spec = parseSpec(args);
        final String op = firstNonBlank(spec.get("op"), spec.get("cmd"), firstValue(args));
        if (List.of("clear", "restore", "reset").contains(normalize(op))) {
            return this.clear();
        }
        if (List.of("remove", "delete", "del").contains(normalize(op))) {
            return this.remove(firstNonBlank(spec.get("id"), secondValue(args)));
        }
        final Review review = this.review(spec);
        if (!review.ok()) {
            return "rule rejected: " + review.message();
        }
        if (review.recipe()) {
            return "review ok; " + this.addShapelessRecipe(
                spec.get("result"),
                intValue(spec.get("amount"), 1, 1, 64),
                splitList(firstNonBlank(spec.get("ingredients"), spec.get("ingredient"), spec.get("items"))),
                durationSeconds(spec.get("duration"), 300)
            );
        }
        final String id = this.nextId("rule");
        final TemporaryRule rule = new TemporaryRule(
            id,
            "rule",
            normalize(spec.get("trigger")),
            normalize(spec.get("action")),
            this.ruleLabel(spec),
            null,
            parseMaterial(spec.get("material")),
            intValue(spec.get("amount"), 1, 1, 64),
            intValue(firstNonBlank(spec.get("cooldown"), spec.get("cooldown-seconds")), 3, 0, 3600),
            spec.getOrDefault("message", ""),
            intValue(spec.get("dx"), 0, -16, 16),
            intValue(spec.get("dy"), 0, -16, 16),
            intValue(spec.get("dz"), 0, -16, 16),
            firstNonBlank(spec.get("contains"), spec.get("text")),
            null,
            new ArrayList<>()
        );
        this.rules.put(id, rule);
        this.scheduleExpiry(id, durationSeconds(spec.get("duration"), 300));
        return "review ok; rule " + id + " added: " + rule.trigger() + " -> " + rule.action() + " " + rule.label();
    }

    String addSneakGive(final String materialName, final int amount, final int cooldownSeconds, final int durationSeconds) {
        final Material material = parseMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            return "invalid material " + materialName;
        }
        final String id = this.nextId("sneak-give");
        final TemporaryRule rule = new TemporaryRule(
            id,
            "sneak-give",
            "sneak",
            "give",
            material.key().value() + "x" + Math.max(1, amount),
            null,
            material,
            Math.max(1, Math.min(64, amount)),
            Math.max(1, cooldownSeconds),
            "",
            0,
            0,
            0,
            "",
            null,
            new ArrayList<>()
        );
        this.rules.put(id, rule);
        this.scheduleExpiry(id, durationSeconds);
        return "rule " + id + " added: sneak gives " + rule.label() + this.durationSuffix(durationSeconds);
    }

    String addShapelessRecipe(final String resultName, final int amount, final List<String> ingredientNames, final int durationSeconds) {
        final Material result = parseMaterial(resultName);
        if (result == null || result.isAir() || !result.isItem()) {
            return "invalid recipe result " + resultName;
        }
        if (ingredientNames.isEmpty() || ingredientNames.size() > 9) {
            return "recipe needs 1-9 ingredients";
        }
        final List<Material> ingredients = new ArrayList<>();
        for (final String ingredientName : ingredientNames) {
            final Material material = parseMaterial(ingredientName);
            if (material == null || material.isAir() || !material.isItem()) {
                return "invalid ingredient " + ingredientName;
            }
            ingredients.add(material);
        }
        final String id = this.nextId("recipe");
        final NamespacedKey key = new NamespacedKey(this.plugin, id);
        final ShapelessRecipe recipe = new ShapelessRecipe(key, new ItemStack(result, Math.max(1, Math.min(64, amount))));
        for (final Material ingredient : ingredients) {
            recipe.addIngredient(ingredient);
        }
        if (!Bukkit.addRecipe(recipe)) {
            return "failed to add recipe " + id;
        }
        final TemporaryRule rule = new TemporaryRule(
            id,
            "recipe",
            "craft",
            "recipe",
            result.key().value() + "x" + Math.max(1, Math.min(64, amount)),
            key,
            null,
            0,
            0,
            "",
            0,
            0,
            0,
            "",
            null,
            new ArrayList<>()
        );
        this.rules.put(id, rule);
        this.scheduleExpiry(id, durationSeconds);
        return "rule " + id + " added: shapeless recipe " + rule.label() + this.durationSuffix(durationSeconds);
    }

    String remove(final String id) {
        return this.remove(id, true);
    }

    String clear() {
        int count = 0;
        for (final String id : new ArrayList<>(this.rules.keySet())) {
            this.remove(id, false);
            count++;
        }
        return "cleared " + count + " gameplay rules";
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(final PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        this.runTrigger("sneak", event.getPlayer(), null, "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onJoin(final PlayerJoinEvent event) {
        this.runTrigger("join", event.getPlayer(), null, "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        this.runTrigger("interact", event.getPlayer(), event.getClickedBlock(), "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        this.runTrigger("break", event.getPlayer(), event.getBlock(), "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        this.runTrigger("place", event.getPlayer(), event.getBlockPlaced(), "");
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        if (this.rules.values().stream().noneMatch(rule -> rule.trigger().equals("chat"))) {
            return;
        }
        final Player player = event.getPlayer();
        final String message = event.getMessage();
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.runTrigger("chat", player, null, message));
    }

    private void runTrigger(final String trigger, final Player player, final Block eventBlock, final String message) {
        final long now = System.currentTimeMillis();
        for (final TemporaryRule rule : this.rules.values()) {
            if (!rule.trigger().equals(trigger)) {
                continue;
            }
            if (!rule.contains().isBlank() && !message.toLowerCase(Locale.ROOT).contains(rule.contains().toLowerCase(Locale.ROOT))) {
                continue;
            }
            final String cooldownKey = rule.id() + ":" + player.getUniqueId();
            if (this.cooldowns.getOrDefault(cooldownKey, 0L) > now) {
                continue;
            }
            this.applyRuleAction(rule, player, eventBlock);
            this.cooldowns.put(cooldownKey, now + (rule.cooldownSeconds() * 1000L));
        }
    }

    private void applyRuleAction(final TemporaryRule rule, final Player player, final Block eventBlock) {
        switch (rule.action()) {
            case "give" -> {
                if (rule.material() != null) {
                    player.getInventory().addItem(new ItemStack(rule.material(), rule.amount()));
                }
            }
            case "drop" -> {
                if (rule.material() != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), new ItemStack(rule.material(), rule.amount()));
                }
            }
            case "take" -> {
                if (rule.material() != null) {
                    player.getInventory().removeItem(new ItemStack(rule.material(), rule.amount()));
                }
            }
            case "setblock" -> {
                if (rule.material() == null || !rule.material().isBlock()) {
                    return;
                }
                final Block target = eventBlock == null
                    ? player.getLocation().clone().add(rule.dx(), rule.dy(), rule.dz()).getBlock()
                    : eventBlock.getRelative(rule.dx(), rule.dy(), rule.dz());
                rule.blockChanges().add(new BlockSnapshot(target.getLocation(), target.getBlockData()));
                target.setType(rule.material(), true);
            }
            case "message" -> {
                if (!rule.message().isBlank()) {
                    player.sendMessage(rule.message().replace("%player%", player.getName()));
                }
            }
            default -> {
            }
        }
    }

    private String remove(final String id, final boolean reportMissing) {
        final TemporaryRule rule = this.rules.remove(HunterToolsPreferences.normalize(id));
        if (rule == null) {
            return reportMissing ? "rule not found: " + id : "";
        }
        if (rule.expiryTask() != null) {
            rule.expiryTask().cancel();
        }
        if (rule.recipeKey() != null) {
            Bukkit.removeRecipe(rule.recipeKey());
        }
        for (int index = rule.blockChanges().size() - 1; index >= 0; index--) {
            final BlockSnapshot snapshot = rule.blockChanges().get(index);
            if (snapshot.location().getWorld() != null) {
                snapshot.location().getBlock().setBlockData(snapshot.blockData(), true);
            }
        }
        final String prefix = rule.id() + ":";
        final Iterator<String> iterator = this.cooldowns.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove();
            }
        }
        return "rule " + rule.id() + " removed";
    }

    private void scheduleExpiry(final String id, final int durationSeconds) {
        final int seconds = Math.max(0, Math.min(86_400, durationSeconds));
        if (seconds <= 0) {
            return;
        }
        final TemporaryRule rule = this.rules.get(id);
        if (rule == null) {
            return;
        }
        final BukkitTask task = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.remove(id, false), seconds * 20L);
        this.rules.put(id, rule.withExpiryTask(task));
    }

    private String nextId(final String prefix) {
        return prefix + "-" + this.nextId++;
    }

    private String durationSuffix(final int durationSeconds) {
        return durationSeconds > 0 ? " for " + durationSeconds + "s" : "";
    }

    private static Material parseMaterial(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
    }

    private Review review(final Map<String, String> spec) {
        final String action = normalize(firstNonBlank(spec.get("action"), spec.get("type")));
        if (List.of("recipe", "crafting", "craft").contains(action)) {
            final Material result = parseMaterial(spec.get("result"));
            final List<String> ingredients = splitList(firstNonBlank(spec.get("ingredients"), spec.get("ingredient"), spec.get("items")));
            if (result == null || result.isAir() || !result.isItem()) {
                return Review.rejected("invalid recipe result");
            }
            if (ingredients.isEmpty() || ingredients.size() > 9) {
                return Review.rejected("recipe needs 1-9 ingredients");
            }
            for (final String ingredient : ingredients) {
                final Material material = parseMaterial(ingredient);
                if (material == null || material.isAir() || !material.isItem()) {
                    return Review.rejected("invalid ingredient " + ingredient);
                }
            }
            return Review.acceptedRecipe();
        }
        final String trigger = normalize(spec.get("trigger"));
        if (!TRIGGERS.contains(trigger)) {
            return Review.rejected("unsupported trigger " + trigger);
        }
        if (!ACTIONS.contains(action)) {
            return Review.rejected("unsupported action " + action);
        }
        if (List.of("give", "drop", "take", "setblock").contains(action)) {
            final Material material = parseMaterial(spec.get("material"));
            if (material == null || material.isAir()) {
                return Review.rejected("invalid material");
            }
            if (action.equals("setblock") && !material.isBlock()) {
                return Review.rejected("setblock material must be a block");
            }
            if (!action.equals("setblock") && !material.isItem()) {
                return Review.rejected("item action material must be an item");
            }
        }
        if (action.equals("message") && firstNonBlank(spec.get("message")).isBlank()) {
            return Review.rejected("message action needs message");
        }
        return Review.accepted();
    }

    private String ruleLabel(final Map<String, String> spec) {
        final String action = normalize(spec.get("action"));
        if (action.equals("message")) {
            return "message";
        }
        final Material material = parseMaterial(spec.get("material"));
        return material == null ? action : material.key().value() + "x" + intValue(spec.get("amount"), 1, 1, 64);
    }

    private static Map<String, String> parseSpec(final String args) {
        final Map<String, String> spec = new HashMap<>();
        for (final String token : tokens(args)) {
            final int equals = token.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            spec.put(normalize(token.substring(0, equals)), token.substring(equals + 1).trim());
        }
        final String first = firstValue(args);
        if (!first.isBlank() && first.contains(":")) {
            final String[] parts = first.split(":", 2);
            spec.putIfAbsent("trigger", parts[0]);
            spec.putIfAbsent("action", parts[1]);
        }
        spec.putIfAbsent("action", first);
        return spec;
    }

    private static List<String> splitList(final String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[+;,|]"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private static int intValue(final String raw, final int fallback, final int min, final int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim().replaceAll("[^0-9-]", ""))));
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }

    private static int durationSeconds(final String raw, final int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        final String value = raw.trim().toLowerCase(Locale.ROOT);
        final int multiplier;
        final String number;
        if (value.endsWith("h")) {
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
            return Math.max(1, Math.min(86_400, Integer.parseInt(number.trim()) * multiplier));
        } catch (final NumberFormatException ex) {
            return fallback;
        }
    }

    private static List<String> tokens(final String args) {
        if (args == null || args.isBlank()) {
            return List.of();
        }
        return Arrays.stream(args.replace(',', ' ').split("\\s+"))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private static String firstValue(final String args) {
        for (final String token : tokens(args)) {
            if (!token.contains("=")) {
                return token;
            }
        }
        return "";
    }

    private static String secondValue(final String args) {
        boolean first = false;
        for (final String token : tokens(args)) {
            if (token.contains("=")) {
                continue;
            }
            if (!first) {
                first = true;
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

    private static String normalize(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', '-').trim();
    }

    private record Review(boolean ok, boolean recipe, String message) {
        private static Review accepted() {
            return new Review(true, false, "");
        }

        private static Review acceptedRecipe() {
            return new Review(true, true, "");
        }

        private static Review rejected(final String message) {
            return new Review(false, false, message);
        }
    }

    private record BlockSnapshot(Location location, BlockData blockData) {
    }

    private record TemporaryRule(
        String id,
        String type,
        String trigger,
        String action,
        String label,
        NamespacedKey recipeKey,
        Material material,
        int amount,
        int cooldownSeconds,
        String message,
        int dx,
        int dy,
        int dz,
        String contains,
        BukkitTask expiryTask,
        List<BlockSnapshot> blockChanges
    ) {
        private TemporaryRule withExpiryTask(final BukkitTask task) {
            return new TemporaryRule(
                this.id, this.type, this.trigger, this.action, this.label, this.recipeKey, this.material, this.amount,
                this.cooldownSeconds, this.message, this.dx, this.dy, this.dz, this.contains, task, this.blockChanges
            );
        }
    }
}
