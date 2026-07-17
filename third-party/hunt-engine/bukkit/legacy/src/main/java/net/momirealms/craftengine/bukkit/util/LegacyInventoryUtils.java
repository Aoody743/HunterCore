package net.momirealms.craftengine.bukkit.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Merchant;
import org.jetbrains.annotations.Nullable;

public final class LegacyInventoryUtils {
    private LegacyInventoryUtils() {}

    public static Inventory getTopInventory(Player player) {
        return player.getOpenInventory().getTopInventory();
    }

    public static void setRepairCost(AnvilInventory anvilInventory, int repairCost) {
        anvilInventory.setRepairCost(repairCost);
    }

    public static void setRepairCostAmount(AnvilInventory anvilInventory, int amount) {
        anvilInventory.setRepairCostAmount(amount);
    }

    @Nullable
    public static String getRenameText(AnvilInventory anvilInventory) {
        return anvilInventory.getRenameText();
    }

    public static int getMaxRepairCost(AnvilInventory anvilInventory) {
        return anvilInventory.getMaximumRepairCost();
    }

    public static int getRepairCost(AnvilInventory anvilInventory) {
        return anvilInventory.getRepairCost();
    }

    public static InventoryView getView(PrepareAnvilEvent event) {
        return event.getView();
    }

    public static void openAnvil(Player player) {
        player.openAnvil(null, true);
    }

    public static void openCartographyTable(Player player) {
        player.openCartographyTable(null, true);
    }

    public static void openEnchanting(Player player) {
        player.openEnchanting(null, true);
    }

    public static void openGrindstone(Player player) {
        player.openGrindstone(null, true);
    }

    public static void openLoom(Player player) {
        player.openLoom(null, true);
    }

    public static void openSmithingTable(Player player) {
        player.openSmithingTable(null, true);
    }

    public static void openWorkbench(Player player) {
        player.openWorkbench(null, true);
    }

    public static void openMerchant(Player player, Merchant merchant) {
        player.openMerchant(merchant, true);
    }

    @SuppressWarnings("deprecation")
    public static Merchant createMerchant() {
        return Bukkit.createMerchant("Villager");
    }

    public static Player getPlayerFromInventoryEvent(InventoryEvent event) {
        return (Player) event.getView().getPlayer();
    }

    public static boolean isHotBarSwapAndReadd(InventoryAction action) {
        return action == InventoryAction.HOTBAR_MOVE_AND_READD;
    }
}
