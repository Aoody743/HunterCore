package net.momirealms.craftengine.core.item.equipment;

import net.momirealms.craftengine.core.util.Key;

import java.util.Objects;

public abstract class AbstractEquipment implements Equipment {
    protected final Key assetId;

    protected AbstractEquipment(Key assetId) {
        this.assetId = Objects.requireNonNull(assetId, "asset-id cannot be null");
    }

    @Override
    public Key assetId() {
        return this.assetId;
    }
}
