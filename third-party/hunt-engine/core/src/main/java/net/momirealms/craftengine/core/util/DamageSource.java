package net.momirealms.craftengine.core.util;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum DamageSource {
    BLOCK_EXPLOSION,
    CAMPFIRE,
    CONTACT,
    CRAMMING,
    CUSTOM,
    DROWNING,
    DRYOUT,
    ENTITY_ATTACK,
    ENTITY_EXPLOSION,
    ENTITY_SWEEP_ATTACK,
    FALL,
    FALLING_BLOCK,
    FIRE,
    FIRE_TICK,
    FLY_INTO_WALL,
    FREEZE,
    HOT_FLOOR,
    KILL,
    LAVA,
    LIGHTNING,
    MAGIC,
    MELTING,
    POISON,
    PROJECTILE,
    SONIC_BOOM,
    STARVATION,
    SUFFOCATION,
    SUICIDE,
    THORNS,
    VOID,
    WITHER,
    WORLD_BORDER;

    public static final Map<String, DamageSource> BY_NAME = new HashMap<>();

    static {
        for (DamageSource cause : values()) {
            BY_NAME.put(cause.name().toLowerCase(Locale.ENGLISH), cause);
            BY_NAME.put(cause.name(), cause);
        }
    }

    @Nullable
    public static DamageSource byName(String name) {
        return BY_NAME.get(name);
    }
}
