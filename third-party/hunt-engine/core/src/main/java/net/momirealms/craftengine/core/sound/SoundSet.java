package net.momirealms.craftengine.core.sound;

import net.momirealms.craftengine.core.block.BlockKeys;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SoundSet(List<Key> blocks, Key openSound, Key closeSound) {
    // Trapdoor sound sets
    public static final SoundSet WOODEN_TRAPDOOR = new SoundSet(BlockKeys.WOODEN_TRAPDOORS, Sounds.WOODEN_TRAPDOOR_OPEN, Sounds.WOODEN_TRAPDOOR_CLOSE);
    public static final SoundSet NETHER_WOOD_TRAPDOOR = new SoundSet(BlockKeys.NETHER_TRAPDOORS, Sounds.NETHER_WOOD_TRAPDOOR_OPEN, Sounds.NETHER_WOOD_TRAPDOOR_CLOSE);
    public static final SoundSet BAMBOO_WOOD_TRAPDOOR = new SoundSet(BlockKeys.BAMBOO_TRAPDOORS, Sounds.BAMBOO_WOOD_TRAPDOOR_OPEN, Sounds.BAMBOO_WOOD_TRAPDOOR_CLOSE);
    public static final SoundSet CHERRY_WOOD_TRAPDOOR = new SoundSet(BlockKeys.CHERRY_TRAPDOORS, Sounds.CHERRY_WOOD_TRAPDOOR_OPEN, Sounds.CHERRY_WOOD_TRAPDOOR_CLOSE);
    public static final SoundSet COPPER_TRAPDOOR = new SoundSet(BlockKeys.COPPER_TRAPDOORS, Sounds.COPPER_TRAPDOOR_OPEN, Sounds.COPPER_TRAPDOOR_CLOSE);

    // Door sound sets
    public static final SoundSet WOODEN_DOOR = new SoundSet(BlockKeys.WOODEN_DOORS, Sounds.WOODEN_DOOR_OPEN, Sounds.WOODEN_DOOR_CLOSE);
    public static final SoundSet NETHER_WOOD_DOOR = new SoundSet(BlockKeys.NETHER_DOORS, Sounds.NETHER_WOOD_DOOR_OPEN, Sounds.NETHER_WOOD_DOOR_CLOSE);
    public static final SoundSet BAMBOO_WOOD_DOOR = new SoundSet(BlockKeys.BAMBOO_DOORS, Sounds.BAMBOO_WOOD_DOOR_OPEN, Sounds.BAMBOO_WOOD_DOOR_CLOSE);
    public static final SoundSet CHERRY_WOOD_DOOR = new SoundSet(BlockKeys.CHERRY_DOORS, Sounds.CHERRY_WOOD_DOOR_OPEN, Sounds.CHERRY_WOOD_DOOR_CLOSE);
    public static final SoundSet COPPER_DOOR = new SoundSet(BlockKeys.COPPER_DOORS, Sounds.COPPER_DOOR_OPEN, Sounds.COPPER_DOOR_CLOSE);

    // Button sound sets
    public static final SoundSet WOODEN_BUTTON = new SoundSet(BlockKeys.WOODEN_BUTTONS, Sounds.WOODEN_BUTTON_CLICK_ON, Sounds.WOODEN_BUTTON_CLICK_OFF);
    public static final SoundSet NETHER_WOOD_BUTTON = new SoundSet(BlockKeys.NETHER_BUTTONS, Sounds.NETHER_WOOD_BUTTON_CLICK_ON, Sounds.NETHER_WOOD_BUTTON_CLICK_OFF);
    public static final SoundSet BAMBOO_WOOD_BUTTON = new SoundSet(BlockKeys.BAMBOO_BUTTONS, Sounds.BAMBOO_WOOD_BUTTON_CLICK_ON, Sounds.BAMBOO_WOOD_BUTTON_CLICK_OFF);
    public static final SoundSet CHERRY_WOOD_BUTTON = new SoundSet(BlockKeys.CHERRY_BUTTONS, Sounds.CHERRY_WOOD_BUTTON_CLICK_ON, Sounds.CHERRY_WOOD_BUTTON_CLICK_OFF);
    public static final SoundSet STONE_BUTTON = new SoundSet(BlockKeys.STONE_BUTTONS, Sounds.STONE_BUTTON_CLICK_ON, Sounds.STONE_BUTTON_CLICK_OFF);

    // Fence gate sound sets
    public static final SoundSet WOODEN_FENCE_GATE = new SoundSet(BlockKeys.WOODEN_FENCE_GATES, Sounds.WOODEN_FENCE_GATE_OPEN, Sounds.WOODEN_FENCE_GATE_CLOSE);
    public static final SoundSet NETHER_WOOD_FENCE_GATE = new SoundSet(BlockKeys.NETHER_FENCE_GATES, Sounds.NETHER_WOOD_FENCE_GATE_OPEN, Sounds.NETHER_WOOD_FENCE_GATE_CLOSE);
    public static final SoundSet BAMBOO_WOOD_FENCE_GATE = new SoundSet(BlockKeys.BAMBOO_FENCE_GATES, Sounds.BAMBOO_WOOD_FENCE_GATE_OPEN, Sounds.BAMBOO_WOOD_FENCE_GATE_CLOSE);
    public static final SoundSet CHERRY_WOOD_FENCE_GATE = new SoundSet(BlockKeys.CHERRY_FENCE_GATES, Sounds.CHERRY_WOOD_FENCE_GATE_OPEN, Sounds.CHERRY_WOOD_FENCE_GATE_CLOSE);

    // 获取所有声音集合的便捷方法
    public static List<SoundSet> getAllSoundSets() {
        return List.of(
                // Trapdoors
                WOODEN_TRAPDOOR, NETHER_WOOD_TRAPDOOR, BAMBOO_WOOD_TRAPDOOR,
                CHERRY_WOOD_TRAPDOOR, COPPER_TRAPDOOR,
                // Doors
                WOODEN_DOOR, NETHER_WOOD_DOOR, BAMBOO_WOOD_DOOR,
                CHERRY_WOOD_DOOR, COPPER_DOOR,
                // Fence gates
                WOODEN_FENCE_GATE, NETHER_WOOD_FENCE_GATE,
                BAMBOO_WOOD_FENCE_GATE, CHERRY_WOOD_FENCE_GATE,
                // Buttons
                WOODEN_BUTTON, NETHER_WOOD_BUTTON, BAMBOO_WOOD_BUTTON,
                CHERRY_WOOD_BUTTON, STONE_BUTTON
        );
    }

    // 按类型获取声音集合的方法
    public static List<SoundSet> getTrapdoorSoundSets() {
        return List.of(WOODEN_TRAPDOOR, NETHER_WOOD_TRAPDOOR, BAMBOO_WOOD_TRAPDOOR,
                CHERRY_WOOD_TRAPDOOR, COPPER_TRAPDOOR);
    }

    public static List<SoundSet> getDoorSoundSets() {
        return List.of(WOODEN_DOOR, NETHER_WOOD_DOOR, BAMBOO_WOOD_DOOR,
                CHERRY_WOOD_DOOR, COPPER_DOOR);
    }

    public static List<SoundSet> getFenceGateSoundSets() {
        return List.of(WOODEN_FENCE_GATE, NETHER_WOOD_FENCE_GATE,
                BAMBOO_WOOD_FENCE_GATE, CHERRY_WOOD_FENCE_GATE);
    }

    public static List<SoundSet> getButtonSoundSets() {
        return List.of(WOODEN_BUTTON, NETHER_WOOD_BUTTON, BAMBOO_WOOD_BUTTON,
                CHERRY_WOOD_BUTTON, STONE_BUTTON);
    }

    private static final Map<Key, SoundSet> SOUND_SETS = new HashMap<>();

    static {
        for (SoundSet set : getAllSoundSets()) {
            set.blocks.forEach(block -> {
                SOUND_SETS.put(block, set);
            });
        }
    }

    @Nullable
    public static SoundSet getByBlock(Key blockType) {
        return SOUND_SETS.get(blockType);
    }
}