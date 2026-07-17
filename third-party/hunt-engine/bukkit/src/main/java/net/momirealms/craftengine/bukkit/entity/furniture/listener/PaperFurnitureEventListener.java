package net.momirealms.craftengine.bukkit.entity.furniture.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurnitureManager;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class PaperFurnitureEventListener implements Listener {
    private final BukkitFurnitureManager manager;

    public PaperFurnitureEventListener(final BukkitFurnitureManager manager) {
        this.manager = manager;
    }

    // 主要是为了辅助监听 WorldEdit 添加的实体
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityLoad(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ItemDisplay itemDisplay) {
            this.manager.handleMetaEntityAfterChunkLoad(itemDisplay);
        } else if (BukkitFurnitureManager.COLLISION_ENTITY_CLASS.isInstance(entity)) {
            this.manager.handleCollisionEntityAfterChunkLoad(entity);
        }
    }

    // 主要是为了辅助监听 WorldEdit 移除的实体或被kill的实体
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityUnload(EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ItemDisplay itemDisplay) {
            this.manager.handleMetaEntityUnload(itemDisplay, false);
        } else if (BukkitFurnitureManager.COLLISION_ENTITY_CLASS.isInstance(entity)) {
            this.manager.handleCollisionEntityUnload(entity);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTrackFurniture(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof ItemDisplay furnitureEntity) {
            int entityId = furnitureEntity.getEntityId();
            BukkitFurniture furniture = BukkitFurnitureManager.instance().loadedFurnitureByMetaEntityId(entityId);
            if (furniture == null) return;
            BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(event.getPlayer());
            if (serverPlayer == null) return;
            furniture.controller.onPlayerTrack(serverPlayer);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onUntrackFurniture(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof ItemDisplay furnitureEntity) {
            int entityId = furnitureEntity.getEntityId();
            BukkitFurniture furniture = BukkitFurnitureManager.instance().loadedFurnitureByMetaEntityId(entityId);
            if (furniture == null) return;
            BukkitServerPlayer serverPlayer = BukkitAdaptor.adapt(event.getPlayer());
            if (serverPlayer == null) return;
            furniture.controller.onPlayerUntrack(serverPlayer);
        }
    }
}
