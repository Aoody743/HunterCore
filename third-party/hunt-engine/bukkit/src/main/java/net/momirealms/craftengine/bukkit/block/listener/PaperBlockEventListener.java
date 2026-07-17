package net.momirealms.craftengine.bukkit.block.listener;

import io.papermc.paper.event.block.BlockBreakBlockEvent;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptor;
import net.momirealms.craftengine.bukkit.loot.BlockLootContext;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitExistingBlock;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.loot.Loot;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.plugin.context.parameter.DirectContextParameters;
import net.momirealms.craftengine.core.world.WorldPosition;
import net.momirealms.craftengine.proxy.bukkit.craftbukkit.CraftWorldProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.BlockGetterProxy;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class PaperBlockEventListener implements Listener {

    // BlockBreakBlockEvent = liquid + piston
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreakBlock(BlockBreakBlockEvent event) {
        Block block = event.getBlock();
        Object blockState = BlockGetterProxy.INSTANCE.getBlockState(CraftWorldProxy.INSTANCE.getWorld(block.getWorld()), LocationUtils.toBlockPos(block.getX(), block.getY(), block.getZ()));
        if (BlockStateUtils.isVanillaBlock(blockState)) {
            // override vanilla block loots
            CraftEngine.instance().lootManager().getBlockLoot(BlockStateUtils.blockStateToId(blockState)).ifPresent(it -> {
                if (it.override()) {
                    event.getDrops().clear();
                    event.setExpToDrop(0);
                }
                Location location = block.getLocation();
                net.momirealms.craftengine.core.world.World world = BukkitAdaptor.adapt(location.getWorld());
                WorldPosition position = new WorldPosition(world, location.getBlockX() + 0.5, location.getBlockY() + 0.5, location.getBlockZ() + 0.5);
                ContextHolder contextHolder = ContextHolder.builder()
                        .withParameter(DirectContextParameters.POSITION, position)
                        .withParameter(DirectContextParameters.BLOCK, new BukkitExistingBlock(block))
                        .build();
                BlockLootContext blockLootContext = new BlockLootContext(world, null, 1.0f, contextHolder, BukkitAdaptor.adapt(block), null, null);
                for (Loot loot : it.loots()) {
                    for (Item item : loot.getRandomItems(blockLootContext)) {
                        world.dropItemNaturally(position, item);
                    }
                }
            });
        }
    }
}
