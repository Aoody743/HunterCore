package net.momirealms.craftengine.core.world.particle;

import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.plugin.CraftEngine;
import net.momirealms.craftengine.core.plugin.config.ConfigConstants;
import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.LazyReference;

import java.util.HashMap;
import java.util.Map;

public final class ParticleDataTypes {
    public static final Map<Key, java.util.function.Function<ConfigSection, ParticleData>> TYPES = new HashMap<>();
    private static final String[] BLOCK_STATE = new String[]{"blockstate", "block_state", "block-state"};
    private static final String[] TARGET_X = new String[]{"target_x", "target-x"};
    private static final String[] TARGET_Y = new String[]{"target_y", "target-y"};
    private static final String[] TARGET_Z = new String[]{"target_z", "target-z"};
    private static final String[] ARRIVAL_TIME = new String[]{"arrival_time", "arrival-time"};
    private static final String[] WATER_BLOCKS = new String[]{"blocks", "water_blocks", "water-blocks"};
    private static final String[] BURST_IMPULSE_BASE = new String[]{"base", "burst_impulse_base", "burst-impulse-base"};
    private static final String[] ROLL = new String[]{"roll", "charge"};

    static {
        registerParticleData(section -> {
                    final String blockState = section.getNonNullString(BLOCK_STATE);
                    return new BlockStateData(LazyReference.lazyReference(() -> CraftEngine.instance().blockManager().createBlockState(blockState)));
                },
                ParticleTypes.BLOCK, ParticleTypes.FALLING_DUST, ParticleTypes.DUST_PILLAR, ParticleTypes.BLOCK_CRUMBLE, ParticleTypes.BLOCK_MARKER);
        registerParticleData(section -> new ColorData(
                        section.getNonNullValue("color", ConfigConstants.ARGUMENT_COLOR).getAsColor()
                ),
                ParticleTypes.ENTITY_EFFECT, ParticleTypes.TINTED_LEAVES);
        registerParticleData(section -> new SculkChargeData(
                        section.getFloat(ROLL)
                ),
                ParticleTypes.SCULK_CHARGE);
        registerParticleData(section -> new GeyserData(
                        section.getInt(WATER_BLOCKS)
                ),
                ParticleTypes.GEYSER, ParticleTypes.GEYSER_PLUME);
        registerParticleData(section -> new GeyserBaseData(
                        section.getInt(WATER_BLOCKS),
                        section.getFloat(BURST_IMPULSE_BASE)
                ),
                ParticleTypes.GEYSER_BASE, ParticleTypes.GEYSER_POOF);
        registerParticleData(section -> new ShriekData(
                        section.getInt("shriek")
                ),
                ParticleTypes.SHRIEK);
        registerParticleData(section -> new DustData(
                        section.getNonNullValue("color", ConfigConstants.ARGUMENT_COLOR).getAsColor(),
                        section.getFloat("scale", 1f)
                ),
                ParticleTypes.DUST);
        registerParticleData(section -> new DustTransitionData(
                        section.getNonNullValue("from", ConfigConstants.ARGUMENT_COLOR).getAsColor(),
                        section.getNonNullValue("to", ConfigConstants.ARGUMENT_COLOR).getAsColor(),
                        section.getFloat("scale", 1f)
                ),
                ParticleTypes.DUST_COLOR_TRANSITION);
        registerParticleData(section -> {
                    final Key itemId = section.getNonNullIdentifier("item");
                    return new ItemStackData(LazyReference.lazyReference(() -> Item.byId(itemId)));
                },
                ParticleTypes.ITEM);
        registerParticleData(section -> new VibrationData(
                        section.getNumber(TARGET_X, ConfigConstants.CONSTANT_ZERO),
                        section.getNumber(TARGET_Y, ConfigConstants.CONSTANT_ZERO),
                        section.getNumber(TARGET_Z, ConfigConstants.CONSTANT_ZERO),
                        section.getNumber(ARRIVAL_TIME, ConfigConstants.CONSTANT_TEN)
                ),
                ParticleTypes.VIBRATION);
        registerParticleData(section -> new TrailData(
                        section.getNumber(TARGET_X, ConfigConstants.CONSTANT_ZERO),
                        section.getNumber(TARGET_Y, ConfigConstants.CONSTANT_ZERO),
                        section.getNumber(TARGET_Z, ConfigConstants.CONSTANT_ZERO),
                        section.getNonNullValue("color", ConfigConstants.ARGUMENT_COLOR).getAsColor(),
                        section.getNumber("duration", ConfigConstants.CONSTANT_TEN)
                ),
                ParticleTypes.TRAIL);
        registerParticleData(section -> new SpellParticleData(
                        section.getNonNullValue("color", ConfigConstants.ARGUMENT_COLOR).getAsColor(),
                        section.getFloat("power", 1f)
                ),
                ParticleTypes.SPELL);
    }

    public static void registerParticleData(java.util.function.Function<ConfigSection, ParticleData> function, Key... types) {
        for (Key type : types) {
            TYPES.put(type, function);
        }
    }
}
