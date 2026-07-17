package net.momirealms.craftengine.core.entity.furniture.element;

import net.momirealms.craftengine.core.entity.furniture.Furniture;
import org.jetbrains.annotations.NotNull;

public interface FurnitureElementConfig<E extends FurnitureElement> {

    E create(@NotNull Furniture furniture);

    default E create(@NotNull Furniture furniture, @NotNull E previous) {
        return null;
    }

    default E createExact(@NotNull Furniture furniture, @NotNull E previous) {
        return null;
    }

    Class<E> elementClass();
}
