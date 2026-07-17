package net.momirealms.craftengine.core.block;

public final class BlockRegistryMirror {
    private static BlockStateWrapper[] blockStates;
    private static BlockStateWrapper stoneState;

    public static void init(BlockStateWrapper[] states, BlockStateWrapper state) {
        if (blockStates != null) throw new IllegalStateException("block states have already been set");
        blockStates = states;
        stoneState = state;
    }

    public static BlockStateWrapper byId(int stateId) {
        if (stateId < 0) return stoneState;
        return blockStates[stateId];
    }

    public static int size() {
        return blockStates.length;
    }

    public static BlockStateWrapper stoneState() {
        return stoneState;
    }

    public static BlockStateWrapper[] blockStates() {
        return blockStates;
    }
}
