package net.momirealms.craftengine.core.item.updater;

import net.momirealms.craftengine.core.util.Key;

public record ItemUpdaterType<T extends ItemUpdater>(Key id, ItemUpdaterFactory<T> factory) {
}
