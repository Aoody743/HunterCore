package net.momirealms.craftengine.bukkit.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Projectile;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class BlockDispenseProjectileEvent extends BlockEvent {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final ItemStack item;
    private final Projectile projectile;

    public BlockDispenseProjectileEvent(@NotNull Block block, @NotNull ItemStack item, @NotNull Projectile projectile) {
        super(block);
        this.item = item;
        this.projectile = projectile;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public Projectile getProjectile() {
        return this.projectile;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
