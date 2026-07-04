package org.huntercore.plugins.assets;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class HunterAssetsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int GUI_PAGE_SIZE = 45;
    private final Map<String, CustomAssetItem> items = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadState();
        Bukkit.getPluginManager().registerEvents(this, this);
        final PluginCommand command = getCommand("hunterassets");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openAssetsMenu(player, 0);
            } else {
                sender.sendMessage(color(text("可用: /hunterassets gui|reload|list|give|sendpack", "Available: /hunterassets gui|reload|list|give|sendpack")));
            }
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu" -> openMenuCommand(sender, args);
            case "reload" -> reloadCommand(sender);
            case "list" -> listCommand(sender);
            case "give" -> giveCommand(sender, args);
            case "sendpack" -> sendPackCommand(sender, args);
            default -> {
                sender.sendMessage(color(text("未知子命令。", "Unknown subcommand.")));
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            return filterPrefix(List.of("gui", "reload", "list", "give", "sendpack"), args[0]);
        }
        if (args.length == 2 && ("give".equalsIgnoreCase(args[0]) || "sendpack".equalsIgnoreCase(args[0]))) {
            return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            return filterPrefix(new ArrayList<>(items.keySet()), args[2]);
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        if (getConfig().getBoolean("resource-pack.enabled") && getConfig().getBoolean("resource-pack.send-on-join")) {
            applyResourcePack(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof final Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof AssetsMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) {
            return;
        }
        final int slot = event.getRawSlot();
        if (slot == 45 && holder.page() > 0) {
            openAssetsMenu(player, holder.page() - 1);
            return;
        }
        if (slot == 49) {
            if (applyResourcePack(player)) {
                player.sendMessage(color(text("&a已重新发送 HunterAssets 资源包。", "&aHunterAssets resource pack resent.")));
            } else {
                player.sendMessage(color(text("&e资源包未启用或配置不完整。", "&eResource pack is disabled or incomplete.")));
            }
            return;
        }
        if (slot == 50 && player.hasPermission("hunterassets.admin")) {
            reloadConfig();
            reloadState();
            openAssetsMenu(player, holder.page());
            player.sendMessage(color(text("&aHunterAssets 配置已重载。", "&aHunterAssets configuration reloaded.")));
            return;
        }
        if (slot == 53 && (holder.page() + 1) * GUI_PAGE_SIZE < items.size()) {
            openAssetsMenu(player, holder.page() + 1);
            return;
        }
        if (slot < 0 || slot >= GUI_PAGE_SIZE) {
            return;
        }
        final CustomAssetItem customItem = holder.entries().get(slot);
        if (customItem == null) {
            return;
        }
        if (!player.hasPermission("hunterassets.give")) {
            player.sendMessage(color(text("&c你没有领取自定义物品的权限。", "&cYou do not have permission to take custom items.")));
            return;
        }
        final int amount = event.isShiftClick() ? Math.max(customItem.amount(), 16) : customItem.amount();
        player.getInventory().addItem(createItem(customItem, amount));
        player.sendMessage(color(text("&a已领取物品: ", "&aReceived item: ") + customItem.id()));
    }

    private boolean openMenuCommand(final CommandSender sender, final String[] args) {
        final Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("hunterassets.admin")) {
                sender.sendMessage(color(text("&c你没有权限。", "&cYou do not have permission.")));
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(color(text("&e用法: /hunterassets gui <玩家>", "&eUsage: /hunterassets gui <player>")));
            return true;
        }
        if (target == null) {
            sender.sendMessage(color(text("&c找不到该玩家。", "&cPlayer not found.")));
            return true;
        }
        openAssetsMenu(target, 0);
        if (!target.equals(sender)) {
            sender.sendMessage(color(text("&a已为玩家打开 HunterAssets 菜单。", "&aOpened the HunterAssets menu for that player.")));
        }
        return true;
    }

    private boolean reloadCommand(final CommandSender sender) {
        if (!sender.hasPermission("hunterassets.admin")) {
            sender.sendMessage(color(text("&c你没有权限。", "&cYou do not have permission.")));
            return true;
        }
        reloadConfig();
        reloadState();
        sender.sendMessage(color(text("&aHunterAssets 配置已重载。", "&aHunterAssets configuration reloaded.")));
        return true;
    }

    private boolean listCommand(final CommandSender sender) {
        if (items.isEmpty()) {
            sender.sendMessage(color(text("&e当前没有自定义物品。", "&eThere are no custom items yet.")));
            return true;
        }
        sender.sendMessage(color(text("&6HunterAssets 物品: ", "&6HunterAssets items: ") + String.join(", ", items.keySet())));
        return true;
    }

    private boolean giveCommand(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("hunterassets.give")) {
            sender.sendMessage(color(text("&c你没有权限。", "&cYou do not have permission.")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(color(text("&e用法: /hunterassets give <玩家> <物品> [数量]", "&eUsage: /hunterassets give <player> <item> [amount]")));
            return true;
        }
        final Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color(text("&c找不到该玩家。", "&cPlayer not found.")));
            return true;
        }
        final CustomAssetItem customItem = items.get(args[2].toLowerCase(Locale.ROOT));
        if (customItem == null) {
            sender.sendMessage(color(text("&c找不到该物品。", "&cUnknown item.")));
            return true;
        }
        int amount = customItem.amount();
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (final NumberFormatException ignored) {
                sender.sendMessage(color(text("&c数量格式错误。", "&cInvalid amount.")));
                return true;
            }
        }
        final ItemStack stack = createItem(customItem, amount);
        target.getInventory().addItem(stack);
        sender.sendMessage(color(text("&a已给予物品: ", "&aGave item: ") + customItem.id() + " -> " + target.getName()));
        return true;
    }

    private boolean sendPackCommand(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("hunterassets.admin")) {
            sender.sendMessage(color(text("&c你没有权限。", "&cYou do not have permission.")));
            return true;
        }
        final Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(color(text("&e用法: /hunterassets sendpack <玩家>", "&eUsage: /hunterassets sendpack <player>")));
            return true;
        }
        if (target == null) {
            sender.sendMessage(color(text("&c找不到该玩家。", "&cPlayer not found.")));
            return true;
        }
        if (!applyResourcePack(target)) {
            sender.sendMessage(color(text("&c资源包未启用或配置不完整。", "&cResource pack is disabled or incomplete.")));
            return true;
        }
        sender.sendMessage(color(text("&a已发送资源包到 ", "&aSent resource pack to ") + target.getName()));
        return true;
    }

    private void openAssetsMenu(final Player player, final int page) {
        final List<CustomAssetItem> allItems = new ArrayList<>(items.values());
        final int safePage = Math.max(0, page);
        final int pageCount = Math.max(1, (int) Math.ceil(allItems.size() / (double) GUI_PAGE_SIZE));
        final int currentPage = Math.min(safePage, pageCount - 1);
        final Inventory inventory = Bukkit.createInventory(new AssetsMenuHolder(currentPage), 54, color(text("&3HunterAssets · 资源与物品", "&3HunterAssets · Assets & Items")));
        final int fromIndex = currentPage * GUI_PAGE_SIZE;
        final int toIndex = Math.min(allItems.size(), fromIndex + GUI_PAGE_SIZE);
        for (int index = fromIndex; index < toIndex; index++) {
            final int slot = index - fromIndex;
            final CustomAssetItem customItem = allItems.get(index);
            inventory.setItem(slot, createMenuItem(customItem));
            ((AssetsMenuHolder) inventory.getHolder()).entries().put(slot, customItem);
        }
        inventory.setItem(45, menuButton(Material.ARROW, text("&b上一页", "&bPrevious"), List.of(text("&7返回上一页物品", "&7Go to the previous page"))));
        inventory.setItem(49, menuButton(Material.FILLED_MAP, text("&b发送资源包", "&bSend Resource Pack"), List.of(
            text("&7点击向自己重新发送资源包", "&7Resend the resource pack to yourself"),
            text("&7Shift 点击物品可领取更多数量", "&7Shift-click item entries for more quantity")
        )));
        if (player.hasPermission("hunterassets.admin")) {
            inventory.setItem(50, menuButton(Material.REPEATER, text("&6重载配置", "&6Reload Config"), List.of(text("&7重新加载 HunterAssets 配置", "&7Reload HunterAssets configuration"))));
        }
        inventory.setItem(53, menuButton(Material.ARROW, text("&b下一页", "&bNext"), List.of(text("&7查看下一页物品", "&7Go to the next page"))));
        player.openInventory(inventory);
    }

    private ItemStack createMenuItem(final CustomAssetItem customItem) {
        final ItemStack stack = createItem(customItem, Math.max(1, customItem.amount()));
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            final List<String> lore = new ArrayList<>(Objects.requireNonNullElse(meta.getLore(), List.of()));
            lore.add(color(text("&8ID: &f", "&8ID: &f") + customItem.id()));
            lore.add(color(text("&8CMD: &f", "&8CMD: &f") + customItem.customModelData()));
            lore.add(color(text("&7点击领取默认数量", "&7Click to receive the default amount")));
            lore.add(color(text("&7Shift 点击领取更多", "&7Shift-click to receive more")));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack menuButton(final Material material, final String name, final List<String> lore) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(HunterAssetsPlugin::color).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void reloadState() {
        items.clear();
        final ConfigurationSection section = getConfig().getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            final Material material = Material.matchMaterial(itemSection.getString("material", "PAPER"));
            if (material == null || material.isAir()) {
                getLogger().warning("Skipping HunterAssets item " + key + " because material is invalid.");
                continue;
            }
            items.put(key.toLowerCase(Locale.ROOT), new CustomAssetItem(
                key,
                material,
                Math.max(1, itemSection.getInt("amount", 1)),
                itemSection.getInt("custom-model-data", 0),
                itemSection.getString("name-zh-cn", key),
                itemSection.getString("name-en-us", key),
                itemSection.getStringList("lore-zh-cn"),
                itemSection.getStringList("lore-en-us")
            ));
        }
    }

    private ItemStack createItem(final CustomAssetItem customItem, final int amount) {
        final ItemStack stack = new ItemStack(customItem.material(), Math.max(1, amount));
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(select(customItem.nameZhCn(), customItem.nameEnUs())));
            final List<String> lore = selectList(customItem.loreZhCn(), customItem.loreEnUs()).stream()
                .map(HunterAssetsPlugin::color)
                .toList();
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            if (customItem.customModelData() > 0) {
                meta.setCustomModelData(customItem.customModelData());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean applyResourcePack(final Player player) {
        final String url = getConfig().getString("resource-pack.url", "").trim();
        if (!getConfig().getBoolean("resource-pack.enabled") || url.isBlank()) {
            return false;
        }
        try {
            URI.create(url);
        } catch (final IllegalArgumentException ex) {
            getLogger().warning("HunterAssets resource-pack.url is invalid: " + url);
            return false;
        }
        final byte[] hash = sha1Bytes(getConfig().getString("resource-pack.sha1", "").trim());
        final String prompt = color(select(
            getConfig().getString("resource-pack.prompt-zh-cn", ""),
            getConfig().getString("resource-pack.prompt-en-us", "")
        ));
        player.setResourcePack(url, hash, prompt, getConfig().getBoolean("resource-pack.required"));
        return true;
    }

    private byte[] sha1Bytes(final String sha1) {
        if (sha1.isBlank()) {
            return new byte[0];
        }
        final String normalized = sha1.replace(" ", "").toLowerCase(Locale.ROOT);
        if (normalized.length() != 40) {
            return new byte[0];
        }
        final byte[] bytes = new byte[20];
        for (int i = 0; i < 20; i++) {
            final int index = i * 2;
            try {
                bytes[i] = (byte) Integer.parseInt(normalized.substring(index, index + 2), 16);
            } catch (final NumberFormatException ex) {
                return new byte[0];
            }
        }
        return bytes;
    }

    private String select(final String zhCn, final String enUs) {
        return isZhCn() ? zhCn : enUs;
    }

    private List<String> selectList(final List<String> zhCn, final List<String> enUs) {
        return isZhCn() ? zhCn : enUs;
    }

    private boolean isZhCn() {
        return getConfig().getString("language", "zh_cn").toLowerCase(Locale.ROOT).startsWith("zh");
    }

    private String text(final String zhCn, final String enUs) {
        return isZhCn() ? zhCn : enUs;
    }

    private static String color(final String input) {
        return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNullElse(input, ""));
    }

    private static List<String> filterPrefix(final List<String> candidates, final String prefix) {
        final String needle = prefix.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }

    private record CustomAssetItem(
        String id,
        Material material,
        int amount,
        int customModelData,
        String nameZhCn,
        String nameEnUs,
        List<String> loreZhCn,
        List<String> loreEnUs
    ) {
        private CustomAssetItem {
            loreZhCn = List.copyOf(loreZhCn == null ? List.of() : loreZhCn);
            loreEnUs = List.copyOf(loreEnUs == null ? List.of() : loreEnUs);
        }
    }

    private static final class AssetsMenuHolder implements InventoryHolder {
        private final int page;
        private final Map<Integer, CustomAssetItem> entries = new LinkedHashMap<>();

        private AssetsMenuHolder(final int page) {
            this.page = page;
        }

        private int page() {
            return this.page;
        }

        private Map<Integer, CustomAssetItem> entries() {
            return this.entries;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
