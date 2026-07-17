package net.momirealms.craftengine.core.entity.projectile;

import net.momirealms.craftengine.core.plugin.config.ConfigSection;
import net.momirealms.craftengine.core.plugin.config.ConfigValue;
import net.momirealms.craftengine.core.sound.SoundData;
import net.momirealms.craftengine.core.util.Key;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ProjectileSounds {
    private static final String[] HIT_ENTITY = new String[] {"hit_entity", "hit-entity"};
    private static final String[] HIT_BLOCK = new String[] {"hit_block", "hit-block"};
    private final SoundData throwSound;
    private final TargetBasedSound hitEntitySound;
    private final TargetBasedSound hitBlockSound;

    public ProjectileSounds(SoundData throwSound, TargetBasedSound hitEntitySound, TargetBasedSound hitBlockSound) {
        this.throwSound = throwSound;
        this.hitEntitySound = hitEntitySound;
        this.hitBlockSound = hitBlockSound;
    }

    public static ProjectileSounds fromConfig(ConfigSection section) {
        SoundData throwSound = section.getValue("throw", v -> SoundData.fromConfig(v, SoundData.SoundValue.FIXED_1, SoundData.SoundValue.FIXED_1));
        TargetBasedSound hitEntitySound = section.getValue(HIT_ENTITY, TargetBasedSound::fromConfig);
        TargetBasedSound hitBlockSound = section.getValue(HIT_BLOCK, TargetBasedSound::fromConfig);
        return new ProjectileSounds(throwSound, hitEntitySound, hitBlockSound);
    }

    @Nullable
    public SoundData throwSound() {
        return this.throwSound;
    }

    @Nullable
    public TargetBasedSound hitEntitySound() {
        return this.hitEntitySound;
    }

    @Nullable
    public TargetBasedSound hitBlockSound() {
        return this.hitBlockSound;
    }

    public static class TargetBasedSound {
        private final SoundData defaultSound;
        private final Map<Key, SoundData> contexts;

        public TargetBasedSound(SoundData defaultSound, Map<Key, SoundData> contexts) {
            this.defaultSound = defaultSound;
            this.contexts = contexts;
        }

        public SoundData get(Key target) {
            return this.contexts.getOrDefault(target, this.defaultSound);
        }

        public static TargetBasedSound fromConfig(ConfigValue config) {
            if (config.is(Map.class)) {
                ConfigSection configSection = config.getAsSection();
                if (configSection.containsKey("default")) {
                    SoundData defaultSound = configSection.getValue("default", v -> SoundData.fromConfig(v, SoundData.SoundValue.FIXED_1, SoundData.SoundValue.FIXED_1));
                    ConfigSection overridesSection = configSection.getSection("overrides");
                    Map<Key, SoundData> overrides = new HashMap<>();
                    if (overridesSection != null) {
                        for (String target : overridesSection.keySet()) {
                            overrides.put(
                                    Key.of(target),
                                    overridesSection.getValue(target, v -> SoundData.fromConfig(v, SoundData.SoundValue.FIXED_1, SoundData.SoundValue.FIXED_1))
                            );
                        }
                    }
                    return new TargetBasedSound(defaultSound, overrides);
                } else {
                    SoundData defaultSound = SoundData.fromConfig(config, SoundData.SoundValue.FIXED_1, SoundData.SoundValue.FIXED_1);
                    return new TargetBasedSound(defaultSound, Map.of());
                }
            } else {
                return new TargetBasedSound(SoundData.of(config.getAsAssetPath()), Map.of());
            }
        }
    }
}
