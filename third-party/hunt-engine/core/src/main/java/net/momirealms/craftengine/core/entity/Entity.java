package net.momirealms.craftengine.core.entity;

import net.momirealms.craftengine.core.entity.data.EntityData;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.world.WorldPosition;

import java.util.UUID;

public interface Entity {
    Key type();

    boolean isValid();

    double x();

    double y();

    double z();

    WorldPosition position();

    void tick();

    float xRot();

    float yRot();

    int entityId();

    World world();

    Direction getDirection();

    Object platformEntity();

    Object serverEntity();

    String name();

    UUID uuid();

    Object entityData();

    <T> T getEntityData(EntityData<T> entityData);

    default <T> void setEntityData(EntityData<T> data, T value) {
        setEntityData(data, value, false);
    }

    <T> void setEntityData(EntityData<T> data, T value, boolean force);

    void remove();
}
