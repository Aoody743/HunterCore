package net.momirealms.craftengine.bukkit.plugin.gui;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import net.momirealms.craftengine.bukkit.util.EntityUtils;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.MerchantRecipe;

import static net.momirealms.craftengine.bukkit.plugin.gui.BukkitGuiManager.CRAFT_ENGINE_MAGIC_MERCHANT_NUMBER;

public class PaperGuiEventListener implements Listener {

    // 为了修复没有经验的问题
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMerchantTrade(PlayerPurchaseEvent event) {
        MerchantRecipe trade = event.getTrade();
        if (trade.getMaxUses() == CRAFT_ENGINE_MAGIC_MERCHANT_NUMBER) {
            Player player = event.getPlayer();
            int exp = trade.getVillagerExperience();
            if (exp <= 0) return;
            EntityUtils.spawnEntity(player.getWorld(), player.getLocation(), EntityType.EXPERIENCE_ORB, entity -> {
                ExperienceOrb orb = (ExperienceOrb) entity;
                orb.setExperience(exp);
            });
        }
    }
}
