package net.momirealms.craftengine.core.pack.model.definition.rangedisptach;

import net.momirealms.craftengine.core.util.Key;

public record RangeDispatchPropertyType<T extends RangeDispatchProperty>(Key id, RangeDispatchPropertyFactory<T> factory, RangeDispatchPropertyReader<T> reader) {
}
