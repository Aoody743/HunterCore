package net.momirealms.craftengine.core.world.chunk.storage;

import net.momirealms.craftengine.core.world.World;
import org.jetbrains.annotations.NotNull;

public interface StorageAdaptor {

    @NotNull
    WorldDataStorage adapt(@NotNull World world);
}
