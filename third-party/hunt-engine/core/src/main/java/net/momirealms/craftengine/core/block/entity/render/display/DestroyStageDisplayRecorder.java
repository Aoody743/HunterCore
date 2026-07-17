package net.momirealms.craftengine.core.block.entity.render.display;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.world.BlockPos;

import java.util.UUID;

public abstract class DestroyStageDisplayRecorder {
    private final ConcurrentChainedObject2ObjectHashTable<PosKey, Entry> active = new ConcurrentChainedObject2ObjectHashTable<>();

    protected DestroyStageDisplayRecorder() {
    }

    public record PosKey(UUID world, long pos) {
    }

    public static final class Entry {
        public final PosKey key;
        public final DestroyStageDisplayEntitySetting display;
        public final DestroyStageDisplayEntity entity;
        public final ConcurrentChainedObject2ObjectHashTable<UUID, Float> minerProgress = new ConcurrentChainedObject2ObjectHashTable<>();
        private int displayedIndex = -1;

        private Entry(PosKey key, DestroyStageDisplayEntitySetting display, DestroyStageDisplayEntity entity) {
            this.key = key;
            this.display = display;
            this.entity = entity;
        }

        public int displayedIndex() {
            return this.displayedIndex;
        }

        public void displayedIndex(int index) {
            this.displayedIndex = index;
        }
    }

    protected abstract DestroyStageDisplayEntity createEntity(DestroyStageDisplayEntitySetting display, int entityId, BlockPos pos);

    public Entry getOrCreate(PosKey key, DestroyStageDisplayEntitySetting display, BlockPos pos) {
        Entry entry = this.active.get(key);
        if (entry == null) {
            DestroyStageDisplayEntity entity = this.createEntity(display, CraftEngine.instance().platform().getEntityCounter().incrementAndGet(), pos);
            entry = new Entry(key, display, entity);
            this.active.put(key, entry);
        }
        return entry;
    }

    public Entry get(PosKey key) {
        return this.active.get(key);
    }

    public void remove(PosKey key) {
        Entry entry = this.active.remove(key);
        if (entry == null) return;
        Object removePacket = entry.entity.removePacket();
        for (UUID viewerId : entry.entity.spawnedViewers()) {
            Player player = CraftEngine.instance().platform().getPlayer(viewerId);
            if (player == null) continue;
            player.sendPacket(removePacket, false);
        }
    }

    public void swap(PosKey key, DestroyStageDisplayEntitySetting display) {
        Entry entry = this.active.remove(key);
        if (entry == null) return;
        DestroyStageDisplayEntity entity = this.createEntity(display, entry.entity.entityId, BlockPos.of(key.pos));
        entity.spawnedViewers.addAll(entry.entity.spawnedViewers);
        Entry newEntry = new Entry(key, display, entity);
        entry.minerProgress.forEach(e -> newEntry.minerProgress.put(e.getKey(), e.getValue()));
        newEntry.displayedIndex = entry.displayedIndex;
        this.active.put(key, newEntry);
    }
}
