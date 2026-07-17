package net.momirealms.craftengine.core.pack.model.definition.condition;

import net.momirealms.craftengine.core.util.Key;

public record ConditionPropertyType<T extends ConditionProperty>(Key id, ConditionPropertyFactory<T> factory, ConditionPropertyReader<T> reader) {
}
