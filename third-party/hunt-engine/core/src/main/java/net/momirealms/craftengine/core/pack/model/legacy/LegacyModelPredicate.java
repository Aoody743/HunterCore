package net.momirealms.craftengine.core.pack.model.legacy;

import net.momirealms.craftengine.core.util.Key;

public interface LegacyModelPredicate<T> {

    String legacyPredicateId(Key material);

    Number toLegacyValue(T value);
}
