package net.momirealms.craftengine.bukkit.util;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;

public final class PaperEventUtils {
    private PaperEventUtils() {}

    @SuppressWarnings("UnstableApiUsage")
    public static Event entityInside(Entity entity, Block block) {
        return new EntityInsideBlockEvent(entity, block);
    }
}
