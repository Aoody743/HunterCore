package net.momirealms.craftengine.core.pack.conflict.resolution;

import net.momirealms.craftengine.core.util.Key;

public record ResolutionType<T extends Resolution>(Key id, ResolutionFactory<T> factory) {
}
