package net.momirealms.craftengine.bukkit.util;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import net.momirealms.craftengine.core.util.VersionHelper;
import net.momirealms.craftengine.proxy.minecraft.nbt.NbtOpsProxy;
import net.momirealms.craftengine.proxy.minecraft.resources.RegistryOpsProxy;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.nbt.codec.*;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;

public final class RegistryOps {
    private RegistryOps() {}

    public static final DynamicOps<Object> NBT = RegistryOpsProxy.INSTANCE.create(NbtOpsProxy.NBT_OPS_INSTANCE, RegistryUtils.getRegistryAccess());
    public static final DynamicOps<Tag> SPARROW_NBT = RegistryOpsProxy.INSTANCE.create(VersionHelper.isOrAbove1_20_5 ? NBTOps.INSTANCE : LegacyNBTOps.INSTANCE, RegistryUtils.getRegistryAccess());
    public static final DynamicOps<Object> JAVA = RegistryOpsProxy.INSTANCE.create(SparrowClass.existsNoRemap("com.mojang.serialization.JavaOps") ? JavaOps.INSTANCE : LegacyJavaOps.INSTANCE, RegistryUtils.getRegistryAccess());
    public static final DynamicOps<JsonElement> JSON = RegistryOpsProxy.INSTANCE.create(VersionHelper.isOrAbove1_20_5 ? JsonOps.INSTANCE : LegacyJsonOps.INSTANCE, RegistryUtils.getRegistryAccess());
}
