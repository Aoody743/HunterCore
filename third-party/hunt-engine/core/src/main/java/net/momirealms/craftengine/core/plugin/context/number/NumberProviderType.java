package net.momirealms.craftengine.core.plugin.context.number;

import net.momirealms.craftengine.core.util.Key;

public record NumberProviderType<T extends NumberProvider>(Key id, NumberProviderFactory<T> factory) {
}
