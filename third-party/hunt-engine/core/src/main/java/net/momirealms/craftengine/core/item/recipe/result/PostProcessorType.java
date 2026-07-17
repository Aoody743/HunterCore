package net.momirealms.craftengine.core.item.recipe.result;

import net.momirealms.craftengine.core.util.Key;

public record PostProcessorType<T extends PostProcessor>(Key id, PostProcessorFactory<T> factory) {
}
