package net.momirealms.craftengine.core.world;

import net.momirealms.craftengine.core.block.BlockStateWrapper;
import net.momirealms.craftengine.core.block.ImmutableBlockState;

public interface BlockSetter {

    void setBlockState(int x, int y, int z, BlockStateWrapper blockState, int flags);

    default void setBlockState(int x, int y, int z, ImmutableBlockState blockState, int flags) {
        this.setBlockState(x, y, z, blockState.customBlockState(), flags);
    }

    default void setBlockState(BlockPos pos, BlockStateWrapper blockState, int flags) {
        this.setBlockState(pos.x(), pos.y(), pos.z(), blockState, flags);
    }

    default void setBlockState(BlockPos pos, ImmutableBlockState blockState, int flags) {
        this.setBlockState(pos.x(), pos.y(), pos.z(), blockState, flags);
    }
}
