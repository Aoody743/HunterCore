package net.momirealms.craftengine.core.entity.furniture.hitbox;

import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.collision.AABB;

public record FurnitureHitboxPart(int entityId, AABB aabb, Vec3d pos, boolean interactive) {
}
