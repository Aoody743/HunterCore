package net.momirealms.craftengine.core.pack.model.definition;

import net.momirealms.craftengine.core.util.Key;

public record ItemModelType<T extends ItemModel>(Key id, ItemModelFactory<T> factory, ItemModelReader<T> reader) {
}
