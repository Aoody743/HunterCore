package net.momirealms.craftengine.core.block.behavior;

import net.momirealms.craftengine.core.util.Key;

public record BlockBehaviorType<T extends BlockBehavior>(Key id, BlockBehaviorFactory<T> factory) {
}
