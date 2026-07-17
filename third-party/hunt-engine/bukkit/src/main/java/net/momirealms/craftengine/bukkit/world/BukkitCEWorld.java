package net.momirealms.craftengine.bukkit.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.momirealms.craftengine.bukkit.util.LightUtils;
import net.momirealms.craftengine.core.plugin.config.Config;
import net.momirealms.craftengine.core.util.SectionPosUtils;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.chunk.storage.StorageAdaptor;
import net.momirealms.craftengine.core.world.chunk.storage.WorldDataStorage;

public class BukkitCEWorld extends CEWorld {

    public BukkitCEWorld(World world, StorageAdaptor adaptor) {
        super(world, adaptor);
    }

    public BukkitCEWorld(World world, WorldDataStorage dataStorage) {
        super(world, dataStorage);
    }

    @Override
    public void updateLight() {
        if (!Config.enableBlockLightSystem() || !super.lightUpdateRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            LongOpenHashSet sections = super.drainPendingLightSections();
            if (sections == null || sections.isEmpty()) {
                return;
            }
            LightUtils.updateChunkLight(
                    (org.bukkit.World) this.world.platformWorld(),
                    SectionPosUtils.toMap(sections,
                            this.world.worldHeight().getMinSection() - 1,
                            this.world.worldHeight().getMaxSection() + 1
                    )
            );
        } finally {
            super.lightUpdateRunning.set(false);
        }
    }
}
