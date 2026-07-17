package net.momirealms.craftengine.proxy.minecraft.world.level.block;

import net.momirealms.craftengine.proxy.minecraft.core.dispenser.DispenseItemBehaviorProxy;
import net.momirealms.craftengine.proxy.minecraft.world.level.ItemLikeProxy;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;
import net.momirealms.sparrow.reflection.proxy.annotation.Type;

@ReflectionProxy(name = "net.minecraft.world.level.block.DispenserBlock")
public interface DispenserBlockProxy {
    DispenserBlockProxy INSTANCE = ASMProxyFactory.create(DispenserBlockProxy.class);

    @MethodInvoker(name = "registerBehavior", isStatic = true)
    void registerBehavior(@Type(clazz = ItemLikeProxy.class) Object item, @Type(clazz = DispenseItemBehaviorProxy.class) Object behavior);
}
