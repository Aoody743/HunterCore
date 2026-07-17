package net.momirealms.craftengine.proxy.minecraft.world.entity.projectile;

import net.momirealms.sparrow.reflection.proxy.ASMProxyFactory;
import net.momirealms.sparrow.reflection.proxy.annotation.MethodInvoker;
import net.momirealms.sparrow.reflection.proxy.annotation.ReflectionProxy;

@ReflectionProxy(name = "net.minecraft.world.entity.projectile.FireworkRocketEntity")
public interface FireworkRocketEntityProxy {
    FireworkRocketEntityProxy INSTANCE = ASMProxyFactory.create(FireworkRocketEntityProxy.class);

    @MethodInvoker(name = "getItem")
    Object getItem(Object target);
}
