package net.momirealms.craftengine.core.item.recipe.remainder;

import net.momirealms.craftengine.core.util.Key;

public record CraftRemainderType<T extends CraftRemainder>(Key id, CraftRemainderFactory<T> factory) {
}
