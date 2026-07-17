package net.momirealms.craftengine.core.world.chunk.packet;

import net.momirealms.sparrow.nbt.Tag;

public record BlockEntityData(int packedXZ, short y, int type, Tag tag) {}
