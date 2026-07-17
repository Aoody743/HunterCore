package net.momirealms.craftengine.core.pack.model.definition.special;

import net.momirealms.craftengine.core.util.Key;

public record SpecialModelType<T extends SpecialModel>(Key id, SpecialModelFactory<T> factory, SpecialModelReader<T> reader) {
}
