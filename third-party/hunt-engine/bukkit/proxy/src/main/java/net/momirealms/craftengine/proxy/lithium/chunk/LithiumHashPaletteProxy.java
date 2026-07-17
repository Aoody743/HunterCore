package net.momirealms.craftengine.proxy.lithium.chunk;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.momirealms.craftengine.proxy.minecraft.world.level.chunk.HashMapPaletteProxy;
import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette", optional = true)
public interface LithiumHashPaletteProxy extends HashMapPaletteProxy {
    LithiumHashPaletteProxy INSTANCE = ASMProxyFactory.create(LithiumHashPaletteProxy.class);
    Class<?> CLASS = SparrowClass.find("net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette");

    @FieldGetter(name = "table")
    Reference2IntOpenHashMap<Object> getTable(Object target);
}
