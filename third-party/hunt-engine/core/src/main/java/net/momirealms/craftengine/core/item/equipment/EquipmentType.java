package net.momirealms.craftengine.core.item.equipment;

import net.momirealms.craftengine.core.util.Key;

public record EquipmentType<E extends Equipment>(Key id, EquipmentFactory<E> factory) {
}
