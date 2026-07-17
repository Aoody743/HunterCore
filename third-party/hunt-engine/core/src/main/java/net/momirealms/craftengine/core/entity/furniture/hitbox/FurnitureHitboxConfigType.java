package net.momirealms.craftengine.core.entity.furniture.hitbox;

import net.momirealms.craftengine.core.util.Key;

public record FurnitureHitboxConfigType<H extends FurnitureHitBox>(Key id, FurnitureHitBoxConfigFactory<H> factory) {
}
