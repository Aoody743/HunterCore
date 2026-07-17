package net.momirealms.craftengine.bukkit.entity.projectile;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import static net.momirealms.craftengine.bukkit.entity.projectile.BukkitProjectileManager.PROJECTILE_ITEM;

public final class PaperProjectileEventListener implements Listener {
    private final BukkitProjectileManager manager;

    public PaperProjectileEventListener(BukkitProjectileManager manager) {
        this.manager = manager;
    }

    // 如果物品不直接支持存储物品，使用pdc存储
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerThrowProjectile(PlayerLaunchProjectileEvent event) {
        Projectile projectile = event.getProjectile();
        ItemStack storedItem = this.manager.getItemFromProjectile(projectile, false);
        if (storedItem == null) {
            projectile.getPersistentDataContainer().set(PROJECTILE_ITEM, PersistentDataType.BYTE_ARRAY, BukkitItemManager.instance().wrap(event.getItemStack()).toBytes());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Projectile projectile) {
            this.manager.projectiles.remove(projectile.getEntityId());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Projectile projectile) {
            this.manager.handleProjectileLoad(projectile, false);
        }
    }
}
