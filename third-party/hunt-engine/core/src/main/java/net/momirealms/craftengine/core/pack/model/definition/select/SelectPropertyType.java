package net.momirealms.craftengine.core.pack.model.definition.select;

import net.momirealms.craftengine.core.util.Key;

public record SelectPropertyType<T extends SelectProperty>(Key id, SelectPropertyFactory<T> factory, SelectPropertyReader<T> reader) {
}
