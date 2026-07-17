package net.momirealms.craftengine.core.entity.furniture.element;

import net.momirealms.craftengine.core.util.Key;

public record FurnitureElementConfigType<E extends FurnitureElement>(Key id, FurnitureElementConfigFactory<E> factory) {
}
