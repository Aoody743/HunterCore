package net.momirealms.craftengine.proxy.minecraft.world.item.component;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.FieldGetter;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.world.item.component.Tool", activeIf = "min_version=1.20.5")
public interface ToolProxy {
    ToolProxy INSTANCE = ASMProxyFactory.create(ToolProxy.class);

    @FieldGetter(name = "canDestroyBlocksInCreative", activeIf = "min_version=1.21.5")
    boolean canDestroyBlocksInCreative(Object target);
}
