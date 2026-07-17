package net.momirealms.craftengine.bukkit.plugin.gui;

import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.ComponentUtils;
import net.momirealms.craftengine.bukkit.util.InventoryUtils;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.LegacyInventoryUtils;
import net.momirealms.craftengine.bukkit.world.WorldlyContainerHolder;
import net.momirealms.craftengine.bukkit.world.inventory.BukkitStorageContainer;
import net.momirealms.craftengine.core.item.trade.MerchantOffer;
import net.momirealms.craftengine.core.plugin.gui.*;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.inventory.CraftInventoryProxy;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.inventory.CraftMerchantCustomProxy;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.inventory.CraftMerchantProxy;
import net.momirealms.craftengine.proxy.minecraft.network.protocol.game.ClientboundOpenScreenPacketProxy;
import net.momirealms.craftengine.proxy.minecraft.world.entity.player.PlayerProxy;
import net.momirealms.craftengine.proxy.minecraft.world.inventory.AbstractContainerMenuProxy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

public final class BukkitGuiManager implements GuiManager, Listener {
    public static final int CRAFT_ENGINE_MAGIC_MERCHANT_NUMBER = 1821981731;
    private static BukkitGuiManager instance;
    private final BukkitCraftEngine plugin;
    private final PaperGuiEventListener paperGuiEventListener;

    public BukkitGuiManager(BukkitCraftEngine plugin) {
        this.plugin = plugin;
        this.paperGuiEventListener = VersionHelper.hasPaperPatch ? new PaperGuiEventListener() : null;
        instance = this;
    }

    @Override
    public void delayedInit() {
        Bukkit.getPluginManager().registerEvents(this, plugin.javaPlugin());
        if (this.paperGuiEventListener != null) Bukkit.getPluginManager().registerEvents(this.paperGuiEventListener, plugin.javaPlugin());
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        if (this.paperGuiEventListener != null) HandlerList.unregisterAll(this.paperGuiEventListener);
    }

    @Override
    public void openInventory(net.momirealms.craftengine.core.entity.player.Player player, GuiType guiType) {
        Player bukkitPlayer = (Player) player.platformPlayer();
        switch (guiType) {
            case ANVIL -> LegacyInventoryUtils.openAnvil(bukkitPlayer);
            case LOOM -> LegacyInventoryUtils.openLoom(bukkitPlayer);
            case GRINDSTONE -> LegacyInventoryUtils.openGrindstone(bukkitPlayer);
            case SMITHING -> LegacyInventoryUtils.openSmithingTable(bukkitPlayer);
            case CRAFTING -> LegacyInventoryUtils.openWorkbench(bukkitPlayer);
            case ENCHANTMENT -> LegacyInventoryUtils.openEnchanting(bukkitPlayer);
            case CARTOGRAPHY -> LegacyInventoryUtils.openCartographyTable(bukkitPlayer);
        }
    }

    @Override
    public void updateInventoryTitle(net.momirealms.craftengine.core.entity.player.Player player, Component component) {
        Object nmsPlayer = player.serverPlayer();
        Object containerMenu = PlayerProxy.INSTANCE.getContainerMenu(nmsPlayer);
        int containerId = AbstractContainerMenuProxy.INSTANCE.getContainerId(containerMenu);
        Object menuType = AbstractContainerMenuProxy.INSTANCE.getMenuType(containerMenu);
        Object packet = ClientboundOpenScreenPacketProxy.INSTANCE.newInstance(containerId, menuType, ComponentUtils.adventureToMinecraft(component));
        player.sendPacket(packet, false);
    }

    @Override
    public Inventory createInventory(Gui gui, int size) {
        CraftEngineGUIHolder holder = new CraftEngineGUIHolder(gui);
        org.bukkit.inventory.Inventory inventory = CraftInventoryProxy.INSTANCE.newInstance(this.plugin.platform().createContainer(new BukkitStorageContainer(holder, size)));
        holder.holder().bindValue(inventory);
        return new BukkitInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        org.bukkit.inventory.Inventory inventory = event.getInventory();
        if (!InventoryUtils.isCustomContainer(inventory)) return;
        if (!(InventoryUtils.getInventoryHolder(inventory) instanceof CraftEngineGUIHolder craftEngineGUIHolder)) {
            return;
        }
        AbstractGui gui = (AbstractGui) craftEngineGUIHolder.gui();
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == player.getInventory()) {
            gui.handleInventoryClick(new BukkitClick(event, gui, new BukkitInventory(player.getInventory())));
        } else if (event.getClickedInventory() == inventory) {
            gui.handleGuiClick(new BukkitClick(event, gui, new BukkitInventory(inventory)));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        org.bukkit.inventory.Inventory inventory = event.getInventory();
        if (!InventoryUtils.isCustomContainer(inventory)) return;
        if (!(InventoryUtils.getInventoryHolder(inventory) instanceof CraftEngineGUIHolder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < inventory.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // 处理自定义容器的关闭音效
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClose(InventoryCloseEvent event) {
        org.bukkit.inventory.Inventory inventory = event.getInventory();
        if (!InventoryUtils.isCustomContainer(inventory)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = InventoryUtils.getInventoryHolder(inventory);
        if (holder instanceof WorldlyContainerHolder furnitureInventoryHolder) {
            BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(player);
            if (serverPlayer == null) return;
            furnitureInventoryHolder.onClose(serverPlayer);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClose(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.Inventory inventory = player.getInventory();
        if (!InventoryUtils.isCustomContainer(inventory)) return;
        InventoryHolder holder = InventoryUtils.getInventoryHolder(inventory);
        if (holder instanceof WorldlyContainerHolder furnitureInventoryHolder) {
            BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(player);
            if (serverPlayer == null) return;
            furnitureInventoryHolder.onClose(serverPlayer);
        }
    }

    @Override
    public void openMerchant(net.momirealms.craftengine.core.entity.player.Player player, Component title, List<MerchantOffer> offers) {
        Merchant merchant = VersionHelper.isOrAbove1_21_4 ? Bukkit.createMerchant() : LegacyInventoryUtils.createMerchant();
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (MerchantOffer offer : offers) {
            MerchantRecipe merchantRecipe = new MerchantRecipe(ItemStackUtils.getBukkitStack(offer.result()), 0, CRAFT_ENGINE_MAGIC_MERCHANT_NUMBER, false, offer.xp(), 0);
            merchantRecipe.addIngredient(ItemStackUtils.getBukkitStack(offer.cost1()));
            offer.cost2().ifPresent(it -> merchantRecipe.addIngredient(ItemStackUtils.getBukkitStack(it)));
            recipes.add(merchantRecipe);
        }
        merchant.setRecipes(recipes);
        if (title != null) {
            Object minecraftMerchant = CraftMerchantProxy.INSTANCE.getMerchant(merchant);
            CraftMerchantCustomProxy.MinecraftMerchantProxy.INSTANCE.setTitle(minecraftMerchant, ComponentUtils.adventureToMinecraft(title));
        }
        LegacyInventoryUtils.openMerchant((org.bukkit.entity.Player) player.platformPlayer(), merchant);
    }

    public static BukkitGuiManager instance() {
        return instance;
    }
}
