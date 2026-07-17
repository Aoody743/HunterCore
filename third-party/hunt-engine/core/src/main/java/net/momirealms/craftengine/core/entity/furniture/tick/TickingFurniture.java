package net.momirealms.craftengine.core.entity.furniture.tick;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface TickingFurniture {

    void tick();

    boolean isValid();

    int entityId();
}
