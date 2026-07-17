package net.momirealms.craftengine.bukkit.item.recipe;

import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.bukkit.util.ItemStackUtils;
import net.momirealms.craftengine.bukkit.util.KeyUtils;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.ItemManager;
import net.momirealms.craftengine.core.item.recipe.CustomCraftingTableRecipe;
import net.momirealms.craftengine.core.item.recipe.CustomDyeRecipe;
import net.momirealms.craftengine.core.item.recipe.Recipe;
import net.momirealms.craftengine.core.item.recipe.UniqueIdItem;
import net.momirealms.craftengine.core.item.recipe.input.CraftingInput;
import net.momirealms.craftengine.core.plugin.context.PlayerOptionalContext;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.VersionHelper;
import org.bukkit.block.BlockState;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CrafterEventListener implements Listener {
    private final ItemManager itemManager;
    private final BukkitRecipeManager recipeManager;
    private final BukkitCraftEngine plugin;

    public CrafterEventListener(BukkitCraftEngine plugin, BukkitRecipeManager recipeManager, ItemManager itemManager) {
        this.itemManager = itemManager;
        this.recipeManager = recipeManager;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCrafterCraft(CrafterCraftEvent event) {
        CraftingRecipe recipe = event.getRecipe();
        Key recipeId = KeyUtils.namespacedKeyToKey(recipe.getKey());
        Optional<Recipe> optionalRecipe = this.recipeManager.recipeById(recipeId);
        // 也许是其他插件注册的配方，直接无视
        if (optionalRecipe.isEmpty()) {
            return;
        }
        CustomCraftingTableRecipe ceRecipe = (CustomCraftingTableRecipe) optionalRecipe.get();
        if (ceRecipe.hasCondition()) {
            if (!ceRecipe.canUse(PlayerOptionalContext.of(null))) {
                event.setCancelled(true);
                return;
            }
        }
        if (ceRecipe.hasVisualResult() || ceRecipe.alwaysRebuildOutput()) {
            // 不要处理染色配方
            if (ceRecipe instanceof CustomDyeRecipe) return;
            if (ceRecipe.requiresInput()) {
                BlockState state = VersionHelper.hasPaperPatch ? event.getBlock().getState(false) : event.getBlock().getState();
                if (state instanceof Crafter crafter) {
                    Inventory inventory = crafter.getInventory();
                    event.setResult(ItemStackUtils.getBukkitStack(ceRecipe.assemble(getCraftingInput(inventory), ItemBuildContext.empty())));
                }
            } else {
                // 重新构建产物，保证papi最新
                event.setResult(ItemStackUtils.getBukkitStack(ceRecipe.assemble(null, ItemBuildContext.empty())));
            }
        }
        // 不执行functions了，估计没什么用
    }

    private CraftingInput getCraftingInput(Inventory inventory) {
        List<UniqueIdItem> uniqueIdItems = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            uniqueIdItems.add(ItemStackUtils.getUniqueIdItem(inventory.getItem(i)));
        }
        return CraftingInput.of(3, 3, uniqueIdItems);
    }
}
