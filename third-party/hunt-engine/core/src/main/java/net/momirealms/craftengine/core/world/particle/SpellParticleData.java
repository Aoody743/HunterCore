package net.momirealms.craftengine.core.world.particle;

import net.momirealms.craftengine.core.util.Color;

public final class SpellParticleData implements ParticleData {
    private final Color color;
    private final float power;

    public SpellParticleData(Color color, float power) {
        this.color = color;
        this.power = power;
    }

    public Color color() {
        return this.color;
    }

    public float power() {
        return this.power;
    }
}
