package net.momirealms.craftengine.core.item.behavior;

import net.momirealms.craftengine.core.util.Key;

public record ItemBehaviorType<T extends ItemBehavior>(Key id, ItemBehaviorFactory<T> factory) {
}
