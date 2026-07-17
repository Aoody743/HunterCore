package net.momirealms.craftengine.core.block.entity.render.display;

import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.world.BlockPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class DestroyStageDisplayEntity {
    protected final DestroyStageDisplayEntitySetting config;
    protected final int entityId;
    protected final Set<UUID> spawnedViewers = new HashSet<>();

    protected DestroyStageDisplayEntity(DestroyStageDisplayEntitySetting config, int entityId, BlockPos pos) {
        this.config = config;
        this.entityId = entityId;
    }

    public abstract Object spawnPacket();

    public abstract Object removePacket();

    public abstract Object metadataPacket(Player player, int index);

    public boolean ensureSpawned(Player viewer, int index, Consumer<Object> packetSender) {
        boolean firstSpawn = this.spawnedViewers.add(viewer.uuid());
        if (firstSpawn) {
            packetSender.accept(this.spawnPacket());
        }
        packetSender.accept(metadataPacket(viewer, index));
        return firstSpawn;
    }

    public void removeLeftViewers(Collection<UUID> currentViewers, Consumer<UUID> onRemove) {
        this.spawnedViewers.removeIf(uuid -> {
            if (!currentViewers.contains(uuid)) {
                onRemove.accept(uuid);
                return true;
            }
            return false;
        });
    }

    public Set<UUID> spawnedViewers() {
        return this.spawnedViewers;
    }

    public boolean isEmpty() {
        return this.spawnedViewers.isEmpty();
    }
}
