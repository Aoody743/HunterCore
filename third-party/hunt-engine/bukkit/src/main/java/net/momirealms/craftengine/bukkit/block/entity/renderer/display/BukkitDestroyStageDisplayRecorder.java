package net.momirealms.craftengine.bukkit.block.entity.renderer.display;

import net.momirealms.craftengine.core.block.entity.render.display.DestroyStageDisplayEntity;
import net.momirealms.craftengine.core.block.entity.render.display.DestroyStageDisplayEntitySetting;
import net.momirealms.craftengine.core.block.entity.render.display.DestroyStageDisplayRecorder;
import net.momirealms.craftengine.core.world.BlockPos;

public final class BukkitDestroyStageDisplayRecorder extends DestroyStageDisplayRecorder {
    public static final BukkitDestroyStageDisplayRecorder INSTANCE = new BukkitDestroyStageDisplayRecorder();

    private BukkitDestroyStageDisplayRecorder() {
    }

    @Override
    protected DestroyStageDisplayEntity createEntity(DestroyStageDisplayEntitySetting display, int entityId, BlockPos pos) {
        return new BukkitDestroyStageDisplayEntity(display, entityId, pos);
    }
}
