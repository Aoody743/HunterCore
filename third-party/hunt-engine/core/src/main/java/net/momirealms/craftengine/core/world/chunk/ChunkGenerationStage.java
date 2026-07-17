package net.momirealms.craftengine.core.world.chunk;

import net.momirealms.craftengine.core.plugin.config.Config;

import java.util.function.Supplier;

public enum ChunkGenerationStage {
    NOISE(Config::generationNoise),
    STRUCTURE(Config::generationStructure),
    CARVER(Config::generationCarver),
    FEATURE(Config::generationFeature),
    SURFACE(Config::generationSurface);

    private final Supplier<Boolean> enabled;

    ChunkGenerationStage(Supplier<Boolean> enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return this.enabled.get();
    }
}
