package net.momirealms.craftengine.core.pack.model.definition.tint;

import net.momirealms.craftengine.core.util.Key;

public record TintType<T extends Tint>(Key id, TintFactory<T> factory, TintReader<T> reader) {
}
