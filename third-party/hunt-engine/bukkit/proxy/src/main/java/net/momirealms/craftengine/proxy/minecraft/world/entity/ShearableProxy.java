package net.momirealms.craftengine.proxy.minecraft.world.entity;

import net.momirealms.sparrow.reflection.clazz.SparrowClass;
import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.world.entity.Shearable")
public interface ShearableProxy {
    ShearableProxy INSTANCE = ASMProxyFactory.create(ShearableProxy.class);
    Class<?> CLASS = SparrowClass.find("net.minecraft.world.entity.Shearable");

    @MethodInvoker(name = "readyForShearing")
    boolean readyForShearing(Object target);
}
